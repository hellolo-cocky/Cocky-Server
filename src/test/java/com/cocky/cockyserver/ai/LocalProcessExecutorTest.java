package com.cocky.cockyserver.ai;

import com.cocky.cockyserver.ai.config.AiProperties;
import com.cocky.cockyserver.ai.dto.ExecRequest;
import com.cocky.cockyserver.ai.dto.ExecResult;
import com.cocky.cockyserver.ai.dto.Language;
import com.cocky.cockyserver.ai.exec.LocalProcessExecutor;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 로컬 실행기 검증. python 없으면 스킵(assumeTrue).
 */
class LocalProcessExecutorTest {

    private LocalProcessExecutor executor(long timeoutMs) {
        AiProperties props = new AiProperties(
                new AiProperties.OpenAi("", "", 1000),
                null,
                "local",
                new AiProperties.Exec(timeoutMs),
                null,
                null);
        return new LocalProcessExecutor(props);
    }

    @Test
    void pythonEchoRunsAndMatches() {
        assumeTrue(pythonAvailable(), "python 미설치 — 스킵");
        LocalProcessExecutor exec = executor(5000);
        String code = "import sys\nprint(sys.stdin.read().strip())";

        ExecResult r = exec.run(new ExecRequest(Language.PYTHON, code, "hello"));

        assertTrue(r.success(), "stderr/exit: " + r.compileError());
        assertEquals("hello", r.stdout().strip());
    }

    @Test
    void infiniteLoopTimesOut() {
        assumeTrue(pythonAvailable(), "python 미설치 — 스킵");
        LocalProcessExecutor exec = executor(1000);
        String code = "while True:\n    pass";

        ExecResult r = exec.run(new ExecRequest(Language.PYTHON, code, ""));

        assertTrue(r.timedOut());
    }

    private boolean pythonAvailable() {
        String cmd = System.getProperty("os.name").toLowerCase().contains("win") ? "python" : "python3";
        try {
            Process p = new ProcessBuilder(cmd, "--version").redirectErrorStream(true).start();
            return p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
