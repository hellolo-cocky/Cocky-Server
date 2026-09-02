package com.cocky.cockyserver.domain.feedback.dto;

import com.cocky.cockyserver.ai.dto.Period;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GET /feedback/periodic 응답. languageStats/difficultyStats는 {@code ai.dto.PeriodStats}가
 * enum(Language/Difficulty)을 키로 쓰는 걸 String 키로 변환해 노출한다 — Jackson의 enum-키
 * 자동 직렬화(ObjectMapper 설정에 따라 형태가 바뀔 수 있음)에 기대지 않고 API 응답 키를
 * 명세대로 고정하기 위함이다.
 *
 * @param wrongTypeStats 이미 {@code PeriodStats.wrongTypeCounts}가 verdict name을 키로 쓰므로 그대로 통과.
 * @param studyRecommend ROUND는 null(명세대로 예습 추천 없음).
 */
public record PeriodFeedbackResponse(
        Period period,
        Map<String, Integer> languageStats,
        Map<String, Integer> difficultyStats,
        Map<String, Integer> wrongTypeStats,
        String aiSummary,
        String studyRecommend
) {

    public static PeriodFeedbackResponse from(PeriodFeedbackResult result) {
        return new PeriodFeedbackResponse(
                result.feedback().period(),
                toStringKeyMap(result.stats().languageCounts()),
                toStringKeyMap(result.stats().difficultyCounts()),
                result.stats().wrongTypeCounts(),
                result.feedback().summary(),
                result.feedback().studyRecommend());
    }

    private static <E extends Enum<E>> Map<String, Integer> toStringKeyMap(Map<E, Integer> source) {
        Map<String, Integer> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key.name(), value));
        return result;
    }
}
