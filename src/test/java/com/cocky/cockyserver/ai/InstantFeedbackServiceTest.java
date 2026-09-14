package com.cocky.cockyserver.ai;

import com.cocky.cockyserver.ai.client.OpenAiClient;
import com.cocky.cockyserver.ai.client.OpenAiException;
import com.cocky.cockyserver.ai.config.AiProperties;
import com.cocky.cockyserver.ai.dto.Difficulty;
import com.cocky.cockyserver.ai.dto.InstantFeedback;
import com.cocky.cockyserver.ai.dto.Language;
import com.cocky.cockyserver.ai.dto.Submission;
import com.cocky.cockyserver.ai.port.InstantFeedbackFailedException;
import com.cocky.cockyserver.ai.service.InstantFeedbackService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InstantFeedbackServiceTest {

    private static final String VALID_JSON = """
            {"items":[
              {"category":"시간복잡도 효율","score":"8.50","comment":"좋음"},
              {"category":"코드 가독성","score":"7.00","comment":"보통"},
              {"category":"풀이 독창성","score":"9.00","comment":"깔끔"}
            ],"personality":"차분한 분석가"}
            """;

    /** 기본값(instantFeedback.maxAttempts=1)을 그대로 쓴다 — 실제 운영 설정과 동일 경로 검증. */
    private final AiProperties props = new AiProperties(null, null, null, null, null, null);

    private Submission submission() {
        return new Submission(Language.JAVA, Difficulty.EASY, "문제", "코드");
    }

    /** chatJson 호출마다 스크립트 순서대로 처리. 예외 항목은 던지고 문자열 항목은 응답으로 반환. */
    private static class ScriptedOpenAiClient extends OpenAiClient {
        private final Object[] script; // String(응답) 또는 RuntimeException(던짐)
        private int calls = 0;

        ScriptedOpenAiClient(AiProperties props, Object... script) {
            super(props, new ObjectMapper());
            this.script = script;
        }

        @Override
        public String chatJson(String model, String systemPrompt, String userPrompt) {
            Object step = script[Math.min(calls++, script.length - 1)];
            if (step instanceof RuntimeException e) {
                throw e;
            }
            return (String) step;
        }

        int calls() {
            return calls;
        }
    }

    @Test
    void firstAttemptSuccess() {
        ScriptedOpenAiClient client = new ScriptedOpenAiClient(props, VALID_JSON);
        InstantFeedback fb = new InstantFeedbackService(client, props).evaluate(submission());
        assertEquals(3, fb.items().size());
        assertEquals(new BigDecimal("8.50"), fb.items().get(0).score());
        assertEquals(1, client.calls());
    }

    @Test
    void retriesThenSucceeds() {
        // 재시도 자체가 동작하는지 보려는 테스트라 기본값(maxAttempts=1)으로는 검증이 안 된다 —
        // instantFeedback.maxAttempts만 3으로 올린 전용 props를 쓴다.
        AiProperties retryProps = new AiProperties(null, null, null, null, null,
                new AiProperties.InstantFeedback(10_000, 3));
        ScriptedOpenAiClient client = new ScriptedOpenAiClient(retryProps,
                new OpenAiException("타임아웃"),
                new OpenAiException("429"),
                VALID_JSON);
        InstantFeedback fb = new InstantFeedbackService(client, retryProps).evaluate(submission());
        assertEquals(3, fb.items().size());
        assertEquals(3, client.calls());
    }

    @Test
    void exhaustedRetriesThrowContractExceptionNotOpenAiException() {
        ScriptedOpenAiClient client = new ScriptedOpenAiClient(props,
                new OpenAiException("500"));
        InstantFeedbackFailedException ex = assertThrows(InstantFeedbackFailedException.class,
                () -> new InstantFeedbackService(client, props).evaluate(submission()));
        assertInstanceOf(OpenAiException.class, ex.getCause());
        assertEquals(props.instantFeedback().maxAttempts(), client.calls());
    }

    @Test
    void invalidItemCountAlsoRetriedAndWrapped() {
        ScriptedOpenAiClient client = new ScriptedOpenAiClient(props,
                "{\"items\":[{\"category\":\"시간복잡도 효율\",\"score\":\"5\",\"comment\":\"x\"}],\"personality\":\"p\"}");
        assertThrows(InstantFeedbackFailedException.class,
                () -> new InstantFeedbackService(client, props).evaluate(submission()));
        assertEquals(props.instantFeedback().maxAttempts(), client.calls());
    }

    @Test
    void unknownCategoryRetriedAndWrapped() {
        String json = VALID_JSON.replace("시간복잡도 효율", "정확성");
        ScriptedOpenAiClient client = new ScriptedOpenAiClient(props, json);
        assertThrows(InstantFeedbackFailedException.class,
                () -> new InstantFeedbackService(client, props).evaluate(submission()));
        assertEquals(props.instantFeedback().maxAttempts(), client.calls());
    }

    @Test
    void duplicateCategoryRetriedAndWrapped() {
        String json = VALID_JSON.replace("코드 가독성", "시간복잡도 효율");
        ScriptedOpenAiClient client = new ScriptedOpenAiClient(props, json);
        assertThrows(InstantFeedbackFailedException.class,
                () -> new InstantFeedbackService(client, props).evaluate(submission()));
        assertEquals(props.instantFeedback().maxAttempts(), client.calls());
    }
}
