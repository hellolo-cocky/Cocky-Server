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
 * 회차 마감(화/목/토 23:59) 다음날 00:00에 직전 마감 라운드(9문제) 점수만으로 전체 유저
 * 랭킹 스냅샷을 생성한다.
 *
 * <p>이번 배치는 전교(SCHOOL) 스코프, 2일 주기(TWO_DAY)만 다룬다. GRADE/CLASS 등 다른
 * scope_type, WEEKLY/MONTHLY 등 다른 period_type 배치는 별도 과제로 남겨둔다.
 */
@Service
@RequiredArgsConstructor
public class RankingBatchService {

    private static final Logger log = LoggerFactory.getLogger(RankingBatchService.class);

    private static final PeriodType BATCH_PERIOD_TYPE = PeriodType.TWO_DAY;
    private static final ScopeType BATCH_SCOPE_TYPE = ScopeType.SCHOOL;

    private final RankingSnapshotRepository rankingSnapshotRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final RoundRepository roundRepository;
    private final Clock clock;

    @Transactional
    public RankingSnapshotResult generateSnapshot() {
        LocalDateTime now = LocalDateTime.now(clock);
        Round closedRound = roundRepository.findTopByCloseAtLessThanEqualOrderByCloseAtDesc(now).orElse(null);
        if (closedRound == null) {
            return RankingSnapshotResult.skipped(BATCH_PERIOD_TYPE, BATCH_SCOPE_TYPE, null, "NO_CLOSED_ROUND");
        }

        if (rankingSnapshotRepository.existsByPeriodTypeAndScopeTypeAndRoundId(
                BATCH_PERIOD_TYPE, BATCH_SCOPE_TYPE, closedRound.getId())) {
            return RankingSnapshotResult.skipped(
                    BATCH_PERIOD_TYPE, BATCH_SCOPE_TYPE, closedRound.getId(), "ALREADY_GENERATED");
        }

        // 주의: aggregates는 Repository 쿼리가 이미 (score DESC, userId ASC)로 정렬해서
        // 반환한다고 가정하고 아래에서 순서대로 표준 경쟁 랭킹(1,2,2,4)을 매긴다.
        // Repository 쿼리 수정 시 이 가정이 깨지지 않는지 반드시 확인할 것.
        List<UserScoreAggregate> aggregates =
                submissionRepository.aggregateLatestScoreByUserForRound(closedRound.getId());
        if (aggregates.isEmpty()) {
            return RankingSnapshotResult.completed(BATCH_PERIOD_TYPE, BATCH_SCOPE_TYPE, closedRound.getId(), now, 0);
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
                log.warn("랭킹 배치: userId={} 를 찾을 수 없어 스냅샷에서 제외 (round={})",
                        aggregate.getUserId(), closedRound.getId());
                continue;
            }
            snapshots.add(new RankingSnapshot(
                    user, closedRound, BATCH_PERIOD_TYPE, BATCH_SCOPE_TYPE, rank, totalScore, now));
        }

        try {
            rankingSnapshotRepository.saveAllAndFlush(snapshots);
        } catch (DataIntegrityViolationException e) {
            // exists 체크와 실제 저장 사이의 race condition(스케줄러 중복 실행, admin 동시
            // 트리거)에 대한 최종 방어선 — DB unique 제약(V10) 위반을 감지해 skip으로 흡수한다.
            log.info("랭킹 배치: round={} 스냅샷 저장 중 유니크 제약 위반 — 동시 실행으로 이미 생성됨",
                    closedRound.getId());
            return RankingSnapshotResult.skipped(
                    BATCH_PERIOD_TYPE, BATCH_SCOPE_TYPE, closedRound.getId(), "ALREADY_GENERATED");
        }

        return RankingSnapshotResult.completed(
                BATCH_PERIOD_TYPE, BATCH_SCOPE_TYPE, closedRound.getId(), now, snapshots.size());
    }
}
