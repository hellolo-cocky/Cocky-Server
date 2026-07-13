package com.cocky.cockyserver.domain.ranking.service;

import com.cocky.cockyserver.domain.ranking.dto.RankingEntryResponse;
import com.cocky.cockyserver.domain.ranking.entity.ScopeType;
import com.cocky.cockyserver.domain.ranking.exception.RankingNotFoundException;
import com.cocky.cockyserver.domain.ranking.exception.UnsupportedRankingCombinationException;
import com.cocky.cockyserver.domain.ranking.repository.RankingSnapshotRepository;
import com.cocky.cockyserver.global.entity.PeriodType;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RankingQueryService {

    private static final Set<PeriodType> SUPPORTED_PERIOD_TYPES =
            Set.of(PeriodType.TWO_DAY, PeriodType.WEEKLY, PeriodType.MONTHLY);
    private static final ScopeType SUPPORTED_SCOPE_TYPE = ScopeType.SCHOOL;

    private final RankingSnapshotRepository rankingSnapshotRepository;

    /**
     * GRADE/CLASS_VS_CLASS/WITHIN_CLASS 등 아직 배치가 생성하지 않는 scope는 400(지원
     * 안 함)으로 구분한다. 지원 조합인데 아직 스냅샷이 없으면(첫 배치 전) 404(데이터 없음).
     */
    @Transactional(readOnly = true)
    public List<RankingEntryResponse> getRanking(PeriodType periodType, ScopeType scopeType) {
        if (!SUPPORTED_PERIOD_TYPES.contains(periodType) || scopeType != SUPPORTED_SCOPE_TYPE) {
            throw new UnsupportedRankingCombinationException(
                    "아직 지원하지 않는 조합입니다: period=" + periodType + ", scope=" + scopeType);
        }

        if (periodType == PeriodType.TWO_DAY) {
            Long latestRoundId = rankingSnapshotRepository.findLatestRoundId(periodType, scopeType)
                    .orElseThrow(() -> new RankingNotFoundException("아직 생성된 랭킹 스냅샷이 없습니다."));

            return rankingSnapshotRepository
                    .findAllByPeriodTypeAndScopeTypeAndRoundIdOrderByRankAsc(periodType, scopeType, latestRoundId)
                    .stream()
                    .map(RankingEntryResponse::from)
                    .toList();
        }

        // WEEKLY/MONTHLY는 round_id가 없으므로 period_key(문자열)로 최신 스냅샷을 찾는다.
        // period_key는 자릿수가 일정한 ISO 날짜 문자열이라 문자열 MAX가 곧 최신값이다
        // (round_id 문자열인 TWO_DAY에는 이 메서드를 쓰면 안 됨 — findLatestPeriodKey 주석 참고).
        String latestPeriodKey = rankingSnapshotRepository.findLatestPeriodKey(periodType, scopeType)
                .orElseThrow(() -> new RankingNotFoundException("아직 생성된 랭킹 스냅샷이 없습니다."));

        return rankingSnapshotRepository
                .findAllByPeriodTypeAndScopeTypeAndPeriodKeyOrderByRankAsc(periodType, scopeType, latestPeriodKey)
                .stream()
                .map(RankingEntryResponse::from)
                .toList();
    }
}
