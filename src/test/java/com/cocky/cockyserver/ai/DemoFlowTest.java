package com.cocky.cockyserver.ai;

import com.cocky.cockyserver.ai.demo.DemoInstantFeedback;
import com.cocky.cockyserver.ai.demo.DemoNicknameGenerator;
import com.cocky.cockyserver.ai.demo.DemoPeriodFeedback;
import com.cocky.cockyserver.ai.demo.DemoProblemGenerator;
import com.cocky.cockyserver.ai.dto.Difficulty;
import com.cocky.cockyserver.ai.dto.GeneratedProblem;
import com.cocky.cockyserver.ai.dto.GenerationRequest;
import com.cocky.cockyserver.ai.dto.InstantFeedback;
import com.cocky.cockyserver.ai.dto.Language;
import com.cocky.cockyserver.ai.dto.Period;
import com.cocky.cockyserver.ai.dto.PeriodFeedback;
import com.cocky.cockyserver.ai.dto.PeriodStats;
import com.cocky.cockyserver.ai.dto.Submission;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 데모 모드(키 없이) 전체 플로우 계약 검증.
 */
class DemoFlowTest {

    @Test
    void generatesExactlyNineValidProblems() {
        var gen = new DemoProblemGenerator();
        var req = GenerationRequest.fullRound("동적 프로그래밍", "메모이제이션", List.of(), List.of());

        var outcome = gen.generate(req);
        assertTrue(outcome.complete());
        assertTrue(outcome.failures().isEmpty());
        List<GeneratedProblem> problems = outcome.problems();

        assertEquals(9, problems.size());
        for (GeneratedProblem p : problems) {
            assertFalse(p.statement().isBlank());
            assertFalse(p.answerCode().isBlank());
            assertFalse(p.examples().isEmpty());
        }
        // 언어×난이도 조합이 정확히 한 번씩.
        long distinct = problems.stream().map(p -> p.language() + "/" + p.difficulty()).distinct().count();
        assertEquals(9, distinct);
    }

    @Test
    void instantFeedbackTotalsThirty() {
        var fb = new DemoInstantFeedback();
        InstantFeedback result = fb.evaluate(new Submission(Language.PYTHON, Difficulty.EASY, "문제", "print(1)"));

        assertEquals(3, result.items().size());
        assertEquals(new BigDecimal("30.00"), result.total());
        assertEquals(2, result.total().scale());
    }

    @Test
    void roundFeedbackHasNoRecommendButWeeklyDoes() {
        var pf = new DemoPeriodFeedback();
        var stats = new PeriodStats(Map.of(Language.PYTHON, 5L), Map.of(Difficulty.EASY, 3L), Map.of(), "그래프 탐색");

        PeriodFeedback round = pf.summarize(Period.ROUND, stats);
        assertFalse(round.summary().isBlank());
        assertNull(round.studyRecommend());

        PeriodFeedback weekly = pf.summarize(Period.WEEKLY, stats);
        assertNotNull(weekly.studyRecommend());
        assertFalse(weekly.studyRecommend().isBlank());
    }

    @Test
    void nicknameIsNonBlank() {
        assertTrue(new DemoNicknameGenerator().generate() != null
                && !new DemoNicknameGenerator().generate().isBlank());
    }
}
