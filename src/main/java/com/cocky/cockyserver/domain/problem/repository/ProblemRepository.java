package com.cocky.cockyserver.domain.problem.repository;

import com.cocky.cockyserver.domain.problem.entity.Difficulty;
import com.cocky.cockyserver.domain.problem.entity.Language;
import com.cocky.cockyserver.domain.problem.entity.Problem;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemRepository extends JpaRepository<Problem, Long> {

    List<Problem> findByRoundIdOrderByIdAsc(Long roundId);

    /** 최근 문제 지문을 스케줄러가 pastStatements(중복 검사용)로 쓴다. */
    List<Problem> findTop20ByOrderByCreatedAtDesc();

    /** 공개된(openAt이 지난) 회차의 문제만 최신순으로 조회 — LATEST/POPULAR(폴백) 정렬용. */
    @Query("select p from Problem p where p.round.openAt <= :now "
            + "and (:language is null or p.language = :language) "
            + "and (:difficulty is null or p.difficulty = :difficulty) "
            + "and (:keyword is null or p.title like concat('%', :keyword, '%')) "
            + "order by p.createdAt desc")
    Page<Problem> searchOpenOrderByLatest(@Param("now") LocalDateTime now, @Param("language") Language language,
            @Param("difficulty") Difficulty difficulty, @Param("keyword") String keyword, Pageable pageable);

    /**
     * 공개된 회차의 문제를 난이도 순(EASY→NORMAL→HARD)으로 조회. difficulty는
     * EnumType.STRING으로 저장되어 알파벳순(EASY, HARD, NORMAL)과 ordinal 순서가 달라
     * CASE 식으로 순서값을 직접 매겨 정렬한다.
     */
    @Query("select p from Problem p where p.round.openAt <= :now "
            + "and (:language is null or p.language = :language) "
            + "and (:difficulty is null or p.difficulty = :difficulty) "
            + "and (:keyword is null or p.title like concat('%', :keyword, '%')) "
            + "order by case p.difficulty "
            + "when com.cocky.cockyserver.domain.problem.entity.Difficulty.EASY then 0 "
            + "when com.cocky.cockyserver.domain.problem.entity.Difficulty.NORMAL then 1 "
            + "else 2 end asc")
    Page<Problem> searchOpenOrderByDifficulty(@Param("now") LocalDateTime now, @Param("language") Language language,
            @Param("difficulty") Difficulty difficulty, @Param("keyword") String keyword, Pageable pageable);
}
