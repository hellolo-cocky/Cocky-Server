package com.cocky.cockyserver.infra.runner;

import com.cocky.cockyserver.infra.judge.JudgeProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * cocky-runner 구현체 빈만 등록한다(엔진 선택은 {@link com.cocky.cockyserver.infra.judge.JudgeConfig}).
 * {@code @Lazy}라 judge.engine이 runner가 아니면 만들어지지 않는다.
 */
@Configuration
@EnableConfigurationProperties(RunnerProperties.class)
public class RunnerConfig {

    static final String TOKEN_HEADER = "X-Runner-Token";

    /**
     * 연결/읽기 타임아웃을 반드시 건다 — 타임아웃이 없으면 runner가 멈췄을 때 Tomcat 스레드가
     * 무기한 묶인다. HTTP/1.1로 고정해 cleartext h2c 업그레이드 협상을 피한다.
     */
    @Bean
    @Lazy
    public RestClient runnerRestClient(RestClient.Builder restClientBuilder, RunnerProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));

        return restClientBuilder
                .baseUrl(stripTrailingSlash(properties.url()))
                .defaultHeader(TOKEN_HEADER, properties.token())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    @Lazy
    public RunnerJudgeAdapter runnerJudgeAdapter(RestClient runnerRestClient, JudgeProperties judgeProperties) {
        return new RunnerJudgeAdapter(runnerRestClient, judgeProperties);
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
