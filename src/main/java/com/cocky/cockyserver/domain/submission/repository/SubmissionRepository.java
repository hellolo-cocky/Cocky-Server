package com.cocky.cockyserver.domain.submission.repository;

import com.cocky.cockyserver.domain.problem.entity.Difficulty;
import com.cocky.cockyserver.domain.problem.entity.Language;
import com.cocky.cockyserver.domain.submission.entity.Submission;
import com.cocky.cockyserver.domain.submission.entity.Verdict;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    Page<Submission> findByUserId(Long userId, Pageable pageable);

    /**
     * 같은 (user, problem)의 기존 is_latest=true 제출을 전부 false로 뒤집는다. 조회 후
     * forEach로 하나씩 바꾸는 대신 벌크 UPDATE 한 방으로 처리해 읽기-쓰기 갭의 경쟁상태를
     * 줄인다. 완전한 방어(동시 제출 직렬화)는 DB unique index로 별도 처리할 예정(백로그).
     */
    @Modifying
    @Query("update Submission s set s.latest = false "
            + "where s.user.id = :userId and s.problem.id = :problemId and s.latest = true")
    int markPreviousNotLatest(@Param("userId") Long userId, @Param("problemId") Long problemId);

    /**
     * 유저별 "해당 라운드 문제당 마지막 제출"(is_latest=true)의 score 합산. 랭킹 배치가
     * 직전 마감 라운드(9문제) 점수만 집계할 때 사용한다 — 과거 재제출은 is_latest=false로
     * 이미 걸러져 있으므로 별도 MAX(id) 서브쿼리가 필요 없다. 동점자 처리를 위해 점수
     * 내림차순 정렬 시 유저 id 오름차순으로 tie-break한다.
     */
    @Query("select s.user.id as userId, sum(s.score) as totalScore "
            + "from Submission s where s.latest = true and s.problem.round.id = :roundId "
            + "group by s.user.id "
            + "order by sum(s.score) desc, s.user.id asc")
    List<UserScoreAggregate> aggregateLatestScoreByUserForRound(@Param("roundId") Long roundId);

    /**
     * 유저별 "기간 내(round_date 기준) 마지막 제출"(is_latest=true)의 score 합산. WEEKLY/
     * MONTHLY 랭킹 배치가 여러 라운드에 걸친 점수를 한번에 집계할 때 사용한다.
     */
    @Query("select s.user.id as userId, sum(s.score) as totalScore "
            + "from Submission s where s.latest = true "
            + "and s.problem.round.roundDate between :start and :end "
            + "group by s.user.id "
            + "order by sum(s.score) desc, s.user.id asc")
    List<UserScoreAggregate> aggregateLatestScoreByUserForPeriod(
            @Param("start") LocalDate start, @Param("end") LocalDate end);

    interface UserScoreAggregate {
        Long getUserId();
        BigDecimal getTotalScore();
    }

    /**
     * 기간 피드백({@code PeriodStats}) 집계 쿼리 3종. 전부 제출 전체(AC 여부 무관) 기준이며
     * verdict별 오답 집계만 AC를 제외한다 — 세 쿼리를 하나로 합치지 않고 목적별로 분리했다
     * (데모 규모라 가독성이 우선, Object[] 대신 인터페이스 projection으로 매핑).
     */
    @Query("select s.language as language, count(s) as count "
            + "from Submission s "
            + "where s.user.id = :userId and s.submittedAt >= :start and s.submittedAt < :end "
            + "group by s.language")
    List<LanguageCount> aggregateLanguageCountsByUserAndPeriod(
            @Param("userId") Long userId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("select s.problem.difficulty as difficulty, count(s) as count "
            + "from Submission s "
            + "where s.user.id = :userId and s.submittedAt >= :start and s.submittedAt < :end "
            + "group by s.problem.difficulty")
    List<DifficultyCount> aggregateDifficultyCountsByUserAndPeriod(
            @Param("userId") Long userId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * 오답 유형(verdict) 집계 — AC는 정답이라 "틀린 유형"이 아니고, PENDING은 아직 채점이
     * 끝나지 않은 상태라 오답으로 볼 수 없으므로 둘 다 제외한다.
     */
    @Query("select s.verdict as verdict, count(s) as count "
            + "from Submission s "
            + "where s.user.id = :userId and s.submittedAt >= :start and s.submittedAt < :end "
            + "and s.verdict not in ("
            + "com.cocky.cockyserver.domain.submission.entity.Verdict.AC, "
            + "com.cocky.cockyserver.domain.submission.entity.Verdict.PENDING) "
            + "group by s.verdict")
    List<VerdictCount> aggregateWrongVerdictCountsByUserAndPeriod(
            @Param("userId") Long userId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    interface LanguageCount {
        Language getLanguage();
        Long getCount();
    }

    interface DifficultyCount {
        Difficulty getDifficulty();
        Long getCount();
    }

    interface VerdictCount {
        Verdict getVerdict();
        Long getCount();
    }
}
