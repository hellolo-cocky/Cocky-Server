package com.cocky.cockyserver.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 모듈 설정 바인딩. 값은 application.yml → 환경변수에서 온다.
 * 시크릿(api-key)은 절대 로그/프롬프트에 싣지 않는다.
 */
@ConfigurationProperties(prefix = "ai")
public record AiProperties(
        OpenAi openai,
        Models models,
        String executor,
        Exec exec,
        Generation generation,
        InstantFeedback instantFeedback
) {
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final long DEFAULT_OPENAI_TIMEOUT_MS = 60_000;
    private static final long DEFAULT_EXEC_TIMEOUT_MS = 5_000;
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.80;
    private static final String DEFAULT_GEN_MODEL = "gpt-5.4-mini";
    private static final String DEFAULT_NANO_MODEL = "gpt-5.4-nano";
    /** 즉시 피드백은 제출 API 동기 경로에서 사용자가 기다린다 — 문제 생성(60s/3회)보다 짧게. */
    private static final long DEFAULT_INSTANT_FEEDBACK_TIMEOUT_MS = 10_000;
    private static final int DEFAULT_INSTANT_FEEDBACK_MAX_RETRIES = 1;

    /**
     * ai.* 설정이 아예 없는 환경(예: 테스트 전용 application.yml이 main 설정을
     * 대체하는 경우)에서도 NPE 없이 데모 모드로 뜨도록 기본값을 채운다.
     */
    public AiProperties {
        if (openai == null) {
            openai = new OpenAi("", DEFAULT_BASE_URL, DEFAULT_OPENAI_TIMEOUT_MS);
        }
        if (models == null) {
            models = new Models(DEFAULT_GEN_MODEL, DEFAULT_NANO_MODEL, DEFAULT_NANO_MODEL,
                    DEFAULT_GEN_MODEL, DEFAULT_GEN_MODEL);
        }
        if (executor == null || executor.isBlank()) {
            executor = "local";
        }
        if (exec == null) {
            exec = new Exec(DEFAULT_EXEC_TIMEOUT_MS);
        }
        if (generation == null) {
            generation = new Generation(DEFAULT_MAX_RETRIES, DEFAULT_SIMILARITY_THRESHOLD);
        }
        if (instantFeedback == null) {
            instantFeedback = new InstantFeedback(
                    DEFAULT_INSTANT_FEEDBACK_TIMEOUT_MS, DEFAULT_INSTANT_FEEDBACK_MAX_RETRIES);
        }
    }
    public record OpenAi(String apiKey, String baseUrl, long timeoutMs) {
    }

    public record Models(
            String generation,
            String instantFeedback,
            String roundFeedback,
            String weeklyFeedback,
            String monthlyFeedback
    ) {
    }

    public record Exec(long timeoutMs) {
    }

    public record Generation(int maxRetries, double similarityThreshold) {
    }

    /** 즉시 피드백 전용 타임아웃/재시도. openai.timeout-ms·generation.max-retries와 별개다(단계 1). */
    public record InstantFeedback(long timeoutMs, int maxRetries) {
    }

    public boolean demoMode() {
        return openai == null || openai.apiKey() == null || openai.apiKey().isBlank();
    }
}
