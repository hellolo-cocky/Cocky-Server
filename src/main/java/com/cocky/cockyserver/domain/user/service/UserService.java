package com.cocky.cockyserver.domain.user.service;

import com.cocky.cockyserver.ai.port.NicknameGenerationFailedException;
import com.cocky.cockyserver.ai.port.NicknameGenerator;
import com.cocky.cockyserver.domain.user.dto.AnonymousToggleResponse;
import com.cocky.cockyserver.domain.user.dto.UserMeResponse;
import com.cocky.cockyserver.domain.user.entity.User;
import com.cocky.cockyserver.domain.user.exception.UserNotFoundException;
import com.cocky.cockyserver.domain.user.repository.UserRepository;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 클래스 단위 {@code @Transactional}을 쓰지 않는다 — {@link #setAnonymous}가 닉네임 생성(외부 AI 호출)을
 * 트랜잭션 밖에서 하고 저장만 {@link TransactionTemplate}으로 짧게 감싸야 하기 때문이다
 * ({@code SubmissionService}와 같은 방식). 읽기 전용 조회는 메서드 단위로 붙인다.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    /** 닉네임 "생성" 총 시도 횟수 — AI 호출이 예외를 던지거나 빈 값을 줄 때 (재시도가 아니라 총 횟수). */
    private static final int MAX_GENERATE_ATTEMPTS = 5;

    /** 닉네임 "저장" 총 시도 횟수 — 1회차는 생성값 그대로, 이후는 "#숫자4자리" 접미사를 붙인다. */
    private static final int MAX_SAVE_ATTEMPTS = 5;

    /** user.anonymous_nickname 컬럼 길이(50) 중 접미사("#" + 숫자 4자리 = 5자)를 뺀 값. */
    private static final int MAX_BASE_LENGTH = 45;

    private final UserRepository userRepository;
    private final NicknameGenerator nicknameGenerator;
    private final TransactionTemplate writeTransaction;

    public UserService(UserRepository userRepository, NicknameGenerator nicknameGenerator,
                       PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.nicknameGenerator = nicknameGenerator;
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public UserMeResponse getMe(Long userId) {
        return UserMeResponse.from(findUser(userId));
    }

    /**
     * 익명 모드 "사용자 기본값" 토글. true로 켤 때 anonymous_nickname이 아직 없으면 생성해서 채우고,
     * 이미 있으면 생성기를 호출하지 않고 재사용한다. false로 끌 때는 is_anonymous_default만 내리고
     * 닉네임은 보존한다(재도입 시 재사용).
     *
     * <p>닉네임 생성(OpenAI 호출, 최대 수십 초)은 트랜잭션 밖에서 하고, DB 쓰기만 짧은 트랜잭션으로
     * 처리한다 — 외부 API 대기 중 DB 커넥션을 물고 있지 않기 위해서다.
     *
     * <p>이 값은 제출별 기본값일 뿐이다 — 제출 요청이 isAnonymous를 명시하면 그 값이 우선한다
     * ({@code SubmissionService} 참고).
     *
     * <p>회원가입 경로에서는 절대 호출하지 않는다 — AI 의존성이 신규 가입을 막게 되는 것을
     * 피하기 위해 이 토글 API에서만 온디맨드로 호출한다.
     */
    public AnonymousToggleResponse setAnonymous(Long userId, boolean anonymousDefault) {
        User current = findUser(userId);

        if (!anonymousDefault || current.getAnonymousNickname() != null) {
            return writeTransaction.execute(status -> applyToggle(userId, anonymousDefault, null));
        }

        String base = generateBaseNickname();
        return saveWithUniqueNickname(userId, base);
    }

    /**
     * 트랜잭션 안에서 호출한다. 후보 닉네임은 아직 닉네임이 없을 때만 쓴다 — 동시에 같은 유저의 다른
     * 요청이 먼저 채웠다면 그 값을 보존한다(닉네임은 최초 생성 후 고정).
     * saveAndFlush로 UNIQUE 위반을 커밋 시점이 아니라 이 호출에서 바로 드러낸다.
     */
    private AnonymousToggleResponse applyToggle(Long userId, boolean anonymousDefault, String candidate) {
        User user = findUser(userId);
        if (candidate != null && user.getAnonymousNickname() == null) {
            user.updateAnonymousNickname(candidate);
        }
        user.updateAnonymousDefault(anonymousDefault);
        userRepository.saveAndFlush(user);
        return AnonymousToggleResponse.from(user);
    }

    /**
     * NicknameGenerator는 인자 없이 무작위로 만들고 폴백 풀도 작아서 같은 값이 나올 수 있다. 호출은
     * 여기서 한 번만 성공시키고, 중복 해소는 {@link #saveWithUniqueNickname}의 접미사로 처리한다.
     * AI 호출 실패든 빈 값이든 총 {@link #MAX_GENERATE_ATTEMPTS}회 소진 시
     * {@link NicknameGenerationFailedException}으로 통일해서 던진다.
     */
    private String generateBaseNickname() {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_GENERATE_ATTEMPTS; attempt++) {
            try {
                String candidate = nicknameGenerator.generate();
                if (candidate != null && !candidate.isBlank()) {
                    String base = candidate.strip();
                    return base.length() > MAX_BASE_LENGTH ? base.substring(0, MAX_BASE_LENGTH) : base;
                }
                log.warn("익명 닉네임 생성 결과가 비어 있음(시도 {}/{})", attempt, MAX_GENERATE_ATTEMPTS);
            } catch (RuntimeException e) {
                last = e;
                log.warn("익명 닉네임 생성 시도 {}/{} 실패: {}", attempt, MAX_GENERATE_ATTEMPTS, e.getMessage());
            }
        }
        log.error("익명 닉네임 생성 총 {}회 시도 소진", MAX_GENERATE_ATTEMPTS);
        throw new NicknameGenerationFailedException(
                "익명 닉네임 생성 실패(총 " + MAX_GENERATE_ATTEMPTS + "회 시도 소진)", last);
    }

    /**
     * 1회차는 생성값 그대로, 중복이면 2회차부터 "#숫자4자리" 접미사(예: 용감한고양이#4821)를 붙여
     * 최대 {@link #MAX_SAVE_ATTEMPTS}회 시도한다. 사전 조회(exists)로 거른 뒤에도 동시 토글이 같은
     * 값을 먼저 커밋하면 저장 시 UNIQUE 위반이 나므로, {@link DataIntegrityViolationException}도
     * 같은 재시도 경로로 처리한다(그대로 두면 500).
     */
    private AnonymousToggleResponse saveWithUniqueNickname(Long userId, String base) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_SAVE_ATTEMPTS; attempt++) {
            String candidate = attempt == 1 ? base : base + "#" + randomSuffix();
            if (userRepository.existsByAnonymousNickname(candidate)) {
                log.warn("익명 닉네임 중복(시도 {}/{}): {}", attempt, MAX_SAVE_ATTEMPTS, candidate);
                continue;
            }
            try {
                return writeTransaction.execute(status -> applyToggle(userId, true, candidate));
            } catch (DataIntegrityViolationException e) {
                last = e;
                log.warn("익명 닉네임 저장 중 UNIQUE 위반(시도 {}/{}): {}", attempt, MAX_SAVE_ATTEMPTS, candidate);
            }
        }
        log.error("익명 닉네임 저장 총 {}회 시도 소진: base={}", MAX_SAVE_ATTEMPTS, base);
        throw new NicknameGenerationFailedException(
                "익명 닉네임 생성 실패(중복으로 총 " + MAX_SAVE_ATTEMPTS + "회 시도 소진)", last);
    }

    private static String randomSuffix() {
        return "%04d".formatted(ThreadLocalRandom.current().nextInt(10_000));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("존재하지 않는 사용자입니다. userId=" + userId));
    }
}
