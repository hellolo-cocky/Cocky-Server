package com.cocky.cockyserver.domain.user.controller;

import com.cocky.cockyserver.ai.port.NicknameGenerationFailedException;
import com.cocky.cockyserver.domain.user.dto.AnonymousToggleResponse;
import com.cocky.cockyserver.domain.user.entity.Role;
import com.cocky.cockyserver.domain.user.service.UserService;
import com.cocky.cockyserver.global.exception.GlobalExceptionHandler;
import com.cocky.cockyserver.global.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PATCH /api/v1/users/me/anonymous 슬라이스 테스트. FeedbackControllerTest와 동일하게
 * standalone MockMvc + 커스텀 ArgumentResolver로 @AuthenticationPrincipal을 흉내 낸다.
 */
class UserControllerTest {

    private static final Long USER_ID = 1L;

    private UserService userService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        UserPrincipal principal = new UserPrincipal(USER_ID, Role.STUDENT);
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService))
                .setCustomArgumentResolvers(new UserPrincipalArgumentResolver(principal))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void anonymous_true_요청시_200과_닉네임이_내려온다() throws Exception {
        when(userService.setAnonymous(eq(USER_ID), eq(true)))
                .thenReturn(new AnonymousToggleResponse(true, "파란 재귀함수"));

        mockMvc.perform(patch("/api/v1/users/me/anonymous")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anonymousDefault\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anonymousDefault").value(true))
                .andExpect(jsonPath("$.anonymousNickname").value("파란 재귀함수"));
    }

    @Test
    void 닉네임_생성이_실패하면_503_NICKNAME_GENERATION_FAILED가_내려온다() throws Exception {
        when(userService.setAnonymous(eq(USER_ID), anyBoolean()))
                .thenThrow(new NicknameGenerationFailedException(
                        "익명 닉네임 생성 실패(총 5회 시도 소진)", null));

        mockMvc.perform(patch("/api/v1/users/me/anonymous")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anonymousDefault\": true}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("NICKNAME_GENERATION_FAILED"));
    }

    @Test
    void anonymousDefault_필드가_없으면_400이_내려온다() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/anonymous")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private static class UserPrincipalArgumentResolver implements HandlerMethodArgumentResolver {

        private final UserPrincipal principal;

        UserPrincipalArgumentResolver(UserPrincipal principal) {
            this.principal = principal;
        }

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType().equals(UserPrincipal.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                       NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            return principal;
        }
    }
}
