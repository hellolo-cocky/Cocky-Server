package com.cocky.cockyserver.domain.ranking.scheduler;

import com.cocky.cockyserver.domain.ranking.service.RankingBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 매월 1일 00:00에 지난달 MONTHLY 랭킹 스냅샷 배치를 실행한다. */
@Component
@RequiredArgsConstructor
public class MonthlyRankingBatchScheduler {

    private final RankingBatchService rankingBatchService;

    @Scheduled(cron = "0 0 0 1 * *", zone = "Asia/Seoul")
    public void scheduledMonthlyRankingSnapshot() {
        rankingBatchService.generateMonthlySnapshot();
    }
}
