package com.cocky.cockyserver.infra.runner.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * cocky-runner POST /internal/v1/judge 응답. verdict는 AC|WA|TLE|RE|CE|MLE 문자열로 받아
 * 어댑터가 매핑한다 — 계약 밖의 값이 오면 enum 역직렬화 에러 대신 어댑터가 명시적으로 502 처리한다.
 * maxMemoryKb/compileOutput은 nullable.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RunnerJudgeResponse(
        String verdict,
        Integer passedCount,
        Integer totalCount,
        Integer maxTimeMs,
        Integer maxMemoryKb,
        String compileOutput
) {
}
