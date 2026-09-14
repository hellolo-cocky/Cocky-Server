package com.cocky.cockyserver.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(AiProperties.class);

    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final long DEFAULT_OPENAI_TIMEOUT_MS = 60_000;
    private static final long DEFAULT_EXEC_TIMEOUT_MS = 5_000;
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.80;
    private static final String DEFAULT_GEN_MODEL = "gpt-5.4-mini";
    private static final String DEFAULT_NANO_MODEL = "gpt-5.4-nano";
    /** 즉시 피드백은 제출 API 동기 경로에서 사용자가 기다린다 — 문제 생성(60s/3회)보다 짧게. */
    private static final long DEFAULT_INSTANT_FEEDBACK_TIMEOUT_MS = 10_000;
    private static final int DEFAULT_INSTANT_FEEDBACK_MAX_ATTEMPTS = 1;

    /**
     * ai.* 설정이 아예 없는 환경(예: 테스트 전용 application.yml이 main 설정을
     * 대체하는 경우)에서도 NPE 없이 데모 모드로 뜨도록 기본값을 채운다.
     *
     * <p>블록 자체가 없는 경우(null)뿐 아니라, timeoutMs·maxAttempts가 1 미만(설정 실수로
     * 0이나 음수가 들어온 경우 포함 — 프리미티브라 "안 씀"과 "0으로 씀"을 구분 못 한다)이면
     * 조용히 무시하지 않고 WARN을 남긴 뒤 기본값으로 대체한다.
     */
    public AiProperties {
        if (openai == null) {
            openai = new OpenAi("", DEFAULT_BASE_URL, DEFAULT_OPENAI_TIMEOUT_MS);
        } else {
            long timeoutMs = validTimeout("ai.openai.timeout-ms", openai.timeoutMs(), DEFAULT_OPENAI_TIMEOUT_MS);
            if (timeoutMs != openai.timeoutMs()) {
                openai = new OpenAi(openai.apiKey(), openai.baseUrl(), timeoutMs);
            }
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
            generation = new Generation(DEFAULT_MAX_ATTEMPTS, DEFAULT_SIMILARITY_THRESHOLD);
        } else {
            int maxAttempts = validAttempts("ai.generation.max-attempts", generation.maxAttempts(), DEFAULT_MAX_ATTEMPTS);
            if (maxAttempts != generation.maxAttempts()) {
                generation = new Generation(maxAttempts, generation.similarityThreshold());
            }
        }
        if (instantFeedback == null) {
            instantFeedback = new InstantFeedback(
                    DEFAULT_INSTANT_FEEDBACK_TIMEOUT_MS, DEFAULT_INSTANT_FEEDBACK_MAX_ATTEMPTS);
        } else {
            long timeoutMs = validTimeout("ai.instant-feedback.timeout-ms",
                    instantFeedback.timeoutMs(), DEFAULT_INSTANT_FEEDBACK_TIMEOUT_MS);
            int maxAttempts = validAttempts("ai.instant-feedback.max-attempts",
                    instantFeedback.maxAttempts(), DEFAULT_INSTANT_FEEDBACK_MAX_ATTEMPTS);
            if (timeoutMs != instantFeedback.timeoutMs() || maxAttempts != instantFeedback.maxAttempts()) {
                instantFeedback = new InstantFeedback(timeoutMs, maxAttempts);
            }
        }
    }

    /** timeoutMs가 1 미만(미설정 시 프리미티브 기본값 0 포함)이면 WARN 후 기본값으로 대체. */
    private static long validTimeout(String key, long timeoutMs, long fallback) {
        if (timeoutMs < 1) {
            log.warn("{}={} 은 유효하지 않음(1 미만) — 기본값 {}ms로 대체", key, timeoutMs, fallback);
            return fallback;
        }
        return timeoutMs;
    }

    /** maxAttempts가 1 미만(미설정 시 프리미티브 기본값 0 포함)이면 WARN 후 기본값으로 대체. */
    private static int validAttempts(String key, int maxAttempts, int fallback) {
        if (maxAttempts < 1) {
            log.warn("{}={} 은 유효하지 않음(1 미만) — 기본값 {}회로 대체", key, maxAttempts, fallback);
            return fallback;
        }
        return maxAttempts;
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

    public record Generation(int maxAttempts, double similarityThreshold) {
    }

    /**
     * 즉시 피드백 전용 타임아웃/시도 횟수. openai.timeout-ms·generation.max-attempts와 별개다(단계 1).
     * maxAttempts는 재시도 횟수가 아니라 총 시도 횟수다(1이면 단발 호출, 재시도 없음).
     */
    public record InstantFeedback(long timeoutMs, int maxAttempts) {
    }

    public boolean demoMode() {
        return openai == null || openai.apiKey() == null || openai.apiKey().isBlank();
    }
}
