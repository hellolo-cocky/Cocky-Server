package com.cocky.cockyserver.domain.admin.controller;

import com.cocky.cockyserver.domain.ranking.dto.RankingSnapshotResult;
import com.cocky.cockyserver.domain.ranking.service.RankingBatchService;
import com.cocky.cockyserver.domain.round.dto.RoundGenerationResult;
import com.cocky.cockyserver.domain.round.service.RoundSchedulerService;
import com.cocky.cockyserver.global.entity.PeriodType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ROLE_ADMIN 전용 — 접근 제어는 SecurityConfig의 "/api/v1/admin/**" 매처가 담당한다. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final RoundSchedulerService roundSchedulerService;
    private final RankingBatchService rankingBatchService;

    @PostMapping("/scheduler/round-generation")
    public ResponseEntity<RoundGenerationResult> triggerRoundGeneration() {
        return ResponseEntity.ok(roundSchedulerService.triggerRoundGeneration());
    }

    @PostMapping("/scheduler/ranking-snapshot")
    public ResponseEntity<RankingSnapshotResult> triggerRankingSnapshot(
            @RequestParam(defaultValue = "TWO_DAY") PeriodType period) {
        RankingSnapshotResult result = switch (period) {
            case TWO_DAY -> rankingBatchService.generateTwoDaySnapshot();
            case WEEKLY -> rankingBatchService.generateWeeklySnapshot();
            case MONTHLY -> rankingBatchService.generateMonthlySnapshot();
        };
        return ResponseEntity.ok(result);
    }
}
