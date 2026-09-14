package com.cocky.cockyserver.ai.config;

import com.cocky.cockyserver.ai.client.OpenAiClient;
import com.cocky.cockyserver.ai.demo.DemoInstantFeedback;
import com.cocky.cockyserver.ai.demo.DemoNicknameGenerator;
import com.cocky.cockyserver.ai.demo.DemoPeriodFeedback;
import com.cocky.cockyserver.ai.demo.DemoProblemGenerator;
import com.cocky.cockyserver.ai.port.CodeExecutor;
import com.cocky.cockyserver.ai.port.InstantFeedbackProvider;
import com.cocky.cockyserver.ai.port.NicknameGenerator;
import com.cocky.cockyserver.ai.port.PeriodFeedbackProvider;
import com.cocky.cockyserver.ai.port.ProblemGenerator;
import com.cocky.cockyserver.ai.service.InstantFeedbackService;
import com.cocky.cockyserver.ai.service.NicknameService;
import com.cocky.cockyserver.ai.service.PeriodFeedbackService;
import com.cocky.cockyserver.ai.service.ProblemGeneratorService;
import com.cocky.cockyserver.ai.service.SimilarityChecker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 포트 빈 구성. 키가 있으면 실구현, 없으면 데모 구현으로 자동 주입.
 *
 * <p>REAL/DEMO 조건은 ai.openai.api-key 공백 여부로 갈린다(하네스: 키 없으면 데모 강제).
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    /** 키가 비어있지 않을 때(실호출). */
    static final String REAL = "!'${ai.openai.api-key:}'.trim().isEmpty()";
    /** 키가 비어있을 때(데모). */
    static final String DEMO = "'${ai.openai.api-key:}'.trim().isEmpty()";

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Bean
    public SimilarityChecker similarityChecker() {
        return new SimilarityChecker();
    }

    // --- 실구현 (키 있음) ---

    @Bean
    @ConditionalOnExpression(REAL)
    public OpenAiClient openAiClient(AiProperties props, ObjectMapper objectMapper) {
        log.info("AI 모듈: 실호출 모드(OpenAI)");
        return new OpenAiClient(props, objectMapper);
    }

    @Bean
    @ConditionalOnExpression(REAL)
    public ProblemGenerator problemGenerator(OpenAiClient openAi, CodeExecutor executor,
                                             SimilarityChecker similarity, AiProperties props) {
        return new ProblemGeneratorService(openAi, executor, similarity, props);
    }

    /**
     * 즉시 피드백은 공유 {@code openAiClient} 빈(생성용 60s/3회 기준 타임아웃)을 쓰지 않고
     * {@code ai.instant-feedback.timeout-ms}로 만든 전용 {@link OpenAiClient} 인스턴스를 쓴다.
     * {@link org.springframework.http.client.SimpleClientHttpRequestFactory}가 인스턴스별 고정
     * 타임아웃이라 빈을 공유하면 목적별 타임아웃 분리가 불가능하기 때문(단계 1) — 이 메서드
     * 안에서만 만들고 별도 빈으로 노출하지 않아 다른 곳에서 실수로 재사용되는 것도 막는다.
     */
    @Bean
    @ConditionalOnExpression(REAL)
    public InstantFeedbackProvider instantFeedbackProvider(AiProperties props, ObjectMapper objectMapper) {
        OpenAiClient instantFeedbackOpenAiClient = new OpenAiClient(
                props, objectMapper, props.instantFeedback().timeoutMs());
        return new InstantFeedbackService(instantFeedbackOpenAiClient, props);
    }

    @Bean
    @ConditionalOnExpression(REAL)
    public PeriodFeedbackProvider periodFeedbackProvider(OpenAiClient openAi, AiProperties props) {
        return new PeriodFeedbackService(openAi, props);
    }

    @Bean
    @ConditionalOnExpression(REAL)
    public NicknameGenerator nicknameGenerator(OpenAiClient openAi, AiProperties props) {
        return new NicknameService(openAi, props);
    }

    // --- 데모 구현 (키 없음) ---

    @Bean
    @ConditionalOnExpression(DEMO)
    public ProblemGenerator demoProblemGenerator() {
        log.info("AI 모듈: 데모 모드(키 없음) — 문제 생성");
        return new DemoProblemGenerator();
    }

    @Bean
    @ConditionalOnExpression(DEMO)
    public InstantFeedbackProvider demoInstantFeedback() {
        return new DemoInstantFeedback();
    }

    @Bean
    @ConditionalOnExpression(DEMO)
    public PeriodFeedbackProvider demoPeriodFeedback() {
        return new DemoPeriodFeedback();
    }

    @Bean
    @ConditionalOnExpression(DEMO)
    public NicknameGenerator demoNicknameGenerator() {
        return new DemoNicknameGenerator();
    }
}
