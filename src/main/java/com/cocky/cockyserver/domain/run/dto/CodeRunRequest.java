package com.cocky.cockyserver.domain.run.dto;

import com.cocky.cockyserver.domain.problem.entity.Language;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** stdin은 선택(null이면 빈 입력). code/stdin 크기 제한(64KB)은 {@code RunService}에서 바이트 기준으로 검증한다. */
public record CodeRunRequest(
        @NotNull(message = "language는 필수입니다.") Language language,
        @NotBlank(message = "code는 필수입니다.") String code,
        String stdin
) {
}
