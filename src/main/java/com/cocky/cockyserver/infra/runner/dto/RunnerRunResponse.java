package com.cocky.cockyserver.infra.runner.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** cocky-runner POST /internal/v1/run 응답. status는 OK|TLE|RE|CE|MLE 문자열, compileOutput은 nullable. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RunnerRunResponse(
        String status,
        String stdout,
        String stderr,
        String compileOutput,
        Integer timeMs
) {
}
