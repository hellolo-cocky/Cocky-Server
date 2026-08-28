package com.cocky.cockyserver.domain.feedback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cocky.cockyserver.ai.dto.Period;
import com.cocky.cockyserver.ai.port.PeriodFeedbackProvider;
import com.cocky.cockyserver.domain.round.repository.RoundRepository;
import com.cocky.cockyserver.domain.submission.repository.SubmissionRepository;
import com.cocky.cockyserver.domain.topic.repository.TopicRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link FeedbackService#resolveWindow} 기간 경계 계산 회귀 테스트. 실제로 어떤 [start, end)
 * 구간이 집계 쿼리에 전달됐는지는 {@code aggregateLanguageCountsByUserAndPeriod} 호출 인자를
 * 캡처해서 확인한다 — 세 집계 쿼리(언어/난이도/오답유형)가 전부 같은 window를 쓰므로 하나만
 * 확인해도 충분하다.
 */
class FeedbackServiceTest {

    private FeedbackService serviceAt(LocalDateTime now, SubmissionRepository submissionRepository,
                                       RoundRepository roundRepository) {
        Clock clock = Clock.fixed(now.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        TopicRepository topicRepository = mock(TopicRepository.class);
        PeriodFeedbackProvider periodFeedbackProvider = mock(PeriodFeedbackProvider.class);
        return new FeedbackService(submissionRepository, roundRepository, topicRepository,
                periodFeedbackProvider, clock);
    }

    /** 주어진 시각(now)에 WEEKLY 집계를 호출했을 때 실제로 쓰인 [start, end)를 반환한다. */
    private LocalDateTime[] weeklyWindow(LocalDateTime now) {
        return window(now, Period.WEEKLY);
    }

    /** 주어진 시각(now)에 MONTHLY 집계를 호출했을 때 실제로 쓰인 [start, end)를 반환한다. */
    private LocalDateTime[] monthlyWindow(LocalDateTime now) {
        return window(now, Period.MONTHLY);
    }

    private LocalDateTime[] window(LocalDateTime now, Period period) {
        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        RoundRepository roundRepository = mock(RoundRepository.class);
        // nextTopic 계산(WEEKLY/MONTHLY 공통 경로)이 라운드를 조회하므로, 이 테스트의 관심사인
        // 기간 경계 계산과는 무관하게 빈 결과로 흘려보낸다.
        when(roundRepository.findTopByOrderByRoundDateDesc()).thenReturn(Optional.empty());
        when(submissionRepository.aggregateLanguageCountsByUserAndPeriod(any(), any(), any()))
                .thenReturn(List.of());
        when(submissionRepository.aggregateDifficultyCountsByUserAndPeriod(any(), any(), any()))
                .thenReturn(List.of());
        when(submissionRepository.aggregateWrongVerdictCountsByUserAndPeriod(any(), any(), any()))
                .thenReturn(List.of());

        serviceAt(now, submissionRepository, roundRepository).aggregateStats(1L, period);

        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(submissionRepository).aggregateLanguageCountsByUserAndPeriod(
                eq(1L), startCaptor.capture(), endCaptor.capture());
        return new LocalDateTime[] {startCaptor.getValue(), endCaptor.getValue()};
    }

    // 2026-07-08은 수요일(RoundSchedulerServiceTest와 동일 기준일) → 7/6 월, 7/4 토, 6/29 월,
    // 7/11 토, 7/12 일.

    @Test
    void WEEKLY_일요일_호출_시_직전_월요일부터_토요일까지_집계한다() {
        LocalDateTime[] window = weeklyWindow(LocalDateTime.of(2026, 7, 12, 10, 0)); // 일요일

        assertThat(window[0]).isEqualTo(LocalDateTime.of(2026, 7, 6, 0, 0));
        assertThat(window[1]).isEqualTo(LocalDateTime.of(2026, 7, 12, 0, 0));
    }

    @Test
    void WEEKLY_수요일_호출_시_지난주_월요일부터_토요일까지_집계한다() {
        LocalDateTime[] window = weeklyWindow(LocalDateTime.of(2026, 7, 8, 15, 0)); // 수요일

        assertThat(window[0]).isEqualTo(LocalDateTime.of(2026, 6, 29, 0, 0));
        assertThat(window[1]).isEqualTo(LocalDateTime.of(2026, 7, 5, 0, 0));
    }

    @Test
    void WEEKLY_토요일_호출_시_당일이_아니라_지난주_월요일부터_토요일까지_집계한다() {
        LocalDateTime[] window = weeklyWindow(LocalDateTime.of(2026, 7, 11, 9, 0)); // 토요일(당일)

        assertThat(window[0]).isEqualTo(LocalDateTime.of(2026, 6, 29, 0, 0));
        assertThat(window[1]).isEqualTo(LocalDateTime.of(2026, 7, 5, 0, 0));
    }

    @Test
    void MONTHLY_호출_요일과_무관하게_지난달_1일부터_말일까지_집계한다() {
        LocalDateTime[] wednesdayCall = monthlyWindow(LocalDateTime.of(2026, 7, 8, 12, 0));
        LocalDateTime[] saturdayCall = monthlyWindow(LocalDateTime.of(2026, 7, 11, 12, 0));

        LocalDateTime expectedStart = LocalDateTime.of(2026, 6, 1, 0, 0);
        LocalDateTime expectedEnd = LocalDateTime.of(2026, 7, 1, 0, 0);
        assertThat(wednesdayCall).containsExactly(expectedStart, expectedEnd);
        assertThat(saturdayCall).containsExactly(expectedStart, expectedEnd);
    }
}
