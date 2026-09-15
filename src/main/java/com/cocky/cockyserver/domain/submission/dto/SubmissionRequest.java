package com.cocky.cockyserver.domain.submission.dto;

import com.cocky.cockyserver.domain.problem.entity.Language;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * isAnonymous가 null이면 서비스 계층이 제출 시점 user.isAnonymousDefault() 값을 그대로
 * 적용하고, null이 아니면 이 값이 사용자 기본값을 덮어쓴다({@code SubmissionService} 참고).
 */
public record SubmissionRequest(
        @NotNull(message = "problemId는 필수입니다.") Long problemId,
        @NotNull(message = "language는 필수입니다.") Language language,
        @NotBlank(message = "code는 필수입니다.") String code,
        Boolean isAnonymous
) {
}
