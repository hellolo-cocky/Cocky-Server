package com.cocky.cockyserver.domain.ranking.service;

import com.cocky.cockyserver.domain.ranking.dto.RankingSnapshotResult;
import com.cocky.cockyserver.domain.ranking.entity.RankingSnapshot;
import com.cocky.cockyserver.domain.ranking.entity.ScopeType;
import com.cocky.cockyserver.domain.ranking.repository.RankingSnapshotRepository;
import com.cocky.cockyserver.domain.round.entity.Round;
import com.cocky.cockyserver.domain.round.repository.RoundRepository;
import com.cocky.cockyserver.domain.submission.repository.SubmissionRepository;
import com.cocky.cockyserver.domain.submission.repository.SubmissionRepository.UserScoreAggregate;
import com.cocky.cockyserver.domain.user.entity.User;
import com.cocky.cockyserver.domain.user.repository.UserRepository;
import com.cocky.cockyserver.global.entity.PeriodType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전교(SCHOOL) 스코프 랭킹 스냅샷을 세 가지 주기로 생성한다.
 *
 * <ul>
 *   <li>TWO_DAY: 회차 마감(화/목/토 23:59) 다음날 00:00에 직전 마감 라운드(9문제) 점수만 집계
 *   <li>WEEKLY: 일요일 00:00에 그 주(월~토) 라운드들 점수 합산 집계
 *   <li>MONTHLY: 매월 1일 00:00에 지난달 라운드들 점수 합산 집계
 * </ul>
 *
 * <p>GRADE/CLASS 등 다른 scope_type 배치는 별도 과제로 남겨둔다.
 */
@Service
@RequiredArgsConstructor
public class RankingBatchService {

    private static final Logger log = LoggerFactory.getLogger(RankingBatchService.class);

    private static final ScopeType BATCH_SCOPE_TYPE = ScopeType.SCHOOL;

    private final RankingSnapshotRepository rankingSnapshotRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final RoundRepository roundRepository;
    private final Clock clock;

    @Transactional
    public RankingSnapshotResult generateTwoDaySnapshot() {
        LocalDateTime now = LocalDateTime.now(clock);
        Round closedRound = roundRepository.findTopByCloseAtLessThanEqualOrderByCloseAtDesc(now).orElse(null);
        if (closedRound == null) {
            return RankingSnapshotResult.skipped(PeriodType.TWO_DAY, BATCH_SCOPE_TYPE, null, "NO_CLOSED_ROUND");
        }

        if (rankingSnapshotRepository.existsByPeriodTypeAndScopeTypeAndRoundId(
                PeriodType.TWO_DAY, BATCH_SCOPE_TYPE, closedRound.getId())) {
            return RankingSnapshotResult.skipped(
                    PeriodType.TWO_DAY, BATCH_SCOPE_TYPE, closedRound.getId(), "ALREADY_GENERATED");
        }

        List<UserScoreAggregate> aggregates =
                submissionRepository.aggregateLatestScoreByUserForRound(closedRound.getId());
        String periodKey = String.valueOf(closedRound.getId());

        return buildAndSaveSnapshot(
                PeriodType.TWO_DAY, BATCH_SCOPE_TYPE, closedRound, null, null, periodKey, aggregates, now);
    }

    @Transactional
    public RankingSnapshotResult generateWeeklySnapshot() {
        LocalDateTime now = LocalDateTime.now(clock);
        // 트리거가 일요일 00:00이므로 지난 월요일~토요일이 대상 기간이다.
        LocalDate weekMonday = now.toLocalDate().minusDays(6);
        LocalDate weekSaturday = now.toLocalDate().minusDays(1);
        String periodKey = weekMonday.toString();

        if (rankingSnapshotRepository.existsByPeriodTypeAndScopeTypeAndPeriodKey(
                PeriodType.WEEKLY, BATCH_SCOPE_TYPE, periodKey)) {
            return RankingSnapshotResult.skippedForPeriod(
                    PeriodType.WEEKLY, BATCH_SCOPE_TYPE, weekMonday, weekSaturday, "ALREADY_GENERATED");
        }

        List<UserScoreAggregate> aggregates =
                submissionRepository.aggregateLatestScoreByUserForPeriod(weekMonday, weekSaturday);

        return buildAndSaveSnapshot(
                PeriodType.WEEKLY, BATCH_SCOPE_TYPE, null, weekMonday, weekSaturday, periodKey, aggregates, now);
    }

    @Transactional
    public RankingSnapshotResult generateMonthlySnapshot() {
        LocalDateTime now = LocalDateTime.now(clock);
        // 트리거가 매월 1일 00:00이므로 지난달 1일~말일이 대상 기간이다.
        LocalDate lastMonthFirstDay = now.toLocalDate().minusMonths(1).withDayOfMonth(1);
        LocalDate lastMonthLastDay = now.toLocalDate().withDayOfMonth(1).minusDays(1);
        String periodKey = lastMonthFirstDay.toString();

        if (rankingSnapshotRepository.existsByPeriodTypeAndScopeTypeAndPeriodKey(
                PeriodType.MONTHLY, BATCH_SCOPE_TYPE, periodKey)) {
            return RankingSnapshotResult.skippedForPeriod(
                    PeriodType.MONTHLY, BATCH_SCOPE_TYPE, lastMonthFirstDay, lastMonthLastDay, "ALREADY_GENERATED");
        }

        List<UserScoreAggregate> aggregates =
                submissionRepository.aggregateLatestScoreByUserForPeriod(lastMonthFirstDay, lastMonthLastDay);

        return buildAndSaveSnapshot(PeriodType.MONTHLY, BATCH_SCOPE_TYPE, null, lastMonthFirstDay, lastMonthLastDay,
                periodKey, aggregates, now);
    }

    /**
     * 표준 경쟁 랭킹(1,2,2,4) 계산 + 저장 공통 로직. round가 null이면 WEEKLY/MONTHLY(기간
     * 기반) 결과로, round가 있으면 TWO_DAY(라운드 기반) 결과로 응답을 만든다.
     *
     * <p>주의: aggregates는 Repository 쿼리가 이미 (score DESC, userId ASC)로 정렬해서
     * 반환한다고 가정하고 아래에서 순서대로 랭킹을 매긴다. Repository 쿼리 수정 시 이 가정이
     * 깨지지 않는지 반드시 확인할 것.
     */
    private RankingSnapshotResult buildAndSaveSnapshot(PeriodType periodType, ScopeType scopeType, Round round,
                                                         LocalDate periodStart, LocalDate periodEnd,
                                                         String periodKey, List<UserScoreAggregate> aggregates,
                                                         LocalDateTime now) {
        Long roundId = round != null ? round.getId() : null;

        if (aggregates.isEmpty()) {
            return roundId != null
                    ? RankingSnapshotResult.completed(periodType, scopeType, roundId, now, 0)
                    : RankingSnapshotResult.completedForPeriod(periodType, scopeType, periodStart, periodEnd, now, 0);
        }

        Map<Long, User> usersById = userRepository
                .findAllById(aggregates.stream().map(UserScoreAggregate::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<RankingSnapshot> snapshots = new ArrayList<>(aggregates.size());
        int rank = 0;
        BigDecimal previousScore = null;
        for (int i = 0; i < aggregates.size(); i++) {
            UserScoreAggregate aggregate = aggregates.get(i);
            BigDecimal totalScore = aggregate.getTotalScore();
            if (previousScore == null || totalScore.compareTo(previousScore) != 0) {
                rank = i + 1;
            }
            previousScore = totalScore;

            User user = usersById.get(aggregate.getUserId());
            if (user == null) {
                // 집계 시점과 유저 조회 시점 사이 탈퇴 등으로 유저가 사라진 엣지케이스.
                // 배치 전체를 실패시키지 않고 해당 유저만 스냅샷에서 제외한다.
                log.warn("랭킹 배치: userId={} 를 찾을 수 없어 스냅샷에서 제외 (periodType={}, periodKey={})",
                        aggregate.getUserId(), periodType, periodKey);
                continue;
            }
            snapshots.add(new RankingSnapshot(
                    user, round, periodType, scopeType, periodStart, periodEnd, periodKey, rank, totalScore, now));
        }

        try {
            rankingSnapshotRepository.saveAllAndFlush(snapshots);
        } catch (DataIntegrityViolationException e) {
            // exists 체크와 실제 저장 사이의 race condition(스케줄러 중복 실행, admin 동시
            // 트리거)에 대한 최종 방어선 — DB unique 제약(V11) 위반을 감지해 skip으로 흡수한다.
            log.info("랭킹 배치: periodType={}, periodKey={} 스냅샷 저장 중 유니크 제약 위반 — 동시 실행으로 이미 생성됨",
                    periodType, periodKey);
            return roundId != null
                    ? RankingSnapshotResult.skipped(periodType, scopeType, roundId, "ALREADY_GENERATED")
                    : RankingSnapshotResult.skippedForPeriod(
                            periodType, scopeType, periodStart, periodEnd, "ALREADY_GENERATED");
        }

        return roundId != null
                ? RankingSnapshotResult.completed(periodType, scopeType, roundId, now, snapshots.size())
                : RankingSnapshotResult.completedForPeriod(
                        periodType, scopeType, periodStart, periodEnd, now, snapshots.size());
    }
}
