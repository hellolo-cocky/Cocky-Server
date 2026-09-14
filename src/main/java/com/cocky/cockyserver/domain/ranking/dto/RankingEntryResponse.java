package com.cocky.cockyserver.domain.ranking.dto;

import com.cocky.cockyserver.domain.ranking.entity.RankingSnapshot;
import com.cocky.cockyserver.domain.user.entity.User;
import java.math.BigDecimal;

public record RankingEntryResponse(Integer rank, Long userId, String nickname, BigDecimal score) {

    /**
     * 이름은 스냅샷에 저장돼 있지 않고 {@code snapshot.getUser()}(FK 연관관계)로 매번 조회 시점의
     * User를 읽어온다 — ranking_snapshot 테이블 자체가 이름을 따로 보관하지 않으므로 "스냅샷
     * 시점 값 vs 조회 시점 값" 선택의 여지가 원래 없었다(항상 조회 시점 값). 익명 여부도 같은
     * 원칙을 그대로 따른다: user.isAnonymous()가 true면 anonymous_nickname, false면 실명.
     * (toggle API가 항상 nickname을 먼저 채운 뒤 anonymous를 켜므로 anonymous=true인데
     * anonymous_nickname이 null인 상태는 정상 경로에서 발생하지 않는다.)
     */
    public static RankingEntryResponse from(RankingSnapshot snapshot) {
        User user = snapshot.getUser();
        String nickname = user.isAnonymous() ? user.getAnonymousNickname() : user.getName();
        return new RankingEntryResponse(snapshot.getRank(), user.getId(), nickname, snapshot.getScore());
    }
}
