package com.cocky.cockyserver.ai;

import com.cocky.cockyserver.ai.config.AiProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 단계 1: 즉시 피드백 전용 타임아웃/시도 횟수 설정이 문제 생성 경로와 분리돼 있는지 확인.
 * ai.* 블록이 통째로 비어도(all-null) 기본값이 채워지는지가 핵심 — application.yml 미설정
 * 환경(테스트 등)에서도 두 경로가 서로 다른 기본값을 갖는다.
 *
 * <p>PR 리뷰 반영: maxAttempts는 재시도 횟수가 아니라 총 시도 횟수(naming 정정)이고,
 * timeoutMs·maxAttempts가 1 미만이면 설정 실수로 보고 기본값으로 폴백한다(범위 검증).
 */
class AiPropertiesTest {

    private final AiProperties props = new AiProperties(null, null, null, null, null, null);

    @Test
    void instantFeedbackDefaultsAreShorterThanGeneration() {
        assertEquals(10_000L, props.instantFeedback().timeoutMs());
        assertEquals(1, props.instantFeedback().maxAttempts());
    }

    @Test
    void generationDefaultsAreUnchanged() {
        assertEquals(60_000L, props.openai().timeoutMs());
        assertEquals(3, props.generation().maxAttempts());
    }

    @Test
    void zeroOrNegativeTimeoutMsFallsBackToDefault() {
        AiProperties zero = new AiProperties(
                null, null, null, null, null,
                new AiProperties.InstantFeedback(0, 1));
        AiProperties negative = new AiProperties(
                null, null, null, null, null,
                new AiProperties.InstantFeedback(-1, 1));

        assertEquals(10_000L, zero.instantFeedback().timeoutMs());
        assertEquals(10_000L, negative.instantFeedback().timeoutMs());
    }

    @Test
    void zeroOrNegativeMaxAttemptsFallsBackToDefault() {
        AiProperties zero = new AiProperties(
                null, null, null, null,
                new AiProperties.Generation(0, 0.80), null);
        AiProperties negative = new AiProperties(
                null, null, null, null,
                new AiProperties.Generation(-1, 0.80), null);

        assertEquals(3, zero.generation().maxAttempts());
        assertEquals(3, negative.generation().maxAttempts());
    }

    @Test
    void validRangeValuesArePreservedAsIs() {
        AiProperties custom = new AiProperties(
                new AiProperties.OpenAi("key", "https://example.com", 30_000),
                null, null, null,
                new AiProperties.Generation(5, 0.80),
                new AiProperties.InstantFeedback(2_000, 2));

        assertEquals(30_000L, custom.openai().timeoutMs());
        assertEquals(5, custom.generation().maxAttempts());
        assertEquals(2_000L, custom.instantFeedback().timeoutMs());
        assertEquals(2, custom.instantFeedback().maxAttempts());
    }
}
