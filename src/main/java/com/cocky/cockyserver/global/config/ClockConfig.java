package com.cocky.cockyserver.global.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** 서버 JVM 기본 시간대(컨테이너는 UTC)와 무관하게 회차 열림/마감 비교를 KST로 고정한다. */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
