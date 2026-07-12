package com.cocky.cockyserver.domain.ranking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cocky.cockyserver.domain.ranking.dto.RankingSnapshotResult;
import com.cocky.cockyserver.domain.ranking.entity.RankingSnapshot;
import com.cocky.cockyserver.domain.ranking.entity.ScopeType;
import com.cocky.cockyserver.domain.ranking.repository.RankingSnapshotRepository;
import com.cocky.cockyserver.domain.round.entity.Round;
import com.cocky.cockyserver.domain.round.repository.RoundRepository;
import com.cocky.cockyserver.domain.submission.repository.SubmissionRepository;
import com.cocky.cockyserver.domain.submission.repository.SubmissionRepository.UserScoreAggregate;
import com.cocky.cockyserver.domain.user.entity.Role;
import com.cocky.cockyserver.domain.user.entity.User;
import com.cocky.cockyserver.domain.user.repository.UserRepository;
import com.cocky.cockyserver.global.entity.PeriodType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RankingBatchServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 8, 0, 0);
    private static final Long CLOSED_ROUND_ID = 42L;

    @Mock
    private RankingSnapshotRepository rankingSnapshotRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoundRepository roundRepository;

    private RankingBatchService rankingBatchService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        rankingBatchService = new RankingBatchService(
                rankingSnapshotRepository, submissionRepository, userRepository, roundRepository, clock);
    }

    private User user(Long id, String name) {
        User user = new User(id, id + "@gsm.hs.kr", name, 2, 3, 1, "SW과", Role.STUDENT);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Round closedRound() {
        Round round = new Round(null, null, null, NOW.minusHours(1));
        ReflectionTestUtils.setField(round, "id", CLOSED_ROUND_ID);
        return round;
    }

    private UserScoreAggregate aggregate(Long userId, String score) {
        UserScoreAggregate aggregate = mock(UserScoreAggregate.class);
        when(aggregate.getUserId()).thenReturn(userId);
        when(aggregate.getTotalScore()).thenReturn(new BigDecimal(score));
        return aggregate;
    }

    @Test
    void noClosedRound_skipsWithoutQueryingSubmissions() {
        when(roundRepository.findTopByCloseAtLessThanEqualOrderByCloseAtDesc(NOW)).thenReturn(Optional.empty());

        RankingSnapshotResult result = rankingBatchService.generateSnapshot();

        assertTrue(result.skipped());
        assertEquals("NO_CLOSED_ROUND", result.reason());
        assertNull(result.roundId());
        verify(submissionRepository, never()).aggregateLatestScoreByUserForRound(any());
    }

    @Test
    void alreadyGeneratedForClosedRound_skipsWithoutAggregating() {
        when(roundRepository.findTopByCloseAtLessThanEqualOrderByCloseAtDesc(NOW))
                .thenReturn(Optional.of(closedRound()));
        when(rankingSnapshotRepository.existsByPeriodTypeAndScopeTypeAndRoundId(
                PeriodType.TWO_DAY, ScopeType.SCHOOL, CLOSED_ROUND_ID))
                .thenReturn(true);

        RankingSnapshotResult result = rankingBatchService.generateSnapshot();

        assertTrue(result.skipped());
        assertEquals("ALREADY_GENERATED", result.reason());
        assertEquals(CLOSED_ROUND_ID, result.roundId());
        verify(submissionRepository, never()).aggregateLatestScoreByUserForRound(any());
    }

    @Test
    void standardCompetitionRanking_tiesShareRankAndSkipNext() {
        // 100 / 80 / 80(동점) / 50 → 순위 1,2,2,4. 쿼리 계약상 이미 점수 내림차순 정렬돼 온다.
        when(roundRepository.findTopByCloseAtLessThanEqualOrderByCloseAtDesc(NOW))
                .thenReturn(Optional.of(closedRound()));
        when(rankingSnapshotRepository.existsByPeriodTypeAndScopeTypeAndRoundId(
                PeriodType.TWO_DAY, ScopeType.SCHOOL, CLOSED_ROUND_ID))
                .thenReturn(false);

        List<UserScoreAggregate> aggregates = List.of(
                aggregate(1L, "100.00"),
                aggregate(2L, "80.00"),
                aggregate(3L, "80.00"),
                aggregate(4L, "50.00"));
        when(submissionRepository.aggregateLatestScoreByUserForRound(CLOSED_ROUND_ID)).thenReturn(aggregates);
        when(userRepository.findAllById(any()))
                .thenReturn(List.of(user(1L, "유저1"), user(2L, "유저2"), user(3L, "유저3"), user(4L, "유저4")));

        RankingSnapshotResult result = rankingBatchService.generateSnapshot();

        assertTrue(!result.skipped());
        assertEquals(CLOSED_ROUND_ID, result.roundId());
        assertEquals(NOW, result.snapshotAt());
        assertEquals(4, result.generatedCount());

        ArgumentCaptor<List<RankingSnapshot>> captor = ArgumentCaptor.forClass(List.class);
        verify(rankingSnapshotRepository).saveAllAndFlush(captor.capture());
        List<RankingSnapshot> saved = captor.getValue();

        assertEquals(1, saved.get(0).getRank());
        assertEquals(2, saved.get(1).getRank());
        assertEquals(2, saved.get(2).getRank());
        assertEquals(4, saved.get(3).getRank());
        for (RankingSnapshot snapshot : saved) {
            assertEquals(PeriodType.TWO_DAY, snapshot.getPeriodType());
            assertEquals(ScopeType.SCHOOL, snapshot.getScopeType());
            assertEquals(CLOSED_ROUND_ID, snapshot.getRound().getId());
            assertEquals(NOW, snapshot.getCalculatedAt());
        }
    }

    @Test
    void noSubmissionsForClosedRound_returnsCompletedWithZeroRowsWithoutSaving() {
        when(roundRepository.findTopByCloseAtLessThanEqualOrderByCloseAtDesc(NOW))
                .thenReturn(Optional.of(closedRound()));
        when(rankingSnapshotRepository.existsByPeriodTypeAndScopeTypeAndRoundId(
                PeriodType.TWO_DAY, ScopeType.SCHOOL, CLOSED_ROUND_ID))
                .thenReturn(false);
        when(submissionRepository.aggregateLatestScoreByUserForRound(CLOSED_ROUND_ID)).thenReturn(List.of());

        RankingSnapshotResult result = rankingBatchService.generateSnapshot();

        assertTrue(!result.skipped());
        assertEquals(0, result.generatedCount());
        verify(rankingSnapshotRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void concurrentSaveViolatesUniqueConstraint_convertsToAlreadyGeneratedSkip() {
        // exists 체크는 통과했지만(경쟁상태로 다른 스레드가 먼저 커밋) 실제 저장 시점에
        // V10 유니크 제약(period_type, scope_type, round_id, user_id)에 걸리는 시나리오.
        when(roundRepository.findTopByCloseAtLessThanEqualOrderByCloseAtDesc(NOW))
                .thenReturn(Optional.of(closedRound()));
        when(rankingSnapshotRepository.existsByPeriodTypeAndScopeTypeAndRoundId(
                PeriodType.TWO_DAY, ScopeType.SCHOOL, CLOSED_ROUND_ID))
                .thenReturn(false);
        List<UserScoreAggregate> aggregates = List.of(aggregate(1L, "100.00"));
        when(submissionRepository.aggregateLatestScoreByUserForRound(CLOSED_ROUND_ID)).thenReturn(aggregates);
        when(userRepository.findAllById(any())).thenReturn(List.of(user(1L, "유저1")));
        when(rankingSnapshotRepository.saveAllAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uk_ranking_snapshot_period_scope_round_user"));

        RankingSnapshotResult result = rankingBatchService.generateSnapshot();

        assertTrue(result.skipped());
        assertEquals("ALREADY_GENERATED", result.reason());
        assertEquals(CLOSED_ROUND_ID, result.roundId());
    }

    @Test
    void missingUser_excludedFromSnapshotWithoutFailingBatch() {
        // 집계 시점과 유저 조회 시점 사이 탈퇴 등으로 userId=2가 사라진 경우.
        when(roundRepository.findTopByCloseAtLessThanEqualOrderByCloseAtDesc(NOW))
                .thenReturn(Optional.of(closedRound()));
        when(rankingSnapshotRepository.existsByPeriodTypeAndScopeTypeAndRoundId(
                PeriodType.TWO_DAY, ScopeType.SCHOOL, CLOSED_ROUND_ID))
                .thenReturn(false);
        List<UserScoreAggregate> aggregates = List.of(aggregate(1L, "100.00"), aggregate(2L, "80.00"));
        when(submissionRepository.aggregateLatestScoreByUserForRound(CLOSED_ROUND_ID)).thenReturn(aggregates);
        when(userRepository.findAllById(any())).thenReturn(List.of(user(1L, "유저1")));

        RankingSnapshotResult result = rankingBatchService.generateSnapshot();

        assertTrue(!result.skipped());
        assertEquals(1, result.generatedCount());

        ArgumentCaptor<List<RankingSnapshot>> captor = ArgumentCaptor.forClass(List.class);
        verify(rankingSnapshotRepository).saveAllAndFlush(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(1L, captor.getValue().get(0).getUser().getId());
    }
}
