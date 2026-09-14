package com.cocky.cockyserver.domain.user.service;

import com.cocky.cockyserver.ai.port.NicknameGenerationFailedException;
import com.cocky.cockyserver.ai.port.NicknameGenerator;
import com.cocky.cockyserver.domain.user.dto.AnonymousToggleResponse;
import com.cocky.cockyserver.domain.user.entity.Role;
import com.cocky.cockyserver.domain.user.entity.User;
import com.cocky.cockyserver.domain.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 단계 3: 익명 닉네임 토글(UserService.setAnonymous) 검증.
 *
 * <p>"AI 호출 실패" 케이스는 NicknameGenerator를 mock으로 대체해 예외를 강제로 던지는 방식으로만
 * 검증한다 — real 구현체({@code NicknameService})는 OpenAI 호출 실패를 내부에서 흡수해 내장
 * 예시로 폴백하므로(항상 성공 반환) 실제로는 이 경로가 트리거되지 않는다. UserService의 계약
 * ("NicknameGenerator가 예외를 던지면 NicknameGenerationFailedException으로 감싼다")은 그
 * 자체로 올바르며 이 테스트가 검증하는 대상이다.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NicknameGenerator nicknameGenerator;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, nicknameGenerator);
    }

    private User user() {
        return new User(USER_ID, "user@gsm.hs.kr", "홍길동", 2, 3, 1, "SW과", Role.STUDENT);
    }

    @Test
    void 토글_on_시_닉네임이_없으면_생성해서_저장한다() {
        User user = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(nicknameGenerator.generate()).thenReturn("파란 재귀함수");
        when(userRepository.existsByAnonymousNickname("파란 재귀함수")).thenReturn(false);

        AnonymousToggleResponse response = userService.setAnonymous(USER_ID, true);

        assertTrue(response.anonymous());
        assertEquals("파란 재귀함수", response.anonymousNickname());
        assertEquals("파란 재귀함수", user.getAnonymousNickname());
        assertTrue(user.isAnonymous());
        verify(nicknameGenerator, times(1)).generate();
    }

    @Test
    void 토글_on_재호출시_닉네임이_이미_있으면_재생성하지_않는다() {
        User user = user();
        user.updateAnonymousNickname("기존닉네임");
        user.updateAnonymous(false); // 껐다가 다시 켜는 상황
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        AnonymousToggleResponse response = userService.setAnonymous(USER_ID, true);

        assertTrue(response.anonymous());
        assertEquals("기존닉네임", response.anonymousNickname());
        verify(nicknameGenerator, never()).generate();
    }

    @Test
    void 토글_off_시_is_anonymous만_내리고_닉네임은_보존한다() {
        User user = user();
        user.updateAnonymousNickname("기존닉네임");
        user.updateAnonymous(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        AnonymousToggleResponse response = userService.setAnonymous(USER_ID, false);

        assertFalse(response.anonymous());
        assertEquals("기존닉네임", response.anonymousNickname()); // 보존
        verify(nicknameGenerator, never()).generate();
    }

    @Test
    void 중복이_발생하면_재생성_후_성공한다() {
        User user = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(nicknameGenerator.generate()).thenReturn("중복", "중복", "고유닉네임");
        when(userRepository.existsByAnonymousNickname("중복")).thenReturn(true);
        when(userRepository.existsByAnonymousNickname("고유닉네임")).thenReturn(false);

        AnonymousToggleResponse response = userService.setAnonymous(USER_ID, true);

        assertEquals("고유닉네임", response.anonymousNickname());
        verify(nicknameGenerator, times(3)).generate();
    }

    @Test
    void 중복이_5회_모두_발생하면_NicknameGenerationFailedException이_발생한다() {
        User user = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(nicknameGenerator.generate()).thenReturn("항상중복");
        when(userRepository.existsByAnonymousNickname("항상중복")).thenReturn(true);

        assertThrows(NicknameGenerationFailedException.class,
                () -> userService.setAnonymous(USER_ID, true));

        verify(nicknameGenerator, times(5)).generate();
        // 5회 소진으로 예외가 났으면 user 엔티티에 닉네임이 써지지 않은 채로 남아야 한다.
        assertNull(user.getAnonymousNickname());
        assertFalse(user.isAnonymous());
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
}
