package com.cocky.cockyserver.ai.port;

/**
 * 기간 피드백 최종 실패 계약 예외. {@link PeriodFeedbackProvider#summarize} 호출이 실패하면
 * 던진다. 백엔드는 이 타입만 캐치하면 된다 — 내부 구현 예외(OpenAiException, JSON 파싱 실패로
 * 인한 IllegalStateException 등)는 port를 넘지 않는다.
 *
 * <p>{@link InstantFeedbackFailedException}과 같은 위치·패턴이지만, 재시도 루프는 없다(단계 2 —
 * periodic은 호출 빈도가 낮아 재시도 도입 여부는 별도 판단 필요, 지금은 보류). 단발 호출이
 * 실패하면 바로 이 예외로 감싸 던진다.
 */
public class PeriodFeedbackFailedException extends RuntimeException {

    public PeriodFeedbackFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
