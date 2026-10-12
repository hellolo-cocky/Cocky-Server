package com.cocky.cockyserver.domain.round.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cocky.cockyserver.ai.dto.ExampleIo;
import com.cocky.cockyserver.ai.dto.GeneratedProblem;
import com.cocky.cockyserver.ai.dto.GenerationItem;
import com.cocky.cockyserver.ai.dto.GenerationOutcome;
import com.cocky.cockyserver.ai.port.ProblemGenerator;
import com.cocky.cockyserver.domain.admin.entity.AiGenerationLog;
import com.cocky.cockyserver.domain.admin.entity.GenerationStatus;
import com.cocky.cockyserver.domain.admin.repository.AiGenerationLogRepository;
import com.cocky.cockyserver.domain.problem.repository.ProblemRepository;
import com.cocky.cockyserver.domain.problem.repository.TestCaseRepository;
import com.cocky.cockyserver.domain.round.dto.RoundGenerationResult;
import com.cocky.cockyserver.domain.round.entity.Round;
import com.cocky.cockyserver.domain.round.repository.RoundRepository;
import com.cocky.cockyserver.domain.topic.entity.Topic;
import com.cocky.cockyserver.domain.topic.repository.TopicRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class RoundSchedulerServiceTest {

    // 2026-07-08은 수요일 → 다음날(타깃) 2026-07-09는 목요일(일요일 스킵 케이스 배제).
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 8, 23, 0);
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 7, 9);

    @Mock
    private RoundRepository roundRepository;

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private TestCaseRepository testCaseRepository;

    @Mock
    private AiGenerationLogRepository aiGenerationLogRepository;

    @Mock
    private ProblemGenerator problemGenerator;

    @Mock
    private PlatformTransactionManager transactionManager;

    private RoundSchedulerService schedulerService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        schedulerService = new RoundSchedulerService(roundRepository, topicRepository, problemRepository,
                testCaseRepository, aiGenerationLogRepository, problemGenerator, clock, transactionManager);
    }

    private Topic topic() {
        return new Topic("구현", 1);
    }

    /** 최초 실행 가정(직전 라운드 없음) — nextTopicOrder=1로 계획을 세우는 공통 스텁. */
    private void stubFreshPlan() {
        when(roundRepository.findByRoundDate(TARGET_DATE)).thenReturn(Optional.empty());
        when(roundRepository.findTopRoundWithProblems()).thenReturn(Optional.empty());
        when(topicRepository.findByTopicOrder(1)).thenReturn(Optional.of(topic()));
        when(aiGenerationLogRepository.findTop30BySubtypeIsNotNullOrderByCreatedAtDesc()).thenReturn(List.of());
        when(problemRepository.findTop20ByOrderByCreatedAtDesc()).thenReturn(List.of());
    }

    private GenerationItem successItem(com.cocky.cockyserver.ai.dto.Language lang,
                                       com.cocky.cockyserver.ai.dto.Difficulty diff) {
        GeneratedProblem generated = new GeneratedProblem(lang, diff, "제목-" + lang + "-" + diff,
                "지문-" + lang + "-" + diff,
                List.of(new ExampleIo("in1", "out1"), new ExampleIo("in2", "out2")),
                "answer code", "시뮬레이션");
        return GenerationItem.success(generated, 1);
    }

    private GenerationItem failureItem(com.cocky.cockyserver.ai.dto.Language lang,
                                       com.cocky.cockyserver.ai.dto.Difficulty diff) {
        return GenerationItem.failure(lang, diff, 3, "재시도 소진");
    }

    @Test
    void allNineCombinationsSucceed() {
        stubFreshPlan();
        List<GenerationItem> items = new ArrayList<>();
        for (com.cocky.cockyserver.ai.dto.Language lang : com.cocky.cockyserver.ai.dto.Language.values()) {
            for (com.cocky.cockyserver.ai.dto.Difficulty diff : com.cocky.cockyserver.ai.dto.Difficulty.values()) {
                items.add(successItem(lang, diff));
            }
        }
        when(problemGenerator.generate(any())).thenReturn(new GenerationOutcome(items));

        RoundGenerationResult result = schedulerService.triggerRoundGeneration();

        assertFalse(result.skipped());
        assertEquals(TARGET_DATE, result.roundDate());
        assertEquals(9, result.successCount());
        assertEquals(0, result.failureCount());

        ArgumentCaptor<Round> roundCaptor = ArgumentCaptor.forClass(Round.class);
        verify(roundRepository).save(roundCaptor.capture());
        Round savedRound = roundCaptor.getValue();
        assertTrue(savedRound.isActive());
        assertEquals(TARGET_DATE, savedRound.getRoundDate());
        assertEquals(TARGET_DATE.atStartOfDay(), savedRound.getOpenAt());
        assertEquals(TARGET_DATE.atTime(23, 59, 59), savedRound.getCloseAt());

        ArgumentCaptor<AiGenerationLog> logCaptor = ArgumentCaptor.forClass(AiGenerationLog.class);
        verify(aiGenerationLogRepository, times(9)).save(logCaptor.capture());
        List<AiGenerationLog> logs = logCaptor.getAllValues();
        for (int i = 0; i < logs.size(); i++) {
            assertEquals(i + 1, logs.get(i).getSequenceNo());
            assertEquals(GenerationStatus.SUCCESS, logs.get(i).getStatus());
        }

        verify(testCaseRepository, times(18)).save(any());
    }

    @Test
    void sevenSucceedTwoFail_sequenceNoCoversFullNine() {
        stubFreshPlan();
        var lang = com.cocky.cockyserver.ai.dto.Language.PYTHON;
        var diff = com.cocky.cockyserver.ai.dto.Difficulty.EASY;
        // 9칸 중 3번째(seq=3), 7번째(seq=7)를 실패로 섞어 성공/실패가 뒤섞여도
        // sequenceNo가 리스트 내 실제 위치(1~9)를 그대로 반영하는지 검증한다.
        List<GenerationItem> items = List.of(
                successItem(lang, diff), successItem(lang, diff), failureItem(lang, diff),
                successItem(lang, diff), successItem(lang, diff), successItem(lang, diff),
                failureItem(lang, diff), successItem(lang, diff), successItem(lang, diff));
        when(problemGenerator.generate(any())).thenReturn(new GenerationOutcome(items));

        RoundGenerationResult result = schedulerService.triggerRoundGeneration();

        assertFalse(result.skipped());
        assertEquals(7, result.successCount());
        assertEquals(2, result.failureCount());

        ArgumentCaptor<AiGenerationLog> logCaptor = ArgumentCaptor.forClass(AiGenerationLog.class);
        verify(aiGenerationLogRepository, times(9)).save(logCaptor.capture());
        List<AiGenerationLog> logs = logCaptor.getAllValues();
        assertEquals(9, logs.size());
        for (int i = 0; i < logs.size(); i++) {
            assertEquals(i + 1, logs.get(i).getSequenceNo());
        }
        assertEquals(GenerationStatus.FAILED, logs.get(2).getStatus());
        assertEquals(GenerationStatus.FAILED, logs.get(6).getStatus());
        for (int i : List.of(0, 1, 3, 4, 5, 7, 8)) {
            assertEquals(GenerationStatus.SUCCESS, logs.get(i).getStatus());
        }
    }

    @Test
    void nextTopic_isBasedOnLatestRoundWithProblems_notOnEmptyFailedRound() {
        // 리포지토리가 빈 실패 회차를 제외하고 "문제 있는 마지막 회차(topicOrder=3)"를 돌려주는 상황.
        // 빈 회차(topicOrder=4)가 기준이었다면 5가 조회됐을 것 — 4가 조회돼야 토픽이 건너뛰어지지 않은 것.
        when(roundRepository.findByRoundDate(TARGET_DATE)).thenReturn(Optional.empty());
        when(roundRepository.findTopRoundWithProblems()).thenReturn(Optional.of(
                new Round(new Topic("배열", 3), TARGET_DATE.minusDays(2),
                        TARGET_DATE.minusDays(2).atStartOfDay(), TARGET_DATE.minusDays(2).atTime(23, 59, 59))));
        when(topicRepository.findByTopicOrder(4)).thenReturn(Optional.of(new Topic("구현", 4)));
        when(aiGenerationLogRepository.findTop30BySubtypeIsNotNullOrderByCreatedAtDesc()).thenReturn(List.of());
        when(problemRepository.findTop20ByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(problemGenerator.generate(any())).thenReturn(new GenerationOutcome(nineSuccesses()));

        schedulerService.triggerRoundGeneration();

        verify(topicRepository).findByTopicOrder(4);
        verify(topicRepository, never()).findByTopicOrder(5);
    }

    private Round existingRound(boolean active) {
        Round round = new Round(topic(), TARGET_DATE, TARGET_DATE.atStartOfDay(), TARGET_DATE.atTime(23, 59, 59));
        ReflectionTestUtils.setField(round, "id", 5L);
        if (active) {
            round.activate();
        }
        return round;
    }

    private List<GenerationItem> nineFailures() {
        List<GenerationItem> items = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            items.add(GenerationItem.failure(com.cocky.cockyserver.ai.dto.Language.JAVA,
                    com.cocky.cockyserver.ai.dto.Difficulty.EASY, 1, "크레딧 소진"));
        }
        return items;
    }

    private List<GenerationItem> nineSuccesses() {
        List<GenerationItem> items = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            items.add(successItem(com.cocky.cockyserver.ai.dto.Language.PYTHON,
                    com.cocky.cockyserver.ai.dto.Difficulty.EASY));
        }
        return items;
    }

    @Test
    void activeRoundAlreadyExists_skipsWithoutCallingGenerator() {
        when(roundRepository.findByRoundDate(TARGET_DATE)).thenReturn(Optional.of(existingRound(true)));

        RoundGenerationResult result = schedulerService.triggerRoundGeneration();

        assertTrue(result.skipped());
        assertEquals(TARGET_DATE, result.roundDate());
        assertEquals(0, result.successCount());
        assertEquals(0, result.failureCount());
        verify(problemGenerator, never()).generate(any());
        verify(roundRepository, never()).save(any());
    }

    @Test
    void inactiveRoundWithProblems_skipsWithoutCallingGenerator() {
        Round existing = existingRound(false);
        when(roundRepository.findByRoundDate(TARGET_DATE)).thenReturn(Optional.of(existing));
        when(problemRepository.existsByRoundId(5L)).thenReturn(true);

        RoundGenerationResult result = schedulerService.triggerRoundGeneration();

        assertTrue(result.skipped());
        verify(problemGenerator, never()).generate(any());
        assertFalse(existing.isActive());
    }

    @Test
    void allFail_savesInactiveRoundAndNineFailureLogs() {
        stubFreshPlan();
        when(problemGenerator.generate(any())).thenReturn(new GenerationOutcome(nineFailures()));

        RoundGenerationResult result = schedulerService.triggerRoundGeneration();

        assertTrue(result.skipped());
        assertEquals("전체 문제 생성 실패", result.reason());
        assertEquals(0, result.successCount());
        assertEquals(9, result.failureCount());

        ArgumentCaptor<Round> roundCaptor = ArgumentCaptor.forClass(Round.class);
        verify(roundRepository).save(roundCaptor.capture());
        assertFalse(roundCaptor.getValue().isActive());

        ArgumentCaptor<AiGenerationLog> logCaptor = ArgumentCaptor.forClass(AiGenerationLog.class);
        verify(aiGenerationLogRepository, times(9)).save(logCaptor.capture());
        for (int i = 0; i < 9; i++) {
            assertEquals(i + 1, logCaptor.getAllValues().get(i).getSequenceNo());
            assertEquals(GenerationStatus.FAILED, logCaptor.getAllValues().get(i).getStatus());
        }
        verify(problemRepository, never()).save(any());
    }

    @Test
    void retriggerSameDay_reusesEmptyInactiveRoundAndActivatesOnSuccess() {
        Round existing = existingRound(false);
        when(roundRepository.findByRoundDate(TARGET_DATE)).thenReturn(Optional.of(existing));
        when(problemRepository.existsByRoundId(5L)).thenReturn(false);
        when(roundRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(aiGenerationLogRepository.findTop30BySubtypeIsNotNullOrderByCreatedAtDesc()).thenReturn(List.of());
        when(problemRepository.findTop20ByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(problemGenerator.generate(any())).thenReturn(new GenerationOutcome(nineSuccesses()));

        RoundGenerationResult result = schedulerService.triggerRoundGeneration();

        assertFalse(result.skipped());
        assertEquals(9, result.successCount());
        assertTrue(existing.isActive());
        verify(roundRepository, never()).save(any());
        // 이전 실패 로그는 지우지 않는다 — 새 시도 로그만 추가
        verify(aiGenerationLogRepository, never()).deleteAll();
        verify(aiGenerationLogRepository, never()).deleteById(any());
        verify(aiGenerationLogRepository, times(9)).save(any(AiGenerationLog.class));
    }

    @Test
    void retriggerSameDay_allFailAgain_staysInactiveAndAddsNineMoreLogs() {
        Round existing = existingRound(false);
        when(roundRepository.findByRoundDate(TARGET_DATE)).thenReturn(Optional.of(existing));
        when(problemRepository.existsByRoundId(5L)).thenReturn(false);
        when(roundRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(aiGenerationLogRepository.findTop30BySubtypeIsNotNullOrderByCreatedAtDesc()).thenReturn(List.of());
        when(problemRepository.findTop20ByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(problemGenerator.generate(any())).thenReturn(new GenerationOutcome(nineFailures()));

        RoundGenerationResult result = schedulerService.triggerRoundGeneration();

        assertTrue(result.skipped());
        assertEquals(9, result.failureCount());
        assertFalse(existing.isActive());
        verify(roundRepository, never()).save(any());
        verify(aiGenerationLogRepository, times(9)).save(any(AiGenerationLog.class));
    }
}
