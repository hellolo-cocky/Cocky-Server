package com.cocky.cockyserver.infra.judge0;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Judge0 연동 설정. token은 self-hosted Judge0(CE)의 {@code AUTHN_TOKEN} 인증을 쓸 때만
 * 채운다 — 비어 있으면 인증 헤더 없이 호출한다.
 *
 * <p>시간/메모리 제한 기본값은 엔진 공통 설정({@code judge.default-*},
 * {@link com.cocky.cockyserver.infra.judge.JudgeProperties})으로 옮겼다.
 */
@ConfigurationProperties(prefix = "judge0")
public record Judge0Properties(
        String url,
        String token
) {
}
