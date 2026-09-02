package com.cocky.cockyserver.domain.feedback.controller;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cocky.cockyserver.ai.dto.Difficulty;
import com.cocky.cockyserver.ai.dto.Language;
import com.cocky.cockyserver.ai.dto.Period;
import com.cocky.cockyserver.ai.dto.PeriodFeedback;
import com.cocky.cockyserver.ai.dto.PeriodStats;
import com.cocky.cockyserver.domain.feedback.dto.PeriodFeedbackResult;
import com.cocky.cockyserver.domain.feedback.service.FeedbackService;
import com.cocky.cockyserver.domain.user.entity.Role;
import com.cocky.cockyserver.global.exception.GlobalExceptionHandler;
import com.cocky.cockyserver.global.security.UserPrincipal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * GET /api/v1/feedback/periodic 슬라이스 테스트. spring-security-test 의존성이 없어 standalone
 * MockMvc + 커스텀 {@link HandlerMethodArgumentResolver}로 {@code @AuthenticationPrincipal}을 흉내 낸다.
 */
class FeedbackControllerTest {

    private static final Long USER_ID = 1L;

    private FeedbackService feedbackService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        feedbackService = mock(FeedbackService.class);
        UserPrincipal principal = new UserPrincipal(USER_ID, Role.STUDENT);
        mockMvc = MockMvcBuilders.standaloneSetup(new FeedbackController(feedbackService))
                .setCustomArgumentResolvers(new UserPrincipalArgumentResolver(principal))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private PeriodStats stats(Map<Language, Integer> languageCounts, Map<Difficulty, Integer> difficultyCounts,
                               Map<String, Integer> wrongTypeCounts, String nextTopic) {
        return new PeriodStats(languageCounts, difficultyCounts, wrongTypeCounts, nextTopic);
    }

    @Test
    void ROUND_요청시_200과_응답_필드가_정상_직렬화되고_studyRecommend는_null이다() throws Exception {
        PeriodStats stats = stats(
                Map.of(Language.PYTHON, 3, Language.C, 1, Language.JAVA, 2),
                Map.of(Difficulty.EASY, 2, Difficulty.NORMAL, 3, Difficulty.HARD, 1),
                Map.of("WA", 2, "TLE", 1),
                null);
        PeriodFeedback feedback = new PeriodFeedback(Period.ROUND, "이번 회차 총평", null);
        when(feedbackService.getPeriodicFeedbackWithStats(eq(USER_ID), eq(Period.ROUND)))
                .thenReturn(new PeriodFeedbackResult(stats, feedback));

        mockMvc.perform(get("/api/v1/feedback/periodic").param("period", "ROUND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("ROUND"))
                .andExpect(jsonPath("$.languageStats.PYTHON").value(3))
                .andExpect(jsonPath("$.languageStats.C").value(1))
                .andExpect(jsonPath("$.languageStats.JAVA").value(2))
                .andExpect(jsonPath("$.difficultyStats.EASY").value(2))
                .andExpect(jsonPath("$.difficultyStats.NORMAL").value(3))
                .andExpect(jsonPath("$.difficultyStats.HARD").value(1))
                .andExpect(jsonPath("$.wrongTypeStats.WA").value(2))
                .andExpect(jsonPath("$.wrongTypeStats.TLE").value(1))
                .andExpect(jsonPath("$.aiSummary").value("이번 회차 총평"))
                .andExpect(jsonPath("$.studyRecommend").doesNotExist());
    }

    @Test
    void WEEKLY_요청시_200과_studyRecommend가_함께_내려온다() throws Exception {
        PeriodStats stats = stats(Map.of(Language.PYTHON, 5), Map.of(Difficulty.NORMAL, 5),
                Map.of("WA", 1), "4주차_주제");
        PeriodFeedback feedback = new PeriodFeedback(Period.WEEKLY, "이번 주 총평", "4주차_주제 예습 추천");
        when(feedbackService.getPeriodicFeedbackWithStats(eq(USER_ID), eq(Period.WEEKLY)))
                .thenReturn(new PeriodFeedbackResult(stats, feedback));

        mockMvc.perform(get("/api/v1/feedback/periodic").param("period", "WEEKLY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("WEEKLY"))
                .andExpect(jsonPath("$.aiSummary").value("이번 주 총평"))
                .andExpect(jsonPath("$.studyRecommend").value("4주차_주제 예습 추천"));
    }

    @Test
    void MONTHLY_요청시_200과_studyRecommend가_함께_내려온다() throws Exception {
        PeriodStats stats = stats(Map.of(Language.JAVA, 10), Map.of(Difficulty.HARD, 10),
                Map.of("RE", 3), "5주차_주제");
        PeriodFeedback feedback = new PeriodFeedback(Period.MONTHLY, "이번 달 총평", "5주차_주제 예습 추천");
        when(feedbackService.getPeriodicFeedbackWithStats(eq(USER_ID), eq(Period.MONTHLY)))
                .thenReturn(new PeriodFeedbackResult(stats, feedback));

        mockMvc.perform(get("/api/v1/feedback/periodic").param("period", "MONTHLY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("MONTHLY"))
                .andExpect(jsonPath("$.aiSummary").value("이번 달 총평"))
                .andExpect(jsonPath("$.studyRecommend").value("5주차_주제 예습 추천"));
    }

    @Test
    void 집계_결과가_비어있으면_세_stats_필드_모두_빈_객체로_내려온다() throws Exception {
        PeriodStats emptyStats = stats(Map.of(), Map.of(), Map.of(), null);
        PeriodFeedback feedback = new PeriodFeedback(Period.ROUND, "제출 이력 없음", null);
        when(feedbackService.getPeriodicFeedbackWithStats(eq(USER_ID), eq(Period.ROUND)))
                .thenReturn(new PeriodFeedbackResult(emptyStats, feedback));

        mockMvc.perform(get("/api/v1/feedback/periodic").param("period", "ROUND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.languageStats").value(aMapWithSize(0)))
                .andExpect(jsonPath("$.difficultyStats").value(aMapWithSize(0)))
                .andExpect(jsonPath("$.wrongTypeStats").value(aMapWithSize(0)));
    }

    // GlobalExceptionHandler의 공용 enum 바인딩 실패 처리를 그대로 탄다.
    @Test
    void 잘못된_period_값이면_400_INVALID_REQUEST가_내려온다() throws Exception {
        mockMvc.perform(get("/api/v1/feedback/periodic").param("period", "TWO_DAY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void period_파라미터가_없으면_400이_내려온다() throws Exception {
        mockMvc.perform(get("/api/v1/feedback/periodic"))
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
