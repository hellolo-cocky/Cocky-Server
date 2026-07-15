package com.cocky.cockyserver.domain.problem.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record ProblemPageResponse(List<ProblemListItemResponse> content, int page, int totalPages) {

    public static ProblemPageResponse from(Page<ProblemListItemResponse> page) {
        return new ProblemPageResponse(page.getContent(), page.getNumber(), page.getTotalPages());
    }
}
