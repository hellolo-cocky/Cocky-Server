package com.cocky.cockyserver.ai;

import com.cocky.cockyserver.ai.client.OpenAiClient;
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

/**
 * OpenAiClient가 실제 RestClient 파이프라인으로 classic Jackson2 {@code JsonNode} 응답을
 * 파싱하는지 검증한다. objectMapper.writeValueAsString() 같은 수동 직렬화가 아니라,
 * 실제 소켓으로 오간 HTTP 응답 바이트를 OpenAiClient가 제대로 역직렬화하는지 확인하는 것이 목적
 * — Boot 4.1 기본 컨버터(Jackson 3)가 classic JsonNode를 못 읽어 발생했던
 * {@code InvalidDefinitionException} 회귀를 막기 위한 테스트.
 */
class OpenAiClientJsonNodeIT {

    private HttpServer server;

    @BeforeEach
    void startFakeOpenAi() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            String body = """
                    {"choices":[{"message":{"content":"안녕하세요"}}]}
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
    void stopFakeOpenAi() {
        server.stop(0);
    }

    @Test
    void parsesRealHttpResponseIntoClassicJsonNode() {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        AiProperties props = new AiProperties(
                new AiProperties.OpenAi("test-key", baseUrl, 5000),
                null, null, null, null, null);
        OpenAiClient client = new OpenAiClient(props, new ObjectMapper());

        String content = client.chatText("gpt-5.4-nano", "system", "user");

        assertEquals("안녕하세요", content);
    }
}