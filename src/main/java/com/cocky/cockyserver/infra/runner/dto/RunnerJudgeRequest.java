package com.cocky.cockyserver.infra.runner.dto;

import java.util.List;

/** cocky-runner POST /internal/v1/judge 요청 바디. language는 소문자(python|java|c). */
public record RunnerJudgeRequest(
        String language,
        String sourceCode,
        long timeLimitMs,
        int memoryLimitKb,
        List<TestCase> testCases
) {

    public record TestCase(String input, String expectedOutput) {
    }
}
