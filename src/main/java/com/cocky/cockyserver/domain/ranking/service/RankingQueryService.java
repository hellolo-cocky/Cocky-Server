package com.cocky.cockyserver.domain.ranking.service;

import com.cocky.cockyserver.domain.ranking.dto.RankingEntryResponse;
import com.cocky.cockyserver.domain.ranking.entity.ScopeType;
import com.cocky.cockyserver.domain.ranking.exception.RankingNotFoundException;
import com.cocky.cockyserver.domain.ranking.exception.UnsupportedRankingCombinationException;
import com.cocky.cockyserver.domain.ranking.repository.RankingSnapshotRepository;
import com.cocky.cockyserver.global.entity.PeriodType;
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
     * 조합은 400(지원 안 함)으로 구분한다. TWO_DAY+SCHOOL인데 아직 스냅샷이 없으면
     * (첫 배치 전) 404(데이터 없음).
     */
    @Transactional(readOnly = true)
    public List<RankingEntryResponse> getRanking(PeriodType periodType, ScopeType scopeType) {
        if (periodType != SUPPORTED_PERIOD_TYPE || scopeType != SUPPORTED_SCOPE_TYPE) {
            throw new UnsupportedRankingCombinationException(
                    "아직 지원하지 않는 조합입니다: period=" + periodType + ", scope=" + scopeType);
        }

        Long latestRoundId = rankingSnapshotRepository.findLatestRoundId(periodType, scopeType)
                .orElseThrow(() -> new RankingNotFoundException("아직 생성된 랭킹 스냅샷이 없습니다."));

        return rankingSnapshotRepository
                .findAllByPeriodTypeAndScopeTypeAndRoundIdOrderByRankAsc(periodType, scopeType, latestRoundId)
                .stream()
                .map(RankingEntryResponse::from)
                .toList();
    }
}
