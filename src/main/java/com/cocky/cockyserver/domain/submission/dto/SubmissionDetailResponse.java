package com.cocky.cockyserver.domain.submission.dto;

import com.cocky.cockyserver.domain.problem.entity.Language;
import com.cocky.cockyserver.domain.submission.entity.Submission;
import com.cocky.cockyserver.domain.submission.entity.Verdict;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SubmissionDetailResponse(
        Long submissionId, Long problemId, Language language, String code, Verdict verdict, BigDecimal score,
        BigDecimal timeComplexityScore, BigDecimal readabilityScore, BigDecimal originalityScore, String comment,
        LocalDateTime submittedAt) {

    public static SubmissionDetailResponse from(Submission submission) {
        return new SubmissionDetailResponse(submission.getId(), submission.getProblem().getId(),
                submission.getLanguage(), submission.getCode(), submission.getVerdict(), submission.getScore(),
                submission.getTimeScore(), submission.getReadabilityScore(), submission.getOriginalityScore(),
                submission.getFeedbackComment(), submission.getSubmittedAt());
    }
}