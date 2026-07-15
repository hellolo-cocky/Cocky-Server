package com.cocky.cockyserver.domain.submission.controller;

import com.cocky.cockyserver.domain.submission.dto.SubmissionDetailResponse;
import com.cocky.cockyserver.domain.submission.dto.SubmissionFeedbackResponse;
import com.cocky.cockyserver.domain.submission.dto.SubmissionRequest;
import com.cocky.cockyserver.domain.submission.dto.SubmissionResponse;
import com.cocky.cockyserver.domain.submission.dto.SubmissionSummaryResponse;
import com.cocky.cockyserver.domain.submission.service.SubmissionService;
import com.cocky.cockyserver.global.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @PostMapping
    public ResponseEntity<SubmissionResponse> submit(@AuthenticationPrincipal UserPrincipal principal,
                                                      @Valid @RequestBody SubmissionRequest request) {
        return ResponseEntity.ok(submissionService.submit(principal.userId(), request));
    }

    @GetMapping("/me")
    public ResponseEntity<Page<SubmissionSummaryResponse>> getMySubmissions(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(submissionService.getMySubmissions(principal.userId(), pageable));
    }

    @GetMapping("/{submissionId}")
    public ResponseEntity<SubmissionDetailResponse> getSubmission(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long submissionId) {
        return ResponseEntity.ok(submissionService.getSubmissionDetail(principal.userId(), submissionId));
    }

    @GetMapping("/{submissionId}/feedback")
    public ResponseEntity<SubmissionFeedbackResponse> getFeedback(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long submissionId) {
        return submissionService.getFeedback(principal.userId(), submissionId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.accepted().build());
    }
}
