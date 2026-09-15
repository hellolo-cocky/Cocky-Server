package com.cocky.cockyserver.domain.submission.entity;

import com.cocky.cockyserver.domain.problem.entity.Language;
import com.cocky.cockyserver.domain.problem.entity.Problem;
import com.cocky.cockyserver.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Language language;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Verdict verdict;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "time_score", precision = 5, scale = 2)
    private BigDecimal timeScore;

    @Column(name = "readability_score", precision = 5, scale = 2)
    private BigDecimal readabilityScore;

    @Column(name = "originality_score", precision = 5, scale = 2)
    private BigDecimal originalityScore;

    @Lob
    @Column(name = "feedback_comment", columnDefinition = "TEXT")
    private String feedbackComment;

    @Column(name = "is_latest", nullable = false)
    private boolean latest;

    /**
     * 제출 단위 익명 덮어쓰기. 요청에 isAnonymous가 없으면 서비스 계층이 제출 시점
     * user.isAnonymousDefault() 값을 그대로 넘겨서 저장한다 — 여기서는 결정된 최종 값만 받는다.
     */
    @Column(name = "is_anonymous", nullable = false)
    private boolean anonymous;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    public Submission(User user, Problem problem, Language language, String code, boolean anonymous) {
        this.user = user;
        this.problem = problem;
        this.language = language;
        this.code = code;
        this.verdict = Verdict.PENDING;
        this.score = BigDecimal.ZERO.setScale(2);
        this.latest = true;
        this.anonymous = anonymous;
        this.submittedAt = LocalDateTime.now();
    }

    public void updateResult(Verdict verdict, BigDecimal score) {
        this.verdict = verdict;
        this.score = score;
    }

    public void applyFeedback(BigDecimal timeScore, BigDecimal readabilityScore,
                               BigDecimal originalityScore, String comment) {
        this.timeScore = timeScore;
        this.readabilityScore = readabilityScore;
        this.originalityScore = originalityScore;
        this.feedbackComment = comment;
    }
}
