package com.cocky.cockyserver.infra.runner;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * cocky-runner 연동 설정. judge.engine=runner일 때 url/token은 필수다(비면 기동 실패 —
 * {@link com.cocky.cockyserver.infra.judge.JudgeConfig}).
 *
 * <p>readTimeoutMs는 케이스 여러 개를 한 번에 채점하는 judge 호출 전체를 기다리는 시간이라
 * 연결 타임아웃보다 훨씬 길다.
 */
@ConfigurationProperties(prefix = "runner")
public record RunnerProperties(
        String url,
        String token,
        @DefaultValue("3000") long connectTimeoutMs,
        @DefaultValue("60000") long readTimeoutMs
) {
}
