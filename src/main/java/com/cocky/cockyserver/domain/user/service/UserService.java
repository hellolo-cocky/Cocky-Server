package com.cocky.cockyserver.domain.user.service;

import com.cocky.cockyserver.ai.port.NicknameGenerationFailedException;
import com.cocky.cockyserver.ai.port.NicknameGenerator;
import com.cocky.cockyserver.domain.user.dto.AnonymousToggleResponse;
import com.cocky.cockyserver.domain.user.dto.UserMeResponse;
import com.cocky.cockyserver.domain.user.entity.User;
import com.cocky.cockyserver.domain.user.exception.UserNotFoundException;
import com.cocky.cockyserver.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    /** NicknameGenerator.generate()가 인자 없이 무작위 생성이라 중복 가능 — 총 시도 횟수(재시도 아님). */
    private static final int MAX_NICKNAME_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final NicknameGenerator nicknameGenerator;

    public UserMeResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("존재하지 않는 사용자입니다. userId=" + userId));
        return UserMeResponse.from(user);
    }

    /**
     * 익명 모드 토글. true로 켤 때 anonymous_nickname이 아직 없으면
     * {@link NicknameGenerator#generate}로 채우고, 이미 있으면 재생성하지 않고 재사용한다.
     * false로 끌 때는 is_anonymous만 내리고 닉네임은 보존한다(재도입 시 재사용).
     *
     * <p>회원가입 경로에서는 절대 호출하지 않는다 — AI 의존성이 신규 가입을 막게 되는 것을
     * 피하기 위해 이 토글 API에서만 온디맨드로 호출한다.
     */
    @Transactional
    public AnonymousToggleResponse setAnonymous(Long userId, boolean anonymous) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("존재하지 않는 사용자입니다. userId=" + userId));

        if (anonymous && user.getAnonymousNickname() == null) {
            user.updateAnonymousNickname(generateUniqueNickname());
        }
        user.updateAnonymous(anonymous);

        return AnonymousToggleResponse.from(user);
    }

    /**
     * 생성 → unique 조회 → 중복이면 재생성, 최대 {@link #MAX_NICKNAME_ATTEMPTS}회. AI 호출
     * 실패든 중복 소진이든 {@link NicknameGenerationFailedException} 하나로 통일해서 던진다 —
     * 기본 닉네임 폴백은 만들지 않는다(실패는 실패로 노출).
     */
    private String generateUniqueNickname() {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_NICKNAME_ATTEMPTS; attempt++) {
            try {
                String candidate = nicknameGenerator.generate();
                if (!userRepository.existsByAnonymousNickname(candidate)) {
                    return candidate;
                }
                log.warn("익명 닉네임 중복(시도 {}/{}): {}", attempt, MAX_NICKNAME_ATTEMPTS, candidate);
            } catch (RuntimeException e) {
                last = e;
                log.warn("익명 닉네임 생성 시도 {}/{} 실패: {}", attempt, MAX_NICKNAME_ATTEMPTS, e.getMessage());
            }
        }
        log.error("익명 닉네임 총 {}회 시도 소진", MAX_NICKNAME_ATTEMPTS);
        throw new NicknameGenerationFailedException(
                "익명 닉네임 생성 실패(총 " + MAX_NICKNAME_ATTEMPTS + "회 시도 소진)", last);
    }
}