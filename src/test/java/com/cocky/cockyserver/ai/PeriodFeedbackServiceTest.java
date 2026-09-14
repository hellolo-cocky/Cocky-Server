package com.cocky.cockyserver.ai;

import com.cocky.cockyserver.ai.client.OpenAiClient;
import com.cocky.cockyserver.ai.client.OpenAiException;
import com.cocky.cockyserver.ai.config.AiProperties;
import com.cocky.cockyserver.ai.dto.Period;
import com.cocky.cockyserver.ai.dto.PeriodFeedback;
import com.cocky.cockyserver.ai.dto.PeriodStats;
import com.cocky.cockyserver.ai.port.PeriodFeedbackFailedException;
import com.cocky.cockyserver.ai.service.PeriodFeedbackService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 단계 2: PeriodFeedbackProvider.summarize() 실패 계약 검증.
 * 재시도 루프가 없으므로 chatJson 호출은 항상 1회여야 한다.
 */
class PeriodFeedbackServiceTest {

    private static final String VALID_JSON = """
            {"summary":"이번 주 잘했음","studyRecommend":"다음 주제 예습"}
            """;

    private final AiProperties props = new AiProperties(null, null, null, null, null, null);

    private PeriodStats stats() {
        return new PeriodStats(Map.of(), Map.of(), Map.of(), null);
    }

    /** chatJson 호출 시 스크립트대로 응답하거나 예외를 던진다. 몇 회 호출됐는지도 센다. */
    private static class ScriptedOpenAiClient extends OpenAiClient {
        private final Object script; // String(응답) 또는 RuntimeException(던짐)
        private int calls = 0;

        ScriptedOpenAiClient(AiProperties props, Object script) {
            super(props, new ObjectMapper());
            this.script = script;
        }

        @Override
        public String chatJson(String model, String systemPrompt, String userPrompt) {
            calls++;
            if (script instanceof RuntimeException e) {
                throw e;
            }
            return (String) script;
        }

        int calls() {
            return calls;
        }
    }

    @Test
    void 성공시_PeriodFeedback을_그대로_반환한다() {
        ScriptedOpenAiClient client = new ScriptedOpenAiClient(props, VALID_JSON);

        PeriodFeedback fb = new PeriodFeedbackService(client, props).summarize(Period.WEEKLY, stats());

        assertEquals("이번 주 잘했음", fb.summary());
        assertEquals("다음 주제 예습", fb.studyRecommend());
        assertEquals(1, client.calls());
    }

    @Test
    void OpenAI_호출_실패는_PeriodFeedbackFailedException으로_전파되고_재시도하지_않는다() {
        ScriptedOpenAiClient client = new ScriptedOpenAiClient(props, new OpenAiException("500"));

        PeriodFeedbackFailedException ex = assertThrows(PeriodFeedbackFailedException.class,
                () -> new PeriodFeedbackService(client, props).summarize(Period.ROUND, stats()));

        assertInstanceOf(OpenAiException.class, ex.getCause());
        assertEquals(1, client.calls()); // 재시도 루프 없음 — 단발 호출
    }

    @Test
    void JSON_파싱_실패는_PeriodFeedbackFailedException으로_전파된다() {
        ScriptedOpenAiClient client = new ScriptedOpenAiClient(props, "이것은 JSON이 아님");

        PeriodFeedbackFailedException ex = assertThrows(PeriodFeedbackFailedException.class,
                () -> new PeriodFeedbackService(client, props).summarize(Period.WEEKLY, stats()));

        assertInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class, ex.getCause());
        assertEquals(1, client.calls());
    }

    @Test
    void 총평이_비어있으면_PeriodFeedbackFailedException으로_전파된다() {
        ScriptedOpenAiClient client = new ScriptedOpenAiClient(props,
                "{\"summary\":\"\",\"studyRecommend\":\"\"}");

        PeriodFeedbackFailedException ex = assertThrows(PeriodFeedbackFailedException.class,
                () -> new PeriodFeedbackService(client, props).summarize(Period.MONTHLY, stats()));

        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertEquals(1, client.calls());
    }
}
