package com.cocky.cockyserver.ai.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiClientRetryableTest {

    private HttpClientErrorException client(HttpStatus status, String body) {
        return HttpClientErrorException.create(status, status.name(), HttpHeaders.EMPTY,
                body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    @Test
    void 크레딧소진_429는_재시도_불가() {
        String body = "{\"error\":{\"type\":\"insufficient_quota\",\"code\":\"credit_balance_exhausted\"}}";
        assertFalse(OpenAiClient.isRetryable(client(HttpStatus.TOO_MANY_REQUESTS, body)));
    }

    @Test
    void 일반_rate_limit_429는_재시도_가능() {
        String body = "{\"error\":{\"type\":\"requests\",\"code\":\"rate_limit_exceeded\"}}";
        assertTrue(OpenAiClient.isRetryable(client(HttpStatus.TOO_MANY_REQUESTS, body)));
    }

    @Test
    void 키오류_401은_재시도_불가() {
        assertFalse(OpenAiClient.isRetryable(client(HttpStatus.UNAUTHORIZED, "{}")));
    }

    @Test
    void 서버오류_5xx는_재시도_가능() {
        var e = HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "bad gateway", HttpHeaders.EMPTY,
                new byte[0], StandardCharsets.UTF_8);
        assertTrue(OpenAiClient.isRetryable(e));
    }

    @Test
    void 타임아웃은_재시도_가능() {
        assertTrue(OpenAiClient.isRetryable(new ResourceAccessException("timeout", new SocketTimeoutException())));
    }
}
