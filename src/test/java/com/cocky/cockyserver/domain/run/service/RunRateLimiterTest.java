package com.cocky.cockyserver.domain.run.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cocky.cockyserver.domain.run.config.RunProperties;
import com.cocky.cockyserver.domain.run.exception.RunRateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class RunRateLimiterTest {

    private final MutableClock clock = new MutableClock();
    private final RunRateLimiter limiter = new RunRateLimiter(new RunProperties(2), clock);

    @Test
    void 한도까지는_허용하고_초과하면_예외다() {
        limiter.check(1L);
        limiter.check(1L);

        assertThatThrownBy(() -> limiter.check(1L)).isInstanceOf(RunRateLimitExceededException.class);
    }

    @Test
    void 윈도우가_지나면_다시_허용된다() {
        limiter.check(1L);
        limiter.check(1L);

        clock.advance(Duration.ofSeconds(61));

        assertThatCode(() -> limiter.check(1L)).doesNotThrowAnyException();
    }

    @Test
    void 슬라이딩_윈도우라_가장_오래된_호출이_빠지면_한_건씩_풀린다() {
        limiter.check(1L);            // t=0
        clock.advance(Duration.ofSeconds(30));
        limiter.check(1L);            // t=30

        clock.advance(Duration.ofSeconds(31)); // t=61: t=0 호출만 윈도우 밖
        limiter.check(1L);
        assertThatThrownBy(() -> limiter.check(1L)).isInstanceOf(RunRateLimitExceededException.class);
    }

    @Test
    void 유저별로_따로_센다() {
        limiter.check(1L);
        limiter.check(1L);

        assertThatCode(() -> limiter.check(2L)).doesNotThrowAnyException();
    }

    @Test
    void 거절된_호출은_기록되지_않는다() {
        limiter.check(1L);
        limiter.check(1L);
        assertThatThrownBy(() -> limiter.check(1L)).isInstanceOf(RunRateLimitExceededException.class);
        assertThatThrownBy(() -> limiter.check(1L)).isInstanceOf(RunRateLimitExceededException.class);

        clock.advance(Duration.ofSeconds(61));

        limiter.check(1L);
        limiter.check(1L);
    }

    @Test
    void RetryAfter는_가장_오래된_호출이_빠질_때까지의_초다() {
        limiter.check(1L);
        clock.advance(Duration.ofSeconds(20));
        limiter.check(1L);

        assertThatThrownBy(() -> limiter.check(1L))
                .isInstanceOfSatisfying(RunRateLimitExceededException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(40));
    }

    private static class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-10-07T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Seoul");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
