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

    boolean existsByPeriodTypeAndScopeTypeAndPeriodKey(
            PeriodType periodType, ScopeType scopeType, String periodKey);

    /**
     * WEEKLY/MONTHLY 조회 전용. period_key는 문자열 MAX라 TWO_DAY(round_id 문자열, 자릿수
     * 가변)에 쓰면 "9" > "10" 처럼 정렬이 어긋난다 — TWO_DAY는 반드시 findLatestRoundId를
     * 쓸 것.
     */
    @Query("select max(r.periodKey) from RankingSnapshot r "
            + "where r.periodType = :periodType and r.scopeType = :scopeType")
    Optional<String> findLatestPeriodKey(@Param("periodType") PeriodType periodType,
                                          @Param("scopeType") ScopeType scopeType);

    @Query("select r from RankingSnapshot r join fetch r.user "
            + "where r.periodType = :periodType and r.scopeType = :scopeType and r.periodKey = :periodKey "
            + "order by r.rank asc")
    List<RankingSnapshot> findAllByPeriodTypeAndScopeTypeAndPeriodKeyOrderByRankAsc(
            @Param("periodType") PeriodType periodType, @Param("scopeType") ScopeType scopeType,
            @Param("periodKey") String periodKey);
}
