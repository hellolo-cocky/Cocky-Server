package com.cocky.cockyserver.infra.judge0;

import com.cocky.cockyserver.domain.submission.judge.JudgeService;
import com.cocky.cockyserver.infra.stub.StubJudgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.web.client.RestClient;

/**
 * 채점 엔진 선택.
 * - JUDGE0_URL이 설정돼 있으면 → Judge0Adapter(실채점).
 * - JUDGE0_URL이 비어 있으면(미설정) → StubJudgeService 폴백. 학교 채점 VM 재구축(11월)
 *   전까지 Judge0 없이 개발이 막히지 않게 하기 위함 — ai.executor의 OPENAI_API_KEY 부재 시
 *   데모 모드 폴백과 동일한 패턴({@link com.cocky.cockyserver.ai.config.ExecutorConfig}).
 */
@Configuration
@EnableConfigurationProperties(Judge0Properties.class)
public class Judge0Config {

    private static final Logger log = LoggerFactory.getLogger(Judge0Config.class);

    /**
     * 이 프로젝트는 spring-boot-starter-web이 아니라 spring-boot-starter-webmvc만 쓰는데,
     * Boot 4.1은 RestClient.Builder 자동구성을 별도 모듈(spring-boot-starter-restclient)로
     * 분리해놔서 webmvc 스타터만으로는 이 빈이 생기지 않는다. 의존성을 늘리는 대신 여기서
     * 직접 등록한다. 매 주입마다 새 Builder를 받도록 프로토타입 스코프로 둔다(빌더는
     * baseUrl/헤더 설정으로 상태를 바꾸는 1회용 객체라 싱글톤으로 공유하면 안 됨).
     */
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public LanguageMapper languageMapper() {
        return new LanguageMapper();
    }

    @Bean
    public Judge0Client judge0Client(RestClient.Builder restClientBuilder, Judge0Properties properties) {
        return new Judge0Client(restClientBuilder, properties);
    }

    @Bean
    public JudgeService judgeService(Judge0Client judge0Client, LanguageMapper languageMapper,
                                     Judge0Properties properties) {
        if (properties.url() == null || properties.url().isBlank()) {
            log.warn("JUDGE0_URL 미설정 — StubJudgeService로 폴백합니다.");
            return new StubJudgeService();
        }
        log.info("채점 엔진: Judge0Adapter (url={})", properties.url());
        return new Judge0Adapter(judge0Client, languageMapper, properties);
    }
}
