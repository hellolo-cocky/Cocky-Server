package com.cocky.cockyserver.domain.problem.service;

import com.cocky.cockyserver.domain.problem.dto.ProblemDetailResponse;
import com.cocky.cockyserver.domain.problem.dto.ProblemListItemResponse;
import com.cocky.cockyserver.domain.problem.dto.ProblemPageResponse;
import com.cocky.cockyserver.domain.problem.dto.ProblemSort;
import com.cocky.cockyserver.domain.problem.dto.ProblemSummaryResponse;
import com.cocky.cockyserver.domain.problem.dto.RoundProblemsResponse;
import com.cocky.cockyserver.domain.problem.entity.Difficulty;
import com.cocky.cockyserver.domain.problem.entity.Language;
import com.cocky.cockyserver.domain.problem.entity.Problem;
import com.cocky.cockyserver.domain.problem.exception.ProblemNotFoundException;
import com.cocky.cockyserver.domain.problem.repository.ProblemRepository;
import com.cocky.cockyserver.domain.round.entity.Round;
import com.cocky.cockyserver.domain.round.service.RoundService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProblemService {

    private final ProblemRepository problemRepository;
    private final RoundService roundService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public RoundProblemsResponse getCurrentRoundProblems() {
        Round round = roundService.getCurrentActiveRound();
        List<ProblemSummaryResponse> problems = problemRepository.findByRoundIdOrderByIdAsc(round.getId()).stream()
                .map(ProblemSummaryResponse::from)
                .toList();
        return new RoundProblemsResponse(round.getId(), problems);
    }

    @Transactional(readOnly = true)
    public ProblemDetailResponse getProblemDetail(Long problemId) {
        Problem problem = problemRepository
                .findById(problemId)
                .orElseThrow(() -> new ProblemNotFoundException("존재하지 않는 문제입니다. problemId=" + problemId));
        return ProblemDetailResponse.from(problem);
    }

    /** POPULAR는 인기 집계 데이터가 아직 없어 LATEST와 동일하게 최신순으로 폴백한다. */
    @Transactional(readOnly = true)
    public ProblemPageResponse getProblems(Language language, Difficulty difficulty, String keyword,
                                            ProblemSort sort, int page, int size) {
        LocalDateTime now = LocalDateTime.now(clock);
        Pageable pageable = PageRequest.of(page, size);
        Page<Problem> problems = sort == ProblemSort.DIFFICULTY
                ? problemRepository.searchOpenOrderByDifficulty(now, language, difficulty, keyword, pageable)
                : problemRepository.searchOpenOrderByLatest(now, language, difficulty, keyword, pageable);
        return ProblemPageResponse.from(problems.map(ProblemListItemResponse::from));
    }
}
