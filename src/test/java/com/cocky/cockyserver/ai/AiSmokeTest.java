package com.cocky.cockyserver.ai;

import com.cocky.cockyserver.ai.client.OpenAiClient;
import com.cocky.cockyserver.ai.config.AiProperties;
import com.cocky.cockyserver.ai.dto.GeneratedProblem;
import com.cocky.cockyserver.ai.dto.GenerationRequest;
import com.cocky.cockyserver.ai.exec.LocalProcessExecutor;
import com.cocky.cockyserver.ai.service.NicknameService;
import com.cocky.cockyserver.ai.service.ProblemGeneratorService;
import com.cocky.cockyserver.ai.service.SimilarityChecker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 실키 스모크. OPENAI_API_KEY 환경변수 있을 때만 실행. 실제 OpenAI + 로컬 검증.
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class AiSmokeTest {

    private AiProperties props() {
        String key = System.getenv("OPENAI_API_KEY");
        String genModel = envOr("OPENAI_MODEL_GENERATION", "gpt-5.4-mini");
        String nanoModel = envOr("OPENAI_MODEL_INSTANT_FEEDBACK", "gpt-5.4-nano");
        return new AiProperties(
                new AiProperties.OpenAi(key, "https://api.openai.com/v1", 60000),
                new AiProperties.Models(genModel, nanoModel, nanoModel, genModel, genModel),
                "local",
                new AiProperties.Exec(5000),
                new AiProperties.Generation(3, 0.80),
                new AiProperties.InstantFeedback(10000, 1));
    }

    @Test
    void generatesOneRealProblem() {
        AiProperties props = props();
        var service = new ProblemGeneratorService(
                new OpenAiClient(props, new ObjectMapper()), new LocalProcessExecutor(props), new SimilarityChecker(), props);

        var req = new GenerationRequest(
                List.of(com.cocky.cockyserver.ai.dto.Language.PYTHON),
                List.of(com.cocky.cockyserver.ai.dto.Difficulty.EASY),
                "동적 프로그래밍", "메모이제이션", List.of(), List.of());

        var outcome = service.generate(req);
        assertTrue(outcome.complete(), "실패 조합: " + outcome.failures());
        List<GeneratedProblem> problems = outcome.problems();
        assertFalse(problems.isEmpty());
        assertFalse(problems.get(0).statement().isBlank());
    }

    @Test
    void generatesRealNickname() {
        AiProperties props = props();
        String nickname = new NicknameService(new OpenAiClient(props, new ObjectMapper()), props).generate();
        assertFalse(nickname.isBlank());
    }

    private String envOr(String key, String fallback) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? fallback : v;
    }
}
