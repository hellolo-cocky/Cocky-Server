package com.cocky.cockyserver.ai.service;

import com.cocky.cockyserver.ai.client.OpenAiClient;
import com.cocky.cockyserver.ai.config.AiProperties;
import com.cocky.cockyserver.ai.dto.Period;
import com.cocky.cockyserver.ai.dto.PeriodFeedback;
import com.cocky.cockyserver.ai.dto.PeriodStats;
import com.cocky.cockyserver.ai.port.PeriodFeedbackFailedException;
import com.cocky.cockyserver.ai.port.PeriodFeedbackProvider;
import com.cocky.cockyserver.ai.prompt.PromptTemplates;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 기간(회차/주간/월간) 총평. 통계는 백엔드가 집계해 넘긴다.
 */
public class PeriodFeedbackService implements PeriodFeedbackProvider {

    private final OpenAiClient openAi;
    private final AiProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    public PeriodFeedbackService(OpenAiClient openAi, AiProperties props) {
        this.openAi = openAi;
        this.props = props;
    }

    /**
     * 계약: 실패 시 {@link PeriodFeedbackFailedException}만 port 밖으로 나간다. OpenAiException,
     * JSON 파싱 실패, 빈 총평 등 내부 예외는 전부 여기서 흡수해 감싼다. 즉시 피드백과 달리
     * 재시도 루프는 없다(단계 2) — 단발 호출 실패면 바로 이 예외를 던진다.
     */
    @Override
    public PeriodFeedback summarize(Period period, PeriodStats stats) {
        try {
            String json = openAi.chatJson(modelFor(period),
                    PromptTemplates.PERIOD_SYSTEM,
                    PromptTemplates.periodUser(period, stats));
            JsonNode root = mapper.readTree(json);
            String summary = root.path("summary").asText("");
            String recommend = root.path("studyRecommend").asText("");
            if (summary.isBlank()) {
                throw new IllegalStateException("총평이 비어 있음");
            }
            return new PeriodFeedback(period, summary,
                    period == Period.ROUND || recommend.isBlank() ? null : recommend);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new PeriodFeedbackFailedException("기간 피드백 JSON 파싱 실패: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new PeriodFeedbackFailedException("기간 피드백 생성 실패: " + e.getMessage(), e);
        }
    }

    private String modelFor(Period period) {
        return switch (period) {
            case ROUND -> props.models().roundFeedback();
            case WEEKLY -> props.models().weeklyFeedback();
            case MONTHLY -> props.models().monthlyFeedback();
        };
    }
}
