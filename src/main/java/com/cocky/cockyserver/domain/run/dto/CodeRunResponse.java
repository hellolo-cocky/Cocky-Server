package com.cocky.cockyserver.domain.run.dto;

import com.cocky.cockyserver.domain.submission.judge.RunResult;
import com.cocky.cockyserver.domain.submission.judge.RunStatus;

public record CodeRunResponse(
        RunStatus status,
        String stdout,
        String stderr,
        String compileOutput,
        Integer timeMs
) {

    public static CodeRunResponse from(RunResult result) {
        return new CodeRunResponse(result.status(), result.stdout(), result.stderr(),
                result.compileOutput(), result.timeMs());
    }
}
