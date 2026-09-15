package com.cocky.cockyserver.domain.ranking.dto;

import com.cocky.cockyserver.domain.ranking.entity.RankingSnapshot;
import com.cocky.cockyserver.domain.ranking.entity.ScopeType;
import com.cocky.cockyserver.domain.user.entity.Role;
import com.cocky.cockyserver.domain.user.entity.User;
import com.cocky.cockyserver.global.entity.PeriodType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 랭킹 응답의 nickname이 user.isAnonymousDefault()(사용자 기본값)에 따라 달라지는지 검증.
 * ranking_snapshot이 이름을 직접 저장하지 않고 snapshot.getUser()로 조회 시점 값을 읽으므로
 * (RankingEntryResponse.from Javadoc 참고) User 엔티티 상태만 바꿔가며 확인하면 된다.
 * submission 단위 익명은 스냅샷이 submission을 참조하지 않아 반영할 수 없다(같은 Javadoc 참고).
 */
class RankingEntryResponseTest {

    private User user(String name) {
        User user = new User(1L, "user@gsm.hs.kr", name, 2, 3, 1, "SW과", Role.STUDENT);
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    private RankingSnapshot snapshotFor(User user) {
        return new RankingSnapshot(user, null, PeriodType.WEEKLY, ScopeType.SCHOOL,
                null, null, "2026-07-06", 1, new BigDecimal("87.50"), LocalDateTime.now());
    }

    @Test
    void 익명이_아니면_실명을_반환한다() {
        User user = user("홍길동");

        RankingEntryResponse response = RankingEntryResponse.from(snapshotFor(user));

        assertEquals("홍길동", response.nickname());
    }

    @Test
    void 익명이면_anonymous_nickname을_반환한다() {
        User user = user("홍길동");
        user.updateAnonymousNickname("파란 재귀함수");
        user.updateAnonymousDefault(true);

        RankingEntryResponse response = RankingEntryResponse.from(snapshotFor(user));

        assertEquals("파란 재귀함수", response.nickname());
    }
}
