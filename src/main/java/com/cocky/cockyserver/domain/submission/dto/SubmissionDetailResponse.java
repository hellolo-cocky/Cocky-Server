package com.cocky.cockyserver.domain.submission.dto;

import com.cocky.cockyserver.domain.problem.entity.Difficulty;
import com.cocky.cockyserver.domain.problem.entity.Language;
import com.cocky.cockyserver.domain.submission.entity.Submission;
import com.cocky.cockyserver.domain.submission.entity.Verdict;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** problem이 fetch join 되어 있어야 한다({@code SubmissionRepository.findDetailByIdAndUserId}). */
public record SubmissionDetailResponse(
        Long submissionId, Long problemId, String problemTitle, Difficulty difficulty, Language language,
        String code, Verdict verdict, BigDecimal score, BigDecimal aiScore,
        BigDecimal timeComplexityScore, BigDecimal readabilityScore, BigDecimal originalityScore, String comment,
        LocalDateTime submittedAt) {

    public static SubmissionDetailResponse from(Submission submission) {
        return new SubmissionDetailResponse(submission.getId(), submission.getProblem().getId(),
                submission.getProblem().getTitle(), submission.getProblem().getDifficulty(),
                submission.getLanguage(), submission.getCode(), submission.getVerdict(), submission.getScore(),
                submission.aiScore(), submission.getTimeScore(), submission.getReadabilityScore(),
                submission.getOriginalityScore(), submission.getFeedbackComment(), submission.getSubmittedAt());
    }
}
