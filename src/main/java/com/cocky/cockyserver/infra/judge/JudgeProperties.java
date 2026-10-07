package com.cocky.cockyserver.infra.judge;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 채점 엔진 공통 설정.
 *
 * <p>engine은 judge0 | runner | stub 중 하나. defaultTimeLimitMs/defaultMemoryLimitKb는 problem별
 * 제한 컬럼이 아직 없어 쓰는 전역 기본값이며 어느 엔진이든 이 값을 그대로 넘긴다(problem별 제한은
 * 후속 작업).
 */
@ConfigurationProperties(prefix = "judge")
public record JudgeProperties(
        @DefaultValue("stub") String engine,
        @DefaultValue("2000") long defaultTimeLimitMs,
        @DefaultValue("131072") int defaultMemoryLimitKb
) {
}
