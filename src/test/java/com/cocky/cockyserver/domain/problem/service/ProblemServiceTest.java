package com.cocky.cockyserver.domain.problem.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cocky.cockyserver.domain.problem.dto.ProblemDetailResponse;
import com.cocky.cockyserver.domain.problem.dto.ProblemPageResponse;
import com.cocky.cockyserver.domain.problem.dto.ProblemSort;
import com.cocky.cockyserver.domain.problem.dto.RoundProblemsResponse;
import com.cocky.cockyserver.domain.problem.entity.Difficulty;
import com.cocky.cockyserver.domain.problem.entity.Language;
import com.cocky.cockyserver.domain.problem.entity.Problem;
import com.cocky.cockyserver.domain.problem.repository.ProblemRepository;
import com.cocky.cockyserver.domain.round.entity.Round;
import com.cocky.cockyserver.domain.round.service.RoundService;
import com.cocky.cockyserver.domain.topic.entity.Topic;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ProblemServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 15, 12, 0);

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private RoundService roundService;

    private final Clock clock = Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    private Round round() {
        return new Round(new Topic("배열", 1),
                LocalDate.of(2026, 7, 8), LocalDateTime.of(2026, 7, 8, 0, 0), LocalDateTime.of(2026, 7, 9, 0, 0));
    }

    private Problem problem(Round round) {
        return new Problem(round, "두 수의 합", "두 정수를 더해 출력하시오.", Language.PYTHON, Difficulty.HARD, false);
    }

    @Test
    void currentRoundProblemsIncludeDifficulty() {
        ProblemService service = new ProblemService(problemRepository, roundService, clock);
        Round round = round();
        Problem problem = problem(round);
        when(roundService.getCurrentActiveRound()).thenReturn(round);
        when(problemRepository.findByRoundIdOrderByIdAsc(round.getId())).thenReturn(List.of(problem));

        RoundProblemsResponse response = service.getCurrentRoundProblems();

        assertEquals(Difficulty.HARD, response.problems().get(0).difficulty());
    }

    @Test
    void problemDetailIncludesDifficulty() {
        ProblemService service = new ProblemService(problemRepository, roundService, clock);
        Problem problem = problem(round());
        when(problemRepository.findById(anyLong())).thenReturn(Optional.of(problem));

        ProblemDetailResponse response = service.getProblemDetail(1L);

        assertEquals(Difficulty.HARD, response.difficulty());
    }

    @Test
    void popularSortFallsBackToLatest() {
        ProblemService service = new ProblemService(problemRepository, roundService, clock);
        Problem problem = problem(round());
        when(problemRepository.searchOpenOrderByLatest(eq(NOW), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(problem)));

        ProblemPageResponse response = service.getProblems(null, null, null, ProblemSort.POPULAR, 0, 20);

        assertEquals(1, response.content().size());
        verify(problemRepository, never())
                .searchOpenOrderByDifficulty(any(), any(), any(), any(), any());
    }

    @Test
    void difficultySortRoutesToDifficultyQuery() {
        ProblemService service = new ProblemService(problemRepository, roundService, clock);
        Problem problem = problem(round());
        Page<Problem> page = new PageImpl<>(List.of(problem));
        when(problemRepository.searchOpenOrderByDifficulty(
                eq(NOW), eq(Language.PYTHON), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page);

        ProblemPageResponse response = service.getProblems(Language.PYTHON, null, null, ProblemSort.DIFFICULTY, 0, 20);

        assertEquals(1, response.content().size());
        verify(problemRepository, never())
                .searchOpenOrderByLatest(any(), any(), any(), any(), any());
    }
}
