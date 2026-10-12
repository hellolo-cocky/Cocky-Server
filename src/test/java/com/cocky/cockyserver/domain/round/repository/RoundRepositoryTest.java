package com.cocky.cockyserver.domain.round.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocky.cockyserver.domain.problem.entity.Difficulty;
import com.cocky.cockyserver.domain.problem.entity.Language;
import com.cocky.cockyserver.domain.problem.entity.Problem;
import com.cocky.cockyserver.domain.round.entity.Round;
import com.cocky.cockyserver.domain.topic.entity.Topic;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/** 전체 실패로 남은 "문제 0개 비활성 회차"가 마지막 회차 조회에서 제외되는지 실제 JPQL로 검증한다. */
@DataJpaTest
class RoundRepositoryTest {

    private static final LocalDate DAY1 = LocalDate.of(2026, 7, 7);
    private static final LocalDate DAY2 = LocalDate.of(2026, 7, 8);

    @Autowired
    private RoundRepository roundRepository;

    @Autowired
    private EntityManager entityManager;

    private Round persistRound(LocalDate date, int topicOrder, boolean withProblem) {
        Topic topic = new Topic("주제" + topicOrder, topicOrder);
        entityManager.persist(topic);
        Round round = new Round(topic, date, date.atStartOfDay(), date.atTime(23, 59, 59));
        if (withProblem) {
            round.activate();
        }
        entityManager.persist(round);
        if (withProblem) {
            entityManager.persist(new Problem(round, "제목", "내용", Language.PYTHON, Difficulty.EASY, true));
        }
        entityManager.flush();
        return round;
    }

    @Test
    void topRound_skipsNewerEmptyInactiveRound() {
        Round real = persistRound(DAY1, 3, true);
        persistRound(DAY2, 4, false); // 전체 실패로 남은 빈 회차 — 더 최근 날짜

        assertThat(roundRepository.findTopRoundWithProblems()).contains(real);
    }

    @Test
    void topRound_emptyWhenOnlyEmptyRoundsExist() {
        persistRound(DAY1, 3, false);

        assertThat(roundRepository.findTopRoundWithProblems()).isEmpty();
    }

    @Test
    void topClosedRound_skipsNewerEmptyInactiveRound() {
        Round real = persistRound(DAY1, 3, true);
        persistRound(DAY2, 4, false);
        LocalDateTime now = DAY2.plusDays(1).atStartOfDay(); // 두 회차 모두 마감된 시점

        assertThat(roundRepository.findTopClosedRoundWithProblems(now)).contains(real);
    }

    @Test
    void topClosedRound_picksLatestClosedAmongRoundsWithProblems() {
        persistRound(DAY1, 3, true);
        Round later = persistRound(DAY2, 4, true);
        LocalDateTime now = DAY2.plusDays(1).atStartOfDay();

        assertThat(roundRepository.findTopClosedRoundWithProblems(now)).contains(later);
        // DAY2가 아직 마감 전이면 DAY1이 대상
        assertThat(roundRepository.findTopClosedRoundWithProblems(DAY2.atTime(12, 0)))
                .map(Round::getRoundDate).contains(DAY1);
    }
}
