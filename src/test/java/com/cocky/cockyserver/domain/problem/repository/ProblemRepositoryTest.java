package com.cocky.cockyserver.domain.problem.repository;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
class ProblemRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 15, 12, 0);

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private EntityManager entityManager;

    private Round persistRound(LocalDateTime openAt, LocalDateTime closeAt) {
        Topic topic = new Topic("배열", 1);
        entityManager.persist(topic);
        Round round = new Round(topic, LocalDate.of(2026, 7, 15), openAt, closeAt);
        entityManager.persist(round);
        return round;
    }

    private Problem persistProblem(Round round, String title, Language language, Difficulty difficulty) {
        Problem problem = new Problem(round, title, "내용", language, difficulty, false);
        entityManager.persist(problem);
        return problem;
    }

    @Test
    void excludesProblemsFromUnopenedRound() {
        Round openRound = persistRound(NOW.minusDays(1), NOW.plusDays(1));
        Round futureRound = persistRound(NOW.plusDays(1), NOW.plusDays(2));
        persistProblem(openRound, "공개된 문제", Language.PYTHON, Difficulty.EASY);
        persistProblem(futureRound, "미공개 문제", Language.PYTHON, Difficulty.EASY);

        Page<Problem> result = problemRepository.searchOpenOrderByLatest(
                NOW, null, null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("공개된 문제");
    }

    @Test
    void filtersByLanguageAndDifficulty() {
        Round round = persistRound(NOW.minusDays(1), NOW.plusDays(1));
        persistProblem(round, "파이썬 쉬움", Language.PYTHON, Difficulty.EASY);
        persistProblem(round, "자바 어려움", Language.JAVA, Difficulty.HARD);

        Page<Problem> result = problemRepository.searchOpenOrderByLatest(
                NOW, Language.JAVA, Difficulty.HARD, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("자바 어려움");
    }

    @Test
    void ordersByDifficultyEasyToHard() {
        Round round = persistRound(NOW.minusDays(1), NOW.plusDays(1));
        persistProblem(round, "하드", Language.PYTHON, Difficulty.HARD);
        persistProblem(round, "이지", Language.PYTHON, Difficulty.EASY);
        persistProblem(round, "노멀", Language.PYTHON, Difficulty.NORMAL);

        Page<Problem> result = problemRepository.searchOpenOrderByDifficulty(
                NOW, null, null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(Problem::getDifficulty)
                .containsExactly(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD);
    }
}
