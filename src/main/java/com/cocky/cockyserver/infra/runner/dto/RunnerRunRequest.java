package com.cocky.cockyserver.infra.runner.dto;

/** cocky-runner POST /internal/v1/run 요청 바디. language는 소문자(python|java|c). */
public record RunnerRunRequest(
        String language,
        String sourceCode,
        String stdin,
        long timeLimitMs,
        int memoryLimitKb
) {
}
