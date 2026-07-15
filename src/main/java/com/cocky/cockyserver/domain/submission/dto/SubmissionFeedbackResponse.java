package com.cocky.cockyserver.domain.submission.dto;

import com.cocky.cockyserver.domain.submission.entity.Submission;
import java.math.BigDecimal;

public record SubmissionFeedbackResponse(
        BigDecimal timeComplexityScore, BigDecimal readabilityScore, BigDecimal originalityScore, String comment) {

    public static SubmissionFeedbackResponse from(Submission submission) {
        return new SubmissionFeedbackResponse(submission.getTimeScore(), submission.getReadabilityScore(),
                submission.getOriginalityScore(), submission.getFeedbackComment());
    }
}