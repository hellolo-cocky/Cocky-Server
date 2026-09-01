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
        when(roundRepository.findTopByOrderByRoundDateDesc()).thenReturn(Optional.empty());
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
    void roundAlreadyExists_skipsWithoutCallingGenerator() {
        Round existing = new Round(topic(), TARGET_DATE, TARGET_DATE.atStartOfDay(), TARGET_DATE.atTime(23, 59, 59));
        when(roundRepository.findByRoundDate(TARGET_DATE)).thenReturn(Optional.of(existing));

        RoundGenerationResult result = schedulerService.triggerRoundGeneration();

        assertTrue(result.skipped());
        assertEquals(TARGET_DATE, result.roundDate());
        assertEquals(0, result.successCount());
        assertEquals(0, result.failureCount());
        verify(problemGenerator, never()).generate(any());
        verify(roundRepository, never()).save(any());
    }
}
