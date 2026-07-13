package com.cocky.cockyserver.domain.ranking.dto;

import com.cocky.cockyserver.domain.ranking.entity.ScopeType;
import com.cocky.cockyserver.global.entity.PeriodType;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 랭킹 배치 트리거(스케줄러/관리자 API 공용) 결과. skipped=true면 스냅샷이 생성되지
 * 않은 것이므로(직전 마감 라운드 없음, 이미 생성됨) reason으로 사유를 밝힌다.
 *
 * <p>TWO_DAY는 roundId로, WEEKLY/MONTHLY는 periodStart/periodEnd로 대상 기간을 나타낸다
 * (서로 배타적 — 한쪽이 채워지면 다른 쪽은 null).
 */
public record RankingSnapshotResult(boolean skipped, String reason, PeriodType periodType, ScopeType scopeType,
                                     Long roundId, LocalDate periodStart, LocalDate periodEnd,
                                     LocalDateTime snapshotAt, int generatedCount) {

    public static RankingSnapshotResult skipped(PeriodType periodType, ScopeType scopeType, Long roundId,
                                                 String reason) {
        return new RankingSnapshotResult(true, reason, periodType, scopeType, roundId, null, null, null, 0);
    }

    public static RankingSnapshotResult skippedForPeriod(PeriodType periodType, ScopeType scopeType,
                                                           LocalDate periodStart, LocalDate periodEnd,
                                                           String reason) {
        return new RankingSnapshotResult(
                true, reason, periodType, scopeType, null, periodStart, periodEnd, null, 0);
    }

    public static RankingSnapshotResult completed(PeriodType periodType, ScopeType scopeType, Long roundId,
                                                   LocalDateTime snapshotAt, int generatedCount) {
        return new RankingSnapshotResult(
                false, null, periodType, scopeType, roundId, null, null, snapshotAt, generatedCount);
    }

    public static RankingSnapshotResult completedForPeriod(PeriodType periodType, ScopeType scopeType,
                                                             LocalDate periodStart, LocalDate periodEnd,
                                                             LocalDateTime snapshotAt, int generatedCount) {
        return new RankingSnapshotResult(
                false, null, periodType, scopeType, null, periodStart, periodEnd, snapshotAt, generatedCount);
    }
}
