package com.cocky.cockyserver.domain.run.exception;

/** /run 유저별 분당 호출 제한 초과(429). */
public class RunRateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RunRateLimitExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
