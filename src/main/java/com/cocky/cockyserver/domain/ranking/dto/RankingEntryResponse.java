package com.cocky.cockyserver.domain.ranking.dto;

import com.cocky.cockyserver.domain.ranking.entity.RankingSnapshot;
import com.cocky.cockyserver.domain.user.entity.User;
import java.math.BigDecimal;

public record RankingEntryResponse(Integer rank, Long userId, String nickname, BigDecimal score) {

    /**
     * 이름은 스냅샷에 저장돼 있지 않고 {@code snapshot.getUser()}(FK 연관관계)로 매번 조회 시점의
     * User를 읽어온다 — ranking_snapshot 테이블 자체가 이름을 따로 보관하지 않으므로 "스냅샷
     * 시점 값 vs 조회 시점 값" 선택의 여지가 원래 없었다(항상 조회 시점 값).
     *
     * <p>익명 여부는 user.isAnonymousDefault()(사용자 기본값)를 쓴다 — submission.isAnonymous()
     * (제출별 덮어쓰기)로 바꾸고 싶어도 구조상 불가능하다: ranking_snapshot은 특정 submission을
     * 참조하지 않고 {@code UserScoreAggregate}(여러 제출의 점수 합)로만 만들어지고, TWO_DAY조차
     * 한 라운드의 문제 9개 제출을 합산한 결과라 "이 스냅샷 = 이 제출 하나"로 대응시킬 수 없다
     * (WEEKLY/MONTHLY는 여러 라운드에 걸쳐 더 심하다). 제출별 익명이 랭킹 이름에 반영되려면
     * ranking_snapshot 자체가 submission 단위로 재설계돼야 하므로 지금은 현행 유지 + 백로그.
     * (toggle API가 항상 nickname을 먼저 채운 뒤 anonymousDefault를 켜므로
     * isAnonymousDefault=true인데 anonymousNickname이 null인 상태는 정상 경로에서 발생하지 않는다.)
     */
    public static RankingEntryResponse from(RankingSnapshot snapshot) {
        User user = snapshot.getUser();
        String nickname = user.isAnonymousDefault() ? user.getAnonymousNickname() : user.getName();
        return new RankingEntryResponse(snapshot.getRank(), user.getId(), nickname, snapshot.getScore());
    }
}
