package com.cocky.cockyserver.domain.round.dto;

import java.time.LocalDate;

/**
 * 회차 자동 생성 트리거(스케줄러/관리자 API 공용) 결과. skipped=true면 라운드가 전혀
 * 만들어지지 않은 것이므로(일요일, 중복 라운드, 생성기 호출 자체 실패) reason으로 사유를 밝힌다.
 */
public record RoundGenerationResult(boolean skipped, String reason, LocalDate roundDate,
                                    int successCount, int failureCount) {

    public static RoundGenerationResult skipped(LocalDate roundDate, String reason) {
        return new RoundGenerationResult(true, reason, roundDate, 0, 0);
    }

    /** 전체 실패처럼 회차는 skip되지만(비활성 저장) 실패 건수는 알려야 하는 경우. */
    public static RoundGenerationResult skipped(LocalDate roundDate, String reason,
                                                int successCount, int failureCount) {
        return new RoundGenerationResult(true, reason, roundDate, successCount, failureCount);
    }

    public static RoundGenerationResult completed(LocalDate roundDate, int successCount, int failureCount) {
        return new RoundGenerationResult(false, null, roundDate, successCount, failureCount);
    }
}
