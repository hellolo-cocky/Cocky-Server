package com.cocky.cockyserver.infra.stub;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * 스텁 구현체 빈만 등록한다. {@code @Lazy}인 이유: 즉시 생성되면 실채점 엔진을 쓰는 환경에서도
 * {@link StubJudgeService} 기동 배너(WARN "스텁 활성화")가 찍혀 스텁이 켜져 있다는 오해를 준다
 * ({@code JudgeConfigTest}에서 검증).
 */
@Configuration
public class StubConfig {

    @Bean
    @Lazy
    public StubJudgeService stubJudgeService() {
        return new StubJudgeService();
    }
}
