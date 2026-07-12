package com.cocky.cockyserver.domain.ranking.service;

import com.cocky.cockyserver.domain.ranking.dto.RankingEntryResponse;
import com.cocky.cockyserver.domain.ranking.entity.ScopeType;
import com.cocky.cockyserver.domain.ranking.exception.RankingNotFoundException;
import com.cocky.cockyserver.domain.ranking.repository.RankingSnapshotRepository;
import com.cocky.cockyserver.global.entity.PeriodType;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RankingQueryService {

    private static final PeriodType SUPPORTED_PERIOD_TYPE = PeriodType.TWO_DAY;
    private static final ScopeType SUPPORTED_SCOPE_TYPE = ScopeType.SCHOOL;

    private final RankingSnapshotRepository rankingSnapshotRepository;

    /**
     * WEEKLY/MONTHLY, GRADE/CLASS_VS_CLASS/WITHIN_CLASS 등 아직 배치가 생성하지 않는
     * 조합은 빈 배열을 반환한다. TWO_DAY+SCHOOL인데 아직 스냅샷이 없으면(첫 배치 전) 404.
     */
    @Transactional(readOnly = true)
    public List<RankingEntryResponse> getRanking(PeriodType periodType, ScopeType scopeType) {
        if (periodType != SUPPORTED_PERIOD_TYPE || scopeType != SUPPORTED_SCOPE_TYPE) {
            return List.of();
        }

        LocalDateTime latest = rankingSnapshotRepository.findMaxCalculatedAt(periodType, scopeType)
                .orElseThrow(() -> new RankingNotFoundException("아직 생성된 랭킹 스냅샷이 없습니다."));

        return rankingSnapshotRepository
                .findAllByPeriodTypeAndScopeTypeAndCalculatedAtOrderByRankAsc(periodType, scopeType, latest)
                .stream()
                .map(RankingEntryResponse::from)
                .toList();
    }
}
