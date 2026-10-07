package com.cocky.cockyserver.domain.run.service;

import com.cocky.cockyserver.domain.run.config.RunProperties;
import com.cocky.cockyserver.domain.run.exception.RunRateLimitExceededException;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 유저별 슬라이딩 윈도우(60초) 레이트리밋 — 인메모리.
 *
 * <p>Redis는 도입하지 않기로 했고(CLAUDE.md §2) 서버도 단일 인스턴스라 프로세스 메모리로 충분하다.
 * 서버를 여러 대로 늘리면 인스턴스별로 따로 세어지므로 그때는 구조를 다시 봐야 한다. 대상이 로그인한
 * 재학생(약 200명)뿐이라 유저당 deque를 별도로 청소하지 않는다.
 */
@Component
public class RunRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;

    private final Map<Long, Deque<Long>> hitsByUser = new ConcurrentHashMap<>();
    private final int limitPerMinute;
    private final Clock clock;

    public RunRateLimiter(RunProperties properties, Clock clock) {
        this.limitPerMinute = properties.rateLimitPerMinute();
        this.clock = clock;
    }

    /** 허용되면 호출 1건을 기록하고 반환, 제한 초과면 예외를 던진다(기록하지 않음). */
    public void check(Long userId) {
        long now = clock.millis();
        Deque<Long> hits = hitsByUser.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (hits) {
            while (!hits.isEmpty() && hits.peekFirst() <= now - WINDOW_MILLIS) {
                hits.pollFirst();
            }
            if (hits.size() >= limitPerMinute) {
                long retryAfterSeconds = Math.max(1, (hits.peekFirst() + WINDOW_MILLIS - now + 999) / 1000);
                throw new RunRateLimitExceededException(
                        "코드 실행 요청이 너무 많습니다. 분당 %d회까지 가능합니다.".formatted(limitPerMinute),
                        retryAfterSeconds);
            }
            hits.addLast(now);
        }
    }
}
