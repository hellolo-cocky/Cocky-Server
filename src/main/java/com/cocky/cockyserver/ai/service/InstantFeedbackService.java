package com.cocky.cockyserver.ai.service;

import com.cocky.cockyserver.ai.client.OpenAiClient;
import com.cocky.cockyserver.ai.config.AiProperties;
import com.cocky.cockyserver.ai.dto.FeedbackCategory;
import com.cocky.cockyserver.ai.dto.FeedbackItem;
import com.cocky.cockyserver.ai.dto.InstantFeedback;
import com.cocky.cockyserver.ai.dto.Submission;
import com.cocky.cockyserver.ai.port.InstantFeedbackFailedException;
import com.cocky.cockyserver.ai.port.InstantFeedbackProvider;
import com.cocky.cockyserver.ai.prompt.PromptTemplates;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 제출 즉시 피드백. 3항목 각 0.00~10.00, 합 최대 30.00.
 */
public class InstantFeedbackService implements InstantFeedbackProvider {

    private static final Logger log = LoggerFactory.getLogger(InstantFeedbackService.class);

    private static final BigDecimal MAX_ITEM_SCORE = new BigDecimal("10.00");
    /** 평가 항목 수 고정(3×10.00=30.00). 초과/미달 응답은 신뢰 불가 — 합계 상한 붕괴 방지. */
    private static final int REQUIRED_ITEM_COUNT = 3;

    private final OpenAiClient openAi;
    private final AiProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    public InstantFeedbackService(OpenAiClient openAi, AiProperties props) {
        this.openAi = openAi;
        this.props = props;
    }

    /**
     * 계약: 총 시도({@code ai.instant-feedback.max-attempts} — 문제 생성 경로와 별개 설정,
     * 기본 1회) 소진 시 {@link InstantFeedbackFailedException}만 port 밖으로 나간다.
     * OpenAiException 등 내부 예외는 여기서 흡수한다.
     *
     * <p>제출 API가 동기로 기다리는 경로라 타임아웃(기본 10s)·시도 횟수 둘 다 문제 생성보다
     * 짧게 잡는다 — {@code OpenAiClient} 생성 지점(AiConfig)에서 이미 짧은 타임아웃으로 만들어진
     * 인스턴스를 주입받는다(단계 1).
     */
    @Override
    public InstantFeedback evaluate(Submission submission) {
        int maxAttempts = props.instantFeedback().maxAttempts();
        RuntimeException last = null;
        // maxAttempts는 재시도 횟수가 아니라 총 시도 횟수다 (1이면 단발 호출)
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return evaluateOnce(submission);
            } catch (RuntimeException e) {
                last = e;
                log.warn("즉시 피드백 시도 {} 실패: {}", attempt, e.getMessage());
            }
        }
        log.error("즉시 피드백 총 {}회 시도 모두 실패: {}", maxAttempts,
                last == null ? "알 수 없음" : last.getMessage());
        throw new InstantFeedbackFailedException(
                "즉시 피드백 생성 실패(총 " + maxAttempts + "회 시도 소진)", last);
    }

    private InstantFeedback evaluateOnce(Submission submission) {
        String json = openAi.chatJson(props.models().instantFeedback(),
                PromptTemplates.INSTANT_SYSTEM,
                PromptTemplates.instantUser(submission));
        try {
            JsonNode root = mapper.readTree(json);
            List<FeedbackItem> items = new ArrayList<>();
            for (JsonNode item : root.path("items")) {
                BigDecimal score = clampScore(parseScore(item.path("score")));
                items.add(new FeedbackItem(
                        FeedbackCategory.fromLabel(item.path("category").asText("")),
                        score,
                        item.path("comment").asText("")));
            }
            if (items.size() != REQUIRED_ITEM_COUNT) {
                throw new IllegalStateException(
                        "피드백 항목은 정확히 " + REQUIRED_ITEM_COUNT + "개여야 함: " + items.size());
            }
            if (items.stream().map(FeedbackItem::category).distinct().count() != REQUIRED_ITEM_COUNT) {
                throw new IllegalStateException("피드백 category 중복: " + items);
            }
            return new InstantFeedback(items, root.path("personality").asText(""));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("즉시 피드백 JSON 파싱 실패: " + e.getMessage(), e);
        }
    }

    private BigDecimal parseScore(JsonNode node) {
        try {
            return new BigDecimal(node.asText("0"));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal clampScore(BigDecimal raw) {
        BigDecimal v = raw == null ? BigDecimal.ZERO : raw;
        if (v.compareTo(BigDecimal.ZERO) < 0) {
            v = BigDecimal.ZERO;
        } else if (v.compareTo(MAX_ITEM_SCORE) > 0) {
            v = MAX_ITEM_SCORE;
        }
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
