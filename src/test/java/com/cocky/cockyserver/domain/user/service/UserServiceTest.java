package com.cocky.cockyserver.domain.user.service;

import com.cocky.cockyserver.ai.demo.DemoNicknameGenerator;
import com.cocky.cockyserver.ai.port.NicknameGenerationFailedException;
import com.cocky.cockyserver.ai.port.NicknameGenerator;
import com.cocky.cockyserver.domain.user.dto.AnonymousToggleResponse;
import com.cocky.cockyserver.domain.user.entity.Role;
import com.cocky.cockyserver.domain.user.entity.User;
import com.cocky.cockyserver.domain.user.repository.UserRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 익명 닉네임 토글(UserService.setAnonymous) 검증.
 *
 * <p>"AI 호출 실패" 케이스는 NicknameGenerator를 mock으로 대체해 예외를 강제로 던지는 방식으로만
 * 검증한다 — real 구현체({@code NicknameService})는 OpenAI 호출 실패를 내부에서 흡수해 내장
 * 예시로 폴백하므로(항상 성공 반환) 실제로는 이 경로가 트리거되지 않는다. UserService의 계약
 * ("NicknameGenerator가 예외를 던지면 NicknameGenerationFailedException으로 감싼다")은 그
 * 자체로 올바르며 이 테스트가 검증하는 대상이다.
 *
 * <p>중복은 생성기를 다시 부르지 않고 "#숫자4자리" 접미사로 해소한다 — 폴백 풀이 8개뿐이라
 * 생성기 재호출만으로는 9번째 사용자부터 영원히 실패하기 때문이다.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NicknameGenerator nicknameGenerator;

    @Mock
    private PlatformTransactionManager transactionManager;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, nicknameGenerator, transactionManager);
    }

    private User user() {
        return user(USER_ID);
    }

    private User user(Long id) {
        return new User(id, "user" + id + "@gsm.hs.kr", "홍길동", 2, 3, 1, "SW과", Role.STUDENT);
    }

    @Test
    void 토글_on_시_닉네임이_없으면_생성해서_저장한다() {
        User user = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(nicknameGenerator.generate()).thenReturn("파란 재귀함수");
        when(userRepository.existsByAnonymousNickname("파란 재귀함수")).thenReturn(false);

        AnonymousToggleResponse response = userService.setAnonymous(USER_ID, true);

        assertTrue(response.anonymousDefault());
        assertEquals("파란 재귀함수", response.anonymousNickname());
        assertEquals("파란 재귀함수", user.getAnonymousNickname());
        assertTrue(user.isAnonymousDefault());
        verify(nicknameGenerator, times(1)).generate();
        verify(userRepository).saveAndFlush(user);
    }

    @Test
    void 토글_on_재호출시_닉네임이_이미_있으면_생성기를_호출하지_않는다() {
        User user = user();
        user.updateAnonymousNickname("기존닉네임");
        user.updateAnonymousDefault(false); // 껐다가 다시 켜는 상황
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        AnonymousToggleResponse response = userService.setAnonymous(USER_ID, true);

        assertTrue(response.anonymousDefault());
        assertEquals("기존닉네임", response.anonymousNickname());
        // 생성 호출 자체를 건너뛰고, 중복 조회도 하지 않는다.
        verifyNoInteractions(nicknameGenerator);
        verify(userRepository, never()).existsByAnonymousNickname(any());
    }

    @Test
    void 토글_off_시_is_anonymous_default만_내리고_닉네임은_보존한다() {
        User user = user();
        user.updateAnonymousNickname("기존닉네임");
        user.updateAnonymousDefault(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        AnonymousToggleResponse response = userService.setAnonymous(USER_ID, false);

        assertFalse(response.anonymousDefault());
        assertEquals("기존닉네임", response.anonymousNickname()); // 보존
        verifyNoInteractions(nicknameGenerator);
    }

    @Test
    void 닉네임이_중복이면_생성기를_다시_부르지_않고_접미사를_붙여_성공한다() {
        User user = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(nicknameGenerator.generate()).thenReturn("용감한고양이");
        when(userRepository.existsByAnonymousNickname(any()))
                .thenAnswer(inv -> "용감한고양이".equals(inv.getArgument(0)));

        AnonymousToggleResponse response = userService.setAnonymous(USER_ID, true);

        assertTrue(response.anonymousNickname().matches("용감한고양이#\\d{4}"), response.anonymousNickname());
        assertEquals(response.anonymousNickname(), user.getAnonymousNickname());
        verify(nicknameGenerator, times(1)).generate();
    }

    @Test
    void 접미사를_붙여도_5회_모두_중복이면_NicknameGenerationFailedException이_발생한다() {
        User user = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(nicknameGenerator.generate()).thenReturn("항상중복");
        when(userRepository.existsByAnonymousNickname(any())).thenReturn(true);

        assertThrows(NicknameGenerationFailedException.class,
                () -> userService.setAnonymous(USER_ID, true));

        verify(nicknameGenerator, times(1)).generate();
        verify(userRepository, times(5)).existsByAnonymousNickname(any());
        verify(userRepository, never()).saveAndFlush(any());
        // 실패했다면 user 엔티티에 닉네임이 써지지 않은 채로 남아야 한다.
        assertNull(user.getAnonymousNickname());
        assertFalse(user.isAnonymousDefault());
    }

    @Test
    void AI_호출_실패시_NicknameGenerationFailedException으로_전파된다() {
        User user = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        RuntimeException openAiFailure = new RuntimeException("OpenAI 호출 실패");
        when(nicknameGenerator.generate()).thenThrow(openAiFailure);

        NicknameGenerationFailedException ex = assertThrows(NicknameGenerationFailedException.class,
                () -> userService.setAnonymous(USER_ID, true));

        assertEquals(openAiFailure, ex.getCause());
        // 실패했다면 5회 모두 재시도하되(매번 AI 호출 실패), existsByAnonymousNickname은 호출되지 않는다.
        verify(nicknameGenerator, times(5)).generate();
        verify(userRepository, never()).existsByAnonymousNickname(any());
    }

    @Test
    void 폴백_풀_8개를_다_쓴_뒤에도_9번째_이후_사용자가_정상_생성된다() {
        // 실제 폴백 풀(내장 예시 8개)을 쓰는 생성기 + 인메모리 UNIQUE 흉내.
        NicknameGenerator pool = new DemoNicknameGenerator();
        userService = new UserService(userRepository, pool, transactionManager);

        Map<Long, User> users = new HashMap<>();
        Set<String> taken = new HashSet<>();
        when(userRepository.findById(any())).thenAnswer(inv ->
                Optional.of(users.computeIfAbsent(inv.getArgument(0), id -> user((Long) id))));
        when(userRepository.existsByAnonymousNickname(any()))
                .thenAnswer(inv -> taken.contains((String) inv.getArgument(0)));
        when(userRepository.saveAndFlush(any())).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            if (saved.getAnonymousNickname() != null) {
                taken.add(saved.getAnonymousNickname());
            }
            return saved;
        });

        int totalUsers = 20; // 풀(8개)의 2.5배
        for (long id = 1; id <= totalUsers; id++) {
            AnonymousToggleResponse response = userService.setAnonymous(id, true);
            assertTrue(response.anonymousDefault());
            assertTrue(response.anonymousNickname() != null && !response.anonymousNickname().isBlank());
        }

        // 전원이 서로 다른 닉네임을 받았고, 접미사 없는 값은 풀 크기(8)를 넘지 못한다.
        assertEquals(totalUsers, taken.size());
        long withoutSuffix = taken.stream().filter(n -> !n.contains("#")).count();
        assertTrue(withoutSuffix <= 8, "접미사 없는 닉네임 수: " + withoutSuffix);
        assertTrue(taken.stream().anyMatch(n -> n.matches(".+#\\d{4}")));
    }

    @Test
    void 저장_시_UNIQUE_위반이_나면_접미사로_재시도해서_성공한다() {
        // 사전 조회(exists)는 통과했지만 동시 토글이 같은 값을 먼저 커밋한 상황.
        // 롤백 후에는 실제로 엔티티가 새로 로딩되므로 호출마다 새 User를 돌려준다.
        when(userRepository.findById(USER_ID)).thenAnswer(inv -> Optional.of(user()));
        when(nicknameGenerator.generate()).thenReturn("파란 재귀함수");
        when(userRepository.existsByAnonymousNickname(any())).thenReturn(false);
        AtomicInteger saveCalls = new AtomicInteger();
        when(userRepository.saveAndFlush(any())).thenAnswer(inv -> {
            if (saveCalls.incrementAndGet() == 1) {
                throw new DataIntegrityViolationException("Duplicate entry for uk_user_anonymous_nickname");
            }
            return inv.getArgument(0);
        });

        AnonymousToggleResponse response = userService.setAnonymous(USER_ID, true);

        assertTrue(response.anonymousDefault());
        assertTrue(response.anonymousNickname().matches("파란 재귀함수#\\d{4}"), response.anonymousNickname());
        verify(userRepository, times(2)).saveAndFlush(any());
        verify(nicknameGenerator, times(1)).generate();
    }

    @Test
    void UNIQUE_위반이_계속되면_500이_아니라_NicknameGenerationFailedException이다() {
        when(userRepository.findById(USER_ID)).thenAnswer(inv -> Optional.of(user()));
        when(nicknameGenerator.generate()).thenReturn("파란 재귀함수");
        when(userRepository.existsByAnonymousNickname(any())).thenReturn(false);
        when(userRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry"));

        NicknameGenerationFailedException ex = assertThrows(NicknameGenerationFailedException.class,
                () -> userService.setAnonymous(USER_ID, true));

        assertTrue(ex.getCause() instanceof DataIntegrityViolationException);
        verify(userRepository, times(5)).saveAndFlush(any());
    }

    @Test
    void 닉네임_생성은_트랜잭션_밖에서_호출된다() {
        AtomicBoolean inTransaction = new AtomicBoolean(false);
        AtomicBoolean generatedInsideTransaction = new AtomicBoolean(false);
        // TransactionTemplate이 begin/commit 때 부르는 PlatformTransactionManager 호출로 경계를 추적한다.
        when(transactionManager.getTransaction(any())).thenAnswer(inv -> {
            inTransaction.set(true);
            return null;
        });
        doAnswer(inv -> {
            inTransaction.set(false);
            return null;
        }).when(transactionManager).commit(any());
        when(nicknameGenerator.generate()).thenAnswer(inv -> {
            generatedInsideTransaction.set(inTransaction.get());
            return "느긋한 이진탐색";
        });
        when(userRepository.findById(USER_ID)).thenAnswer(inv -> Optional.of(user()));
        when(userRepository.existsByAnonymousNickname(any())).thenReturn(false);

        userService.setAnonymous(USER_ID, true);

        assertFalse(generatedInsideTransaction.get(), "생성기가 트랜잭션 안에서 호출됨");
        verify(transactionManager, times(1)).getTransaction(any());
        verify(transactionManager, times(1)).commit(any());
    }
}
