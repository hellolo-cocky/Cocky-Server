package com.cocky.cockyserver.domain.ranking.scheduler;

import com.cocky.cockyserver.domain.ranking.service.RankingBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 회차 마감(화/목/토 23:59) 다음날 00:00(수/금/일)에 랭킹 스냅샷 배치를 실행한다. */
@Component
@RequiredArgsConstructor
public class RankingBatchScheduler {

    private final RankingBatchService rankingBatchService;

    @Scheduled(cron = "0 0 0 * * WED,FRI,SUN", zone = "Asia/Seoul")
    public void scheduledRankingSnapshot() {
        rankingBatchService.generateSnapshot();
    }
}
