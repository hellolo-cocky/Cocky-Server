package com.cocky.cockyserver.domain.ranking.dto;

import com.cocky.cockyserver.domain.ranking.entity.RankingSnapshot;
import java.math.BigDecimal;

public record RankingEntryResponse(Integer rank, Long userId, String userName, BigDecimal score) {

    public static RankingEntryResponse from(RankingSnapshot snapshot) {
        return new RankingEntryResponse(
                snapshot.getRank(), snapshot.getUser().getId(), snapshot.getUser().getName(), snapshot.getScore());
    }
}
