package com.cocky.cockyserver.ai.client;

import com.cocky.cockyserver.ai.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions 얇은 래퍼(RestClient, 무의존).
 *
 * <p>시크릿(api-key)은 Authorization 헤더로만 전달하며 로그·프롬프트에 절대 싣지 않는다.
 */
public class OpenAiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);

    private final RestClient restClient;

    /**
     * classic Jackson2 {@code ObjectMapper}({@link com.cocky.cockyserver.global.config.JacksonConfig})를
     * 받아 {@link MappingJackson2HttpMessageConverter}로 명시 등록한다. Boot 4.1 기본 컨버터 체인은
     * Jackson 3 기반이라 classic {@link JsonNode} 응답을 파싱하지 못한다
     * ({@code InvalidDefinitionException: Type definition error} 발생) — {@code DataGsmOauthClient}와
     * 동일한 패턴.
     *
     * <p>타임아웃은 {@code props.openai().timeoutMs()}(생성용 기본값)를 쓴다. 호출 목적별로 다른
     * 타임아웃이 필요하면(예: 즉시 피드백) {@link #OpenAiClient(AiProperties, ObjectMapper, long)}를 쓴다.
     */
    public OpenAiClient(AiProperties props, ObjectMapper objectMapper) {
        this(props, objectMapper, props.openai().timeoutMs());
    }

    /**
     * 호출 목적별로 connect/read 타임아웃을 달리 쓰고 싶을 때의 생성자(단계 1: 즉시 피드백 전용
     * 타임아웃 분리). {@link SimpleClientHttpRequestFactory}는 인스턴스마다 타임아웃이 고정이라
     * 요청 단위로 바꿀 수 없으므로, 목적별로 별도 {@code OpenAiClient} 인스턴스를 만드는 방식을
     * 택했다 — baseUrl/apiKey는 여전히 {@code props.openai()}를 그대로 쓰고 timeoutMs만 override한다.
     */
    public OpenAiClient(AiProperties props, ObjectMapper objectMapper, long timeoutMs) {
        AiProperties.OpenAi cfg = props.openai();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
        this.restClient = RestClient.builder()
                .baseUrl(cfg.baseUrl())
                .defaultHeader("Authorization", "Bearer " + cfg.apiKey())
                .requestFactory(factory)
                .messageConverters(converters -> {
                    converters.clear();
                    converters.add(converter);
                })
                .build();
    }

    /** JSON 객체 응답 강제(문제 생성 등 구조화 출력용). content(JSON 문자열) 반환. */
    public String chatJson(String model, String systemPrompt, String userPrompt) {
        return chat(model, systemPrompt, userPrompt, true);
    }

    /** 자유 텍스트 응답(총평·닉네임 등). */
    public String chatText(String model, String systemPrompt, String userPrompt) {
        return chat(model, systemPrompt, userPrompt, false);
    }

    private String chat(String model, String systemPrompt, String userPrompt, boolean jsonMode) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        if (jsonMode) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        JsonNode response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            // 401/429/5xx/타임아웃 등 — 호출부(오케스트레이터)가 OpenAiException 하나로 재시도 판단.
            log.warn("OpenAI 호출 실패(model={}): {}", model, e.getMessage());
            throw new OpenAiException("OpenAI 호출 실패: " + e.getMessage(), e);
        }

        if (response == null || !response.has("choices") || response.get("choices").isEmpty()) {
            log.warn("OpenAI 응답에 choices 없음");
            throw new OpenAiException("OpenAI 응답 형식 오류");
        }
        return response.get("choices").get(0).path("message").path("content").asText();
    }
}
