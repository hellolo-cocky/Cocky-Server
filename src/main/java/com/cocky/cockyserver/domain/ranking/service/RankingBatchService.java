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
            snapshots.add(new RankingSnapshot(
                    user, closedRound, BATCH_PERIOD_TYPE, BATCH_SCOPE_TYPE, rank, totalScore, now));
        }

        rankingSnapshotRepository.saveAll(snapshots);
        return RankingSnapshotResult.completed(
                BATCH_PERIOD_TYPE, BATCH_SCOPE_TYPE, closedRound.getId(), now, snapshots.size());
    }
}
