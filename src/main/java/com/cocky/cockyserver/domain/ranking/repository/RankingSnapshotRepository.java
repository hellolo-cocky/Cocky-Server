package com.cocky.cockyserver.domain.ranking.repository;

import com.cocky.cockyserver.domain.ranking.entity.RankingSnapshot;
import com.cocky.cockyserver.domain.ranking.entity.ScopeType;
import com.cocky.cockyserver.global.entity.PeriodType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RankingSnapshotRepository extends JpaRepository<RankingSnapshot, Long> {

    @Query("select max(r.calculatedAt) from RankingSnapshot r "
            + "where r.periodType = :periodType and r.scopeType = :scopeType")
    Optional<LocalDateTime> findMaxCalculatedAt(@Param("periodType") PeriodType periodType,
                                                 @Param("scopeType") ScopeType scopeType);

    List<RankingSnapshot> findAllByPeriodTypeAndScopeTypeAndCalculatedAtOrderByRankAsc(
            PeriodType periodType, ScopeType scopeType, LocalDateTime calculatedAt);

    boolean existsByPeriodTypeAndScopeTypeAndRoundId(PeriodType periodType, ScopeType scopeType, Long roundId);
}
