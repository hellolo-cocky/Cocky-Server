package com.cocky.cockyserver.domain.run.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** POST /api/v1/run 설정. rateLimitPerMinute는 유저당 분당 허용 호출 수. */
@ConfigurationProperties(prefix = "run")
public record RunProperties(
        @DefaultValue("10") int rateLimitPerMinute
) {
}
