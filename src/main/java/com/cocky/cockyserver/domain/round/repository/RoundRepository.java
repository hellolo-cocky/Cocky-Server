package com.cocky.cockyserver.domain.round.repository;

import com.cocky.cockyserver.domain.round.entity.Round;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * "마지막 회차" 류 조회는 전부 <b>문제가 1개 이상 있는 회차</b>만 대상으로 한다.
 * AI 생성이 전체 실패하면 문제 0개짜리 비활성 회차가 남는데(실패 로그 보관용),
 * 이를 마지막 회차로 치면 토픽 로테이션이 건너뛰어지고 랭킹/피드백이 빈 회차를 집계한다.
 * (is_active로 거르지 않는 이유: 회차 종료 시 deactivate()를 호출하는 곳이 없어 플래그가 종료 여부를 뜻하지 않는다.)
 */
public interface RoundRepository extends JpaRepository<Round, Long> {

    Optional<Round> findByActiveTrueAndOpenAtLessThanEqualAndCloseAtAfter(
            LocalDateTime openAtInclusive, LocalDateTime closeAtExclusive);

    Optional<Round> findByRoundDate(LocalDate roundDate);

    String HAS_PROBLEMS = "exists (select 1 from Problem p where p.round = r)";

    @Query("select r from Round r where " + HAS_PROBLEMS + " order by r.roundDate desc")
    List<Round> findRoundsWithProblemsOrderByRoundDateDesc(Pageable pageable);

    @Query("select r from Round r where r.closeAt <= :now and " + HAS_PROBLEMS + " order by r.closeAt desc")
    List<Round> findClosedRoundsWithProblemsOrderByCloseAtDesc(@Param("now") LocalDateTime now, Pageable pageable);

    /** 토픽 로테이션 기준 "마지막 회차" — 문제가 있는 회차 중 가장 최근 날짜. */
    default Optional<Round> findTopRoundWithProblems() {
        return findRoundsWithProblemsOrderByRoundDateDesc(PageRequest.of(0, 1)).stream().findFirst();
    }

    /** 랭킹 배치·기간 피드백이 대상으로 삼을 "가장 최근에 마감된" 회차(문제 있는 회차만). */
    default Optional<Round> findTopClosedRoundWithProblems(LocalDateTime now) {
        return findClosedRoundsWithProblemsOrderByCloseAtDesc(now, PageRequest.of(0, 1)).stream().findFirst();
    }
}
