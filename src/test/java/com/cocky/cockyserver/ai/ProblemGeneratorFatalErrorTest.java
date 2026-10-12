package com.cocky.cockyserver.ai;

import com.cocky.cockyserver.ai.client.OpenAiClient;
import com.cocky.cockyserver.ai.client.OpenAiException;
import com.cocky.cockyserver.ai.config.AiProperties;
import com.cocky.cockyserver.ai.dto.Difficulty;
import com.cocky.cockyserver.ai.dto.ExecRequest;
import com.cocky.cockyserver.ai.dto.ExecResult;
import com.cocky.cockyserver.ai.dto.GenerationItem;
import com.cocky.cockyserver.ai.dto.GenerationOutcome;
import com.cocky.cockyserver.ai.dto.GenerationRequest;
import com.cocky.cockyserver.ai.dto.Language;
import com.cocky.cockyserver.ai.port.CodeExecutor;
import com.cocky.cockyserver.ai.service.ProblemGeneratorService;
import com.cocky.cockyserver.ai.service.SimilarityChecker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 재시도 불가 OpenAI 오류(크레딧 소진·키 오류)는 즉시 포기하고 남은 조합도 시도하지 않는다. */
class ProblemGeneratorFatalErrorTest {

    /** maxAttempts=3 (운영 기본값과 동일). */
    private final AiProperties props = new AiProperties(null, null, null, null, null, null);

    private static class ThrowingOpenAiClient extends OpenAiClient {
        private final RuntimeException error;
        private int calls = 0;

        ThrowingOpenAiClient(AiProperties props, RuntimeException error) {
            super(props, new ObjectMapper());
            this.error = error;
        }

        @Override
        public String chatJson(String model, String systemPrompt, String userPrompt) {
            calls++;
            throw error;
        }

        int calls() {
            return calls;
        }
    }

    private static final CodeExecutor NO_EXEC = new CodeExecutor() {
        @Override
        public ExecResult run(ExecRequest request) {
            throw new AssertionError("실행기는 호출되면 안 된다");
        }

        @Override
        public boolean available() {
            return true;
        }
    };

    private GenerationRequest javaAllDifficulties() {
        return new GenerationRequest(List.of(Language.JAVA), List.of(Difficulty.values()),
                "동적 프로그래밍", "메모이제이션", List.of(), List.of());
    }

    private GenerationOutcome run(ThrowingOpenAiClient client) {
        return new ProblemGeneratorService(client, NO_EXEC, new SimilarityChecker(), props)
                .generate(javaAllDifficulties());
    }

    @Test
    void 크레딧소진이면_첫_시도에서_포기하고_남은_조합은_호출하지_않는다() {
        var client = new ThrowingOpenAiClient(props,
                new OpenAiException("429 insufficient_quota", null, false));

        GenerationOutcome outcome = run(client);

        assertEquals(1, client.calls(), "재시도도, 다음 조합 시도도 없어야 한다");
        assertEquals(3, outcome.items().size(), "조합 수(EASY/NORMAL/HARD)는 그대로 실패 레코드로 채운다");
        assertTrue(outcome.items().stream().noneMatch(GenerationItem::success));
        assertEquals(1, outcome.items().get(0).attempts());
        assertEquals(0, outcome.items().get(1).attempts());
        assertTrue(outcome.items().get(1).failReason().contains("건너뜀"));
    }

    @Test
    void 키오류도_즉시_포기한다() {
        var client = new ThrowingOpenAiClient(props, new OpenAiException("401", null, false));

        run(client);

        assertEquals(1, client.calls());
    }

    @Test
    void 일반_오류는_조합당_maxAttempts만큼_재시도하고_다음_조합도_시도한다() {
        var client = new ThrowingOpenAiClient(props, new OpenAiException("429 rate limit"));

        GenerationOutcome outcome = run(client);

        assertEquals(9, client.calls(), "3조합 × 3회 — 기존 동작 유지");
        assertEquals(3, outcome.items().size());
        assertTrue(outcome.items().stream().allMatch(i -> i.attempts() == 3));
        assertFalse(outcome.complete());
    }
}
