package com.cocky.cockyserver.domain.submission.judge;

/**
 * 코드 단순 실행 결과. compileOutput은 CE일 때만 값이 있고, timeMs는 엔진이 측정치를 주지
 * 못하면 null일 수 있다.
 */
public record RunResult(RunStatus status, String stdout, String stderr, String compileOutput, Integer timeMs) {
}
