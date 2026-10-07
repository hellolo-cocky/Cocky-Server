package com.cocky.cockyserver.infra.judge0;

import com.cocky.cockyserver.infra.judge.JudgeProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.client.RestClient;

/**
 * Judge0 구현체 빈만 등록한다. 엔진 선택(judge.engine)과 스텁 폴백은
 * {@link com.cocky.cockyserver.infra.judge.JudgeConfig}가 맡는다.
 *
 * <p>{@code judge0Client}/{@code judge0Adapter}는 {@link Lazy}다 — judge.engine이 judge0가 아닐 때
 * (예: runner) 쓰지도 않는 Judge0 클라이언트가 컨테이너 기동 시점에 만들어지지 않게 한다.
 */
@Configuration
@EnableConfigurationProperties(Judge0Properties.class)
public class Judge0Config {

    @Bean
    public LanguageMapper languageMapper() {
        return new LanguageMapper();
    }

    @Bean
    @Lazy
    public Judge0Client judge0Client(RestClient.Builder restClientBuilder, Judge0Properties properties) {
        return new Judge0Client(restClientBuilder, properties);
    }

    @Bean
    @Lazy
    public Judge0Adapter judge0Adapter(Judge0Client judge0Client, LanguageMapper languageMapper,
                                       JudgeProperties judgeProperties) {
        return new Judge0Adapter(judge0Client, languageMapper, judgeProperties);
    }
}
