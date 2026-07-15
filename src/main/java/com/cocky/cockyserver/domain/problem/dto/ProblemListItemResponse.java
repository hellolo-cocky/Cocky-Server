package com.cocky.cockyserver.domain.problem.dto;

import com.cocky.cockyserver.domain.problem.entity.Problem;
import java.util.List;

public record ProblemListItemResponse(
        Long problemId, String title, String language, String difficulty, List<String> tags) {

    public static ProblemListItemResponse from(Problem problem) {
        return new ProblemListItemResponse(problem.getId(), problem.getTitle(),
                problem.getLanguage().name(), problem.getDifficulty().name(), List.of());
    }
}
