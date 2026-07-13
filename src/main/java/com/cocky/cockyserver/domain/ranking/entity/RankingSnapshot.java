package com.cocky.cockyserver.domain.ranking.entity;

import com.cocky.cockyserver.global.entity.PeriodType;
import com.cocky.cockyserver.domain.round.entity.Round;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ranking_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RankingSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** TWO_DAY 스냅샷이 집계 대상으로 삼은 마감 라운드. WEEKLY/MONTHLY는 특정 라운드에 묶이지 않으므로 nullable. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id")
    private Round round;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 10)
    private PeriodType periodType;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 20)
    private ScopeType scopeType;

    /** WEEKLY/MONTHLY 집계 기간의 시작일. TWO_DAY는 라운드 단위라 nullable. */
    @Column(name = "period_start")
    private LocalDate periodStart;

    /** WEEKLY/MONTHLY 집계 기간의 종료일. TWO_DAY는 라운드 단위라 nullable. */
    @Column(name = "period_end")
    private LocalDate periodEnd;

    /**
     * round_id 대신 유니크 제약에 쓰이는 절대 NULL 아닌 식별자. TWO_DAY는 round.getId()
     * 문자열, WEEKLY/MONTHLY는 기간 시작일(월요일/1일)의 ISO 문자열.
     */
    @Column(name = "period_key", nullable = false, length = 20)
    private String periodKey;

    @Column(name = "`rank`", nullable = false)
    private Integer rank;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "calculated_at", nullable = false, updatable = false)
    private LocalDateTime calculatedAt;

    public RankingSnapshot(User user, Round round, PeriodType periodType, ScopeType scopeType,
                            LocalDate periodStart, LocalDate periodEnd, String periodKey, Integer rank,
                            BigDecimal score, LocalDateTime calculatedAt) {
        this.user = user;
        this.round = round;
        this.periodType = periodType;
        this.scopeType = scopeType;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.periodKey = periodKey;
        this.rank = rank;
        this.score = score;
        this.calculatedAt = calculatedAt;
    }
}
