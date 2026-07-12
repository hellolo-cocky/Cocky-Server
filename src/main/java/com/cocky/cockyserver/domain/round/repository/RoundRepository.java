package com.cocky.cockyserver.domain.round.repository;

import com.cocky.cockyserver.domain.round.entity.Round;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoundRepository extends JpaRepository<Round, Long> {

    Optional<Round> findByActiveTrueAndOpenAtLessThanEqualAndCloseAtAfter(
            LocalDateTime openAtInclusive, LocalDateTime closeAtExclusive);

    Optional<Round> findByRoundDate(LocalDate roundDate);

    Optional<Round> findTopByOrderByRoundDateDesc();

    /** 랭킹 배치가 집계 대상으로 삼을 "가장 최근에 마감된" 라운드. */
    Optional<Round> findTopByCloseAtLessThanEqualOrderByCloseAtDesc(LocalDateTime now);
}
