package com.cocky.cockyserver.ai;

import com.cocky.cockyserver.ai.client.OpenAiClient;
import com.cocky.cockyserver.ai.client.OpenAiException;
import com.cocky.cockyserver.ai.config.AiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 단계 1: {@link OpenAiClient}의 3-arg 생성자(목적별 timeoutMs override)가 실제로
 * connect/read 타임아웃에 반영되는지 검증. 응답을 일부러 지연시키는 가짜 서버로,
 * 즉시 피드백처럼 짧은 타임아웃을 주면 실제로 읽기 타임아웃이 걸리고, 문제 생성처럼
 * 긴 타임아웃을 주면 같은 지연에도 성공하는 것을 함께 확인한다.
 */
class OpenAiClientTimeoutIT {

    private static final long SERVER_DELAY_MS = 800;

    private HttpServer server;

    @BeforeEach
    void startSlowFakeOpenAi() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            try {
                Thread.sleep(SERVER_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            String body = """
                    {"choices":[{"message":{"content":"지연 응답"}}]}
                    """;
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopSlowFakeOpenAi() {
        server.stop(0);
    }

    @Test
    void shortInstantFeedbackTimeoutFailsOnSlowResponse() {
        OpenAiClient client = clientWithTimeout(200); // 즉시 피드백 기본값(10s)보다 훨씬 짧게 잡아 결정적으로 재현

        assertThrows(OpenAiException.class,
                () -> client.chatText("gpt-5.4-nano", "system", "user"));
    }

    @Test
    void longGenerationTimeoutSucceedsOnSameSlowResponse() {
        OpenAiClient client = clientWithTimeout(5_000);

        String content = client.chatText("gpt-5.4-mini", "system", "user");

        assertEquals("지연 응답", content);
    }

    private OpenAiClient clientWithTimeout(long timeoutMs) {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        AiProperties props = new AiProperties(
                new AiProperties.OpenAi("test-key", baseUrl, 60_000),
                null, null, null, null, null);
        return new OpenAiClient(props, new ObjectMapper(), timeoutMs);
    }
}
