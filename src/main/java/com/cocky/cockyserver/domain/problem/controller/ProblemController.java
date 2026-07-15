package com.cocky.cockyserver.domain.problem.controller;

import com.cocky.cockyserver.domain.problem.dto.ProblemDetailResponse;
import com.cocky.cockyserver.domain.problem.dto.ProblemPageResponse;
import com.cocky.cockyserver.domain.problem.dto.ProblemSort;
import com.cocky.cockyserver.domain.problem.entity.Difficulty;
import com.cocky.cockyserver.domain.problem.entity.Language;
import com.cocky.cockyserver.domain.problem.service.ProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ProblemController {

    private final ProblemService problemService;

    @GetMapping("/api/v1/problems")
    public ResponseEntity<ProblemPageResponse> getProblems(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Language language,
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "LATEST") ProblemSort sort) {
        return ResponseEntity.ok(problemService.getProblems(language, difficulty, keyword, sort, page, size));
    }

    @GetMapping("/api/v1/problems/{problemId}")
    public ResponseEntity<ProblemDetailResponse> getProblemDetail(@PathVariable Long problemId) {
        return ResponseEntity.ok(problemService.getProblemDetail(problemId));
    }
}
