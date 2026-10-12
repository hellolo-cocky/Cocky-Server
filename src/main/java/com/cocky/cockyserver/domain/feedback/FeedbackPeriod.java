package com.cocky.cockyserver.domain.feedback;

/**
 * GET /feedback/periodic 의 기간 단위(요청 파라미터·응답 값). 값 이름(ROUND/WEEKLY/MONTHLY)이
 * API 계약이다.
 *
 * <p>{@code ai.dto.Period}(AI 모듈 계약 타입)와 일부러 분리했다 — API 계층이 AI 구현 종속 타입에
 * 묶이지 않도록 하기 위함이다. AI 호출 직전에 {@code FeedbackService}가 명시 switch로 변환하므로
 * 한쪽에 값이 추가되면 변환부에서 컴파일 에러(또는 매핑 테스트 실패)로 드러난다.
 *
 * <p>⚠️ DB/랭킹용 {@code global.entity.PeriodType}(TWO_DAY, WEEKLY, MONTHLY)과 혼동하지 말 것.
 */
public enum FeedbackPeriod {
    ROUND,
    WEEKLY,
    MONTHLY
}
