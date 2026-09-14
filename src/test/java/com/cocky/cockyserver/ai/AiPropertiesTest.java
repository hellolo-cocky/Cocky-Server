package com.cocky.cockyserver.ai;

import com.cocky.cockyserver.ai.config.AiProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 단계 1: 즉시 피드백 전용 타임아웃/재시도 설정이 문제 생성 경로와 분리돼 있는지 확인.
 * ai.* 블록이 통째로 비어도(all-null) 기본값이 채워지는지가 핵심 — application.yml 미설정
 * 환경(테스트 등)에서도 두 경로가 서로 다른 기본값을 갖는다.
 */
class AiPropertiesTest {

    private final AiProperties props = new AiProperties(null, null, null, null, null, null);

    @Test
    void instantFeedbackDefaultsAreShorterThanGeneration() {
        assertEquals(10_000L, props.instantFeedback().timeoutMs());
        assertEquals(1, props.instantFeedback().maxRetries());
    }

    @Test
    void generationDefaultsAreUnchanged() {
        assertEquals(60_000L, props.openai().timeoutMs());
        assertEquals(3, props.generation().maxRetries());
    }
}
