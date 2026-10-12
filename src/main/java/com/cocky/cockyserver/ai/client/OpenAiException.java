package com.cocky.cockyserver.ai.client;

/**
 * OpenAI 호출/응답 오류.
 *
 * <p>{@code retryable=false}는 몇 번을 다시 불러도 같은 결과일 것이 확실한 오류(크레딧 소진·키 오류)다.
 * 호출부는 이 값으로 재시도 여부를 판단한다. 기본은 재시도 가능(true).
 */
public class OpenAiException extends RuntimeException {

    private final boolean retryable;

    public OpenAiException(String message) {
        this(message, null, true);
    }

    public OpenAiException(String message, Throwable cause) {
        this(message, cause, true);
    }

    public OpenAiException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
