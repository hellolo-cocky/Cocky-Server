package com.cocky.cockyserver.ai.port;

/**
 * 익명 닉네임 생성 최종 실패 계약 예외. {@link NicknameGenerator#generate} 호출 실패든, 중복
 * 회피 재시도(최대 5회) 소진이든 이 하나로 통일해서 던진다. 백엔드는 이 타입만 캐치하면 된다.
 *
 * <p>{@link PeriodFeedbackFailedException}과 같은 위치·패턴. 기본 닉네임으로의 폴백은 의도적으로
 * 만들지 않는다 — 실패는 실패로 노출한다(단계 3).
 *
 * <p>⚠️ 현재 실구현({@code NicknameService})은 OpenAI 호출 실패 시 내부에서 내장 예시로 폴백해
 * 예외를 던지지 않는다(재시도 없이 항상 성공 반환) — 이 포트가 "AI 호출 실패"로 이 예외를 던지는
 * 경로는 지금은 구조적으로만 존재하고 real 구현체에서는 발동하지 않는다. {@code NicknameService}는
 * 이번 작업 범위 밖이라 손대지 않았다(PR 리뷰 시 재논의 필요).
 */
public class NicknameGenerationFailedException extends RuntimeException {

    public NicknameGenerationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
