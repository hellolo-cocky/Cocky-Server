package com.cocky.cockyserver.domain.feedback.dto;

import com.cocky.cockyserver.ai.dto.PeriodFeedback;
import com.cocky.cockyserver.ai.dto.PeriodStats;

/**
 * {@link com.cocky.cockyserver.domain.feedback.service.FeedbackService#getPeriodicFeedbackWithStats}
 * 결과. 컨트롤러가 응답 DTO(languageStats/difficultyStats 등)를 조립하려면 AI 총평뿐 아니라
 * 집계 원본({@link PeriodStats})도 필요한데, {@code getPeriodicFeedback}은 총평만 반환하고
 * 집계는 내부에서 버린다 — 컨트롤러가 따로 {@code aggregateStats}를 다시 부르면 집계 쿼리
 * 3종이 두 번 돌기 때문에, 한 번의 집계로 둘 다 얻어가는 조립용 캐리어를 별도로 둔다.
 */
public record PeriodFeedbackResult(PeriodStats stats, PeriodFeedback feedback) {
}
