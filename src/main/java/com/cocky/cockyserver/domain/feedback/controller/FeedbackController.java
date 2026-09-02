package com.cocky.cockyserver.domain.feedback.controller;

import com.cocky.cockyserver.ai.dto.Period;
import com.cocky.cockyserver.domain.feedback.dto.PeriodFeedbackResponse;
import com.cocky.cockyserver.domain.feedback.service.FeedbackService;
import com.cocky.cockyserver.global.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @GetMapping("/periodic")
    public ResponseEntity<PeriodFeedbackResponse> getPeriodicFeedback(
            @AuthenticationPrincipal UserPrincipal principal, @RequestParam Period period) {
        return ResponseEntity.ok(PeriodFeedbackResponse.from(
                feedbackService.getPeriodicFeedbackWithStats(principal.userId(), period)));
    }
}
