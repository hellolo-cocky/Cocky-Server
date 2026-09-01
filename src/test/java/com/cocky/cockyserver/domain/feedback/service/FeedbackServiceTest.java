package com.cocky.cockyserver.domain.feedback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cocky.cockyserver.ai.dto.Difficulty;
import com.cocky.cockyserver.ai.dto.Language;
import com.cocky.cockyserver.ai.dto.Period;
import com.cocky.cockyserver.ai.dto.PeriodStats;
import com.cocky.cockyserver.ai.port.PeriodFeedbackProvider;
import com.cocky.cockyserver.domain.round.entity.Round;
import com.cocky.cockyserver.domain.round.repository.RoundRepository;
import com.cocky.cockyserver.domain.submission.entity.Verdict;
import com.cocky.cockyserver.domain.submission.repository.SubmissionRepository;
import com.cocky.cockyserver.domain.topic.entity.Topic;
import com.cocky.cockyserver.domain.topic.repository.TopicRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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

    // CI/로컬 timezone에 따라 주간 윈도우 테스트의 요일이 밀리지 않도록 고정 zone을 쓴다.
    private static final ZoneId TEST_ZONE = ZoneOffset.UTC;

    private FeedbackService serviceAt(LocalDateTime now, SubmissionRepository submissionRepository,
                                       RoundRepository roundRepository) {
        Clock clock = Clock.fixed(now.atZone(TEST_ZONE).toInstant(), TEST_ZONE);
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

    // ── 여기부터 aggregateStats() 관련 테스트: enum 매핑 / resolveNextTopic 분기.
    // window()와 달리 topicRepository도 검증 대상이라 직접 mock을 만들어 넘긴다.

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 7, 8, 12, 0); // 수요일

    private FeedbackService newService(SubmissionRepository submissionRepository,
                                        RoundRepository roundRepository, TopicRepository topicRepository) {
        Clock clock = Clock.fixed(FIXED_NOW.atZone(TEST_ZONE).toInstant(), TEST_ZONE);
        PeriodFeedbackProvider periodFeedbackProvider = mock(PeriodFeedbackProvider.class);
        return new FeedbackService(submissionRepository, roundRepository, topicRepository,
                periodFeedbackProvider, clock);
    }

    /** 세 집계 쿼리를 전부 빈 리스트로 스텁한다 — enum 매핑/resolveNextTopic 테스트에서 관심 없는 축은 이걸로 무시. */
    private void stubEmptyAggregates(SubmissionRepository submissionRepository) {
        when(submissionRepository.aggregateLanguageCountsByUserAndPeriod(any(), any(), any()))
                .thenReturn(List.of());
        when(submissionRepository.aggregateDifficultyCountsByUserAndPeriod(any(), any(), any()))
                .thenReturn(List.of());
        when(submissionRepository.aggregateWrongVerdictCountsByUserAndPeriod(any(), any(), any()))
                .thenReturn(List.of());
    }

    private SubmissionRepository.LanguageCount languageCount(
            com.cocky.cockyserver.domain.problem.entity.Language language, long count) {
        SubmissionRepository.LanguageCount row = mock(SubmissionRepository.LanguageCount.class);
        when(row.getLanguage()).thenReturn(language);
        when(row.getCount()).thenReturn(count);
        return row;
    }

    private SubmissionRepository.DifficultyCount difficultyCount(
            com.cocky.cockyserver.domain.problem.entity.Difficulty difficulty, long count) {
        SubmissionRepository.DifficultyCount row = mock(SubmissionRepository.DifficultyCount.class);
        when(row.getDifficulty()).thenReturn(difficulty);
        when(row.getCount()).thenReturn(count);
        return row;
    }

    private SubmissionRepository.VerdictCount verdictCount(Verdict verdict, long count) {
        SubmissionRepository.VerdictCount row = mock(SubmissionRepository.VerdictCount.class);
        when(row.getVerdict()).thenReturn(verdict);
        when(row.getCount()).thenReturn(count);
        return row;
    }

    // Projection이 domain enum을 반환하므로 알 수 없는 enum 값은 재현할 수 없다.

    @Test
    void 언어별_집계가_ai_Language로_정상_변환된다() {
        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        RoundRepository roundRepository = mock(RoundRepository.class);
        TopicRepository topicRepository = mock(TopicRepository.class);
        stubEmptyAggregates(submissionRepository);
        // 주의: helper(languageCount 등) 내부에도 when(...).thenReturn(...)이 있어서, 이 List.of(...)를
        // 바깥 when(...).thenReturn(...) 인자 자리에서 바로 평가하면 Mockito의 "ongoing stubbing" 슬롯이
        // 바깥 when()이 끝나기 전에 안쪽 when()에 뺏겨 UnfinishedStubbingException이 난다. 그래서 리스트를
        // 먼저 로컬 변수로 완성한 뒤 넘긴다.
        List<SubmissionRepository.LanguageCount> rows = List.of(
                languageCount(com.cocky.cockyserver.domain.problem.entity.Language.PYTHON, 3),
                languageCount(com.cocky.cockyserver.domain.problem.entity.Language.C, 1),
                languageCount(com.cocky.cockyserver.domain.problem.entity.Language.JAVA, 2));
        when(submissionRepository.aggregateLanguageCountsByUserAndPeriod(any(), any(), any()))
                .thenReturn(rows);
        when(roundRepository.findTopByOrderByRoundDateDesc()).thenReturn(Optional.empty());

        PeriodStats stats = newService(submissionRepository, roundRepository, topicRepository)
                .aggregateStats(1L, Period.WEEKLY);

        assertThat(stats.languageCounts()).containsExactlyInAnyOrderEntriesOf(
                Map.of(Language.PYTHON, 3, Language.C, 1, Language.JAVA, 2));
    }

    @Test
    void 난이도별_집계가_ai_Difficulty로_정상_변환된다() {
        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        RoundRepository roundRepository = mock(RoundRepository.class);
        TopicRepository topicRepository = mock(TopicRepository.class);
        stubEmptyAggregates(submissionRepository);
        List<SubmissionRepository.DifficultyCount> rows = List.of(
                difficultyCount(com.cocky.cockyserver.domain.problem.entity.Difficulty.EASY, 4),
                difficultyCount(com.cocky.cockyserver.domain.problem.entity.Difficulty.NORMAL, 5),
                difficultyCount(com.cocky.cockyserver.domain.problem.entity.Difficulty.HARD, 1));
        when(submissionRepository.aggregateDifficultyCountsByUserAndPeriod(any(), any(), any()))
                .thenReturn(rows);
        when(roundRepository.findTopByOrderByRoundDateDesc()).thenReturn(Optional.empty());

        PeriodStats stats = newService(submissionRepository, roundRepository, topicRepository)
                .aggregateStats(1L, Period.WEEKLY);

        assertThat(stats.difficultyCounts()).containsExactlyInAnyOrderEntriesOf(
                Map.of(Difficulty.EASY, 4, Difficulty.NORMAL, 5, Difficulty.HARD, 1));
    }

    @Test
    void 오답유형별_집계는_verdict_name을_키로_사용한다() {
        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        RoundRepository roundRepository = mock(RoundRepository.class);
        TopicRepository topicRepository = mock(TopicRepository.class);
        stubEmptyAggregates(submissionRepository);
        List<SubmissionRepository.VerdictCount> rows = List.of(verdictCount(Verdict.WA, 2), verdictCount(Verdict.TLE, 1));
        when(submissionRepository.aggregateWrongVerdictCountsByUserAndPeriod(any(), any(), any()))
                .thenReturn(rows);
        when(roundRepository.findTopByOrderByRoundDateDesc()).thenReturn(Optional.empty());

        PeriodStats stats = newService(submissionRepository, roundRepository, topicRepository)
                .aggregateStats(1L, Period.WEEKLY);

        assertThat(stats.wrongTypeCounts()).containsExactlyInAnyOrderEntriesOf(Map.of("WA", 2, "TLE", 1));
    }

    @Test
    void 집계_결과가_비어있으면_세_Map_모두_빈_Map을_반환한다() {
        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        RoundRepository roundRepository = mock(RoundRepository.class);
        TopicRepository topicRepository = mock(TopicRepository.class);
        stubEmptyAggregates(submissionRepository);
        when(roundRepository.findTopByOrderByRoundDateDesc()).thenReturn(Optional.empty());

        PeriodStats stats = newService(submissionRepository, roundRepository, topicRepository)
                .aggregateStats(1L, Period.WEEKLY);

        assertThat(stats.languageCounts()).isEmpty();
        assertThat(stats.difficultyCounts()).isEmpty();
        assertThat(stats.wrongTypeCounts()).isEmpty();
    }

    // ── resolveNextTopic 분기.

    @Test
    void ROUND_기간은_다음_주제를_조회하지_않고_null을_반환한다() {
        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        RoundRepository roundRepository = mock(RoundRepository.class);
        TopicRepository topicRepository = mock(TopicRepository.class);
        stubEmptyAggregates(submissionRepository);
        Topic topic = new Topic("주제", 3);
        Round closedRound = new Round(topic, LocalDate.of(2026, 7, 6),
                LocalDateTime.of(2026, 7, 6, 0, 0), LocalDateTime.of(2026, 7, 7, 0, 0));
        when(roundRepository.findTopByCloseAtLessThanEqualOrderByCloseAtDesc(any()))
                .thenReturn(Optional.of(closedRound));

        PeriodStats stats = newService(submissionRepository, roundRepository, topicRepository)
                .aggregateStats(1L, Period.ROUND);

        assertThat(stats.nextTopic()).isNull();
        verify(roundRepository, never()).findTopByOrderByRoundDateDesc();
    }

    @Test
    void 최근_마감된_라운드가_없으면_다음_주제는_null이다() {
        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        RoundRepository roundRepository = mock(RoundRepository.class);
        TopicRepository topicRepository = mock(TopicRepository.class);
        stubEmptyAggregates(submissionRepository);
        when(roundRepository.findTopByCloseAtLessThanEqualOrderByCloseAtDesc(any())).thenReturn(Optional.empty());

        PeriodStats stats = newService(submissionRepository, roundRepository, topicRepository)
                .aggregateStats(1L, Period.WEEKLY);

        assertThat(stats.nextTopic()).isNull();
        verify(roundRepository, never()).findTopByOrderByRoundDateDesc();
    }

    @Test
    void 다음_주차_주제가_아직_없으면_null을_반환한다() {
        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        RoundRepository roundRepository = mock(RoundRepository.class);
        TopicRepository topicRepository = mock(TopicRepository.class);
        stubEmptyAggregates(submissionRepository);
        Topic currentTopic = new Topic("3주차_주제", 3);
        Round closedRound = new Round(currentTopic, LocalDate.of(2026, 7, 6),
                LocalDateTime.of(2026, 7, 6, 0, 0), LocalDateTime.of(2026, 7, 7, 0, 0));
        when(roundRepository.findTopByCloseAtLessThanEqualOrderByCloseAtDesc(any()))
                .thenReturn(Optional.of(closedRound));
        when(topicRepository.findByWeekOrder(4)).thenReturn(Optional.empty());

        PeriodStats stats = newService(submissionRepository, roundRepository, topicRepository)
                .aggregateStats(1L, Period.WEEKLY);

        assertThat(stats.nextTopic()).isNull();
        verify(roundRepository, never()).findTopByOrderByRoundDateDesc();
    }

    @Test
    void 다음_주차_주제_이름을_정상적으로_반환한다() {
        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        RoundRepository roundRepository = mock(RoundRepository.class);
        TopicRepository topicRepository = mock(TopicRepository.class);
        stubEmptyAggregates(submissionRepository);
        Topic currentTopic = new Topic("3주차_주제", 3);
        Round closedRound = new Round(currentTopic, LocalDate.of(2026, 7, 6),
                LocalDateTime.of(2026, 7, 6, 0, 0), LocalDateTime.of(2026, 7, 7, 0, 0));
        when(roundRepository.findTopByCloseAtLessThanEqualOrderByCloseAtDesc(any()))
                .thenReturn(Optional.of(closedRound));
        when(topicRepository.findByWeekOrder(4)).thenReturn(Optional.of(new Topic("4주차_주제", 4)));

        PeriodStats stats = newService(submissionRepository, roundRepository, topicRepository)
                .aggregateStats(1L, Period.WEEKLY);

        assertThat(stats.nextTopic()).isEqualTo("4주차_주제");
        verify(roundRepository, never()).findTopByOrderByRoundDateDesc();
    }

    @Test
    void 팔주차_다음은_일주차로_순환하여_다음_주제를_조회한다() {
        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        RoundRepository roundRepository = mock(RoundRepository.class);
        TopicRepository topicRepository = mock(TopicRepository.class);
        stubEmptyAggregates(submissionRepository);
        Topic currentTopic = new Topic("8주차_주제", 8);
        Round closedRound = new Round(currentTopic, LocalDate.of(2026, 7, 6),
                LocalDateTime.of(2026, 7, 6, 0, 0), LocalDateTime.of(2026, 7, 7, 0, 0));
        when(roundRepository.findTopByCloseAtLessThanEqualOrderByCloseAtDesc(any()))
                .thenReturn(Optional.of(closedRound));
        when(topicRepository.findByWeekOrder(1)).thenReturn(Optional.of(new Topic("1주차_주제", 1)));

        PeriodStats stats = newService(submissionRepository, roundRepository, topicRepository)
                .aggregateStats(1L, Period.WEEKLY);

        assertThat(stats.nextTopic()).isEqualTo("1주차_주제");
        verify(topicRepository).findByWeekOrder(1);
        verify(topicRepository, never()).findByWeekOrder(9);
        verify(roundRepository, never()).findTopByOrderByRoundDateDesc();
    }

    /**
     * 회귀 테스트: RoundSchedulerService가 23시에 미리 만들어 두는 "아직 안 끝난 익일 라운드"가
     * DB에 함께 있어도, resolveNextTopic이 그 라운드가 아니라 마감된 라운드(weekOrder 3) 기준으로
     * 다음 주제를 계산해야 한다. findTopByOrderByRoundDateDesc로 되돌아가면(회귀) 미래 라운드
     * (weekOrder 4)가 최신으로 잡혀 next=5가 되고, findByWeekOrder(5)는 스텁돼 있지 않으므로
     * nextTopic이 null로 어긋나 이 테스트가 실패한다.
     */
    @Test
    void 마감된_라운드와_아직_안_끝난_미래_라운드가_공존해도_마감된_라운드_기준으로_다음_주제를_계산한다() {
        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        RoundRepository roundRepository = mock(RoundRepository.class);
        TopicRepository topicRepository = mock(TopicRepository.class);
        stubEmptyAggregates(submissionRepository);

        Topic closedTopic = new Topic("3주차_주제", 3);
        Round closedRound = new Round(closedTopic, LocalDate.of(2026, 7, 6),
                LocalDateTime.of(2026, 7, 6, 0, 0), LocalDateTime.of(2026, 7, 7, 0, 0));
        // 23시 스케줄러가 미리 만들어 둔, FIXED_NOW(2026-07-08 12:00) 기준 아직 안 끝난 익일 라운드.
        Topic futureTopic = new Topic("4주차_주제", 4);
        Round futureRound = new Round(futureTopic, LocalDate.of(2026, 7, 9),
                LocalDateTime.of(2026, 7, 9, 0, 0), LocalDateTime.of(2026, 7, 9, 23, 59, 59));

        when(roundRepository.findTopByCloseAtLessThanEqualOrderByCloseAtDesc(any()))
                .thenReturn(Optional.of(closedRound));
        // 회귀 시 호출될 구 메서드 — 미래 라운드를 리턴하도록 해서, 만약 프로덕션 코드가 이걸로
        // 되돌아가면 next=5가 되어 아래 findByWeekOrder(4) 스텁을 못 타고 테스트가 실패하게 만든다.
        when(roundRepository.findTopByOrderByRoundDateDesc()).thenReturn(Optional.of(futureRound));
        when(topicRepository.findByWeekOrder(4)).thenReturn(Optional.of(new Topic("4주차_주제", 4)));

        PeriodStats stats = newService(submissionRepository, roundRepository, topicRepository)
                .aggregateStats(1L, Period.WEEKLY);

        assertThat(stats.nextTopic()).isEqualTo("4주차_주제");
        verify(roundRepository, never()).findTopByOrderByRoundDateDesc();
    }
}
