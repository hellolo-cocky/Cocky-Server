package com.cocky.cockyserver.domain.submission.dto;

import com.cocky.cockyserver.domain.problem.entity.Language;
import com.cocky.cockyserver.domain.submission.entity.Submission;
import com.cocky.cockyserver.domain.submission.entity.Verdict;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SubmissionSummaryResponse(
        Long submissionId, Long problemId, Language language, Verdict verdict, BigDecimal score,
        LocalDateTime submittedAt) {

    public static SubmissionSummaryResponse from(Submission submission) {
        return new SubmissionSummaryResponse(submission.getId(), submission.getProblem().getId(),
                submission.getLanguage(), submission.getVerdict(), submission.getScore(),
                submission.getSubmittedAt());
    }
}