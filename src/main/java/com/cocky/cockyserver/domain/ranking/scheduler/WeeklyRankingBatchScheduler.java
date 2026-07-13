package com.cocky.cockyserver.domain.ranking.scheduler;

import com.cocky.cockyserver.domain.ranking.service.RankingBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 일요일 00:00에 그 주(월~토) WEEKLY 랭킹 스냅샷 배치를 실행한다. */
@Component
@RequiredArgsConstructor
public class WeeklyRankingBatchScheduler {

    private final RankingBatchService rankingBatchService;

    @Scheduled(cron = "0 0 0 * * SUN", zone = "Asia/Seoul")
    public void scheduledWeeklyRankingSnapshot() {
        rankingBatchService.generateWeeklySnapshot();
    }
}
