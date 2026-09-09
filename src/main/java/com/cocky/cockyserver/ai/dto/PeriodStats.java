package com.cocky.cockyserver.ai.dto;

import java.util.Map;

/**
 * 기간 통계 (백엔드가 집계해 넘김 → AI는 총평만 생성).
 *
 * @param languageCounts   언어별 제출 수.
 * @param difficultyCounts 난이도별 선택 수.
 * @param wrongTypeCounts  틀린 유형별 빈도.
 * @param nextTopic        다음 기간 대주제(주간/월간 예습 추천용, null 가능).
 */
public record PeriodStats(
        Map<Language, Long> languageCounts,
        Map<Difficulty, Long> difficultyCounts,
        Map<String, Long> wrongTypeCounts,
        String nextTopic
) {
}
