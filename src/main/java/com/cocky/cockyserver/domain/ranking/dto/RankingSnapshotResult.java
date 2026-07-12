package com.cocky.cockyserver.domain.ranking.dto;

import com.cocky.cockyserver.domain.ranking.entity.ScopeType;
import com.cocky.cockyserver.global.entity.PeriodType;
import java.time.LocalDateTime;

/**
 * 랭킹 배치 트리거(스케줄러/관리자 API 공용) 결과. skipped=true면 스냅샷이 생성되지
 * 않은 것이므로(직전 마감 라운드 없음, 이미 생성됨) reason으로 사유를 밝힌다.
 */
public record RankingSnapshotResult(boolean skipped, String reason, PeriodType periodType, ScopeType scopeType,
                                     Long roundId, LocalDateTime snapshotAt, int generatedCount) {

    public static RankingSnapshotResult skipped(PeriodType periodType, ScopeType scopeType, Long roundId,
                                                 String reason) {
        return new RankingSnapshotResult(true, reason, periodType, scopeType, roundId, null, 0);
    }

    public static RankingSnapshotResult completed(PeriodType periodType, ScopeType scopeType, Long roundId,
                                                   LocalDateTime snapshotAt, int generatedCount) {
        return new RankingSnapshotResult(false, null, periodType, scopeType, roundId, snapshotAt, generatedCount);
    }
}
