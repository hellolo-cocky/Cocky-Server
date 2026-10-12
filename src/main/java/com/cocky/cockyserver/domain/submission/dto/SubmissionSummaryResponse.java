package com.cocky.cockyserver.domain.submission.dto;

import com.cocky.cockyserver.domain.problem.entity.Difficulty;
import com.cocky.cockyserver.domain.problem.entity.Language;
import com.cocky.cockyserver.domain.submission.entity.Submission;
import com.cocky.cockyserver.domain.submission.entity.Verdict;
import com.cocky.cockyserver.domain.submission.repository.SubmissionRepository.SubmissionHistoryRow;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** score는 최종 점수(채점 base + AI 가산점), aiScore는 그중 AI 가산점 합(피드백이 없으면 null). */
public record SubmissionSummaryResponse(
        Long submissionId, Long problemId, String problemTitle, Difficulty difficulty, Language language,
        Verdict verdict, BigDecimal score, BigDecimal aiScore, LocalDateTime submittedAt) {

    public static SubmissionSummaryResponse from(SubmissionHistoryRow row) {
        return new SubmissionSummaryResponse(row.getSubmissionId(), row.getProblemId(), row.getProblemTitle(),
                row.getDifficulty(), row.getLanguage(), row.getVerdict(), row.getScore(),
                Submission.sumAiScores(row.getTimeScore(), row.getReadabilityScore(), row.getOriginalityScore()),
                row.getSubmittedAt());
    }
}
