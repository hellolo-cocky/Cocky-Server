package com.cocky.cockyserver.domain.ranking.controller;

import com.cocky.cockyserver.domain.ranking.dto.RankingListResponse;
import com.cocky.cockyserver.domain.ranking.entity.ScopeType;
import com.cocky.cockyserver.domain.ranking.service.RankingQueryService;
import com.cocky.cockyserver.global.entity.PeriodType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rankings")
@RequiredArgsConstructor
public class RankingController {

    private final RankingQueryService rankingQueryService;

    @GetMapping
    public ResponseEntity<RankingListResponse> getRanking(
            @RequestParam PeriodType period, @RequestParam ScopeType scope) {
        return ResponseEntity.ok(new RankingListResponse(rankingQueryService.getRanking(period, scope)));
    }
}
