package com.cocky.cockyserver.domain.ranking.repository;

import com.cocky.cockyserver.domain.ranking.entity.RankingSnapshot;
import com.cocky.cockyserver.domain.ranking.entity.ScopeType;
import com.cocky.cockyserver.global.entity.PeriodType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RankingSnapshotRepository extends JpaRepository<RankingSnapshot, Long> {

    @Query("select max(r.round.id) from RankingSnapshot r "
            + "where r.periodType = :periodType and r.scopeType = :scopeType and r.round is not null")
    Optional<Long> findLatestRoundId(@Param("periodType") PeriodType periodType,
                                      @Param("scopeType") ScopeType scopeType);

    @Query("select r from RankingSnapshot r join fetch r.user "
            + "where r.periodType = :periodType and r.scopeType = :scopeType and r.round.id = :roundId "
            + "order by r.rank asc")
    List<RankingSnapshot> findAllByPeriodTypeAndScopeTypeAndRoundIdOrderByRankAsc(
            @Param("periodType") PeriodType periodType, @Param("scopeType") ScopeType scopeType,
            @Param("roundId") Long roundId);

    boolean existsByPeriodTypeAndScopeTypeAndRoundId(PeriodType periodType, ScopeType scopeType, Long roundId);
}
