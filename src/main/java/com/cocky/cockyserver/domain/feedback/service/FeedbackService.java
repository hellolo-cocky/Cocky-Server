package com.cocky.cockyserver.domain.feedback.service;

import com.cocky.cockyserver.ai.dto.Difficulty;
import com.cocky.cockyserver.ai.dto.Language;
import com.cocky.cockyserver.ai.dto.Period;
import com.cocky.cockyserver.ai.dto.PeriodFeedback;
import com.cocky.cockyserver.ai.dto.PeriodStats;
import com.cocky.cockyserver.ai.port.PeriodFeedbackProvider;
import com.cocky.cockyserver.domain.round.TopicRotationPolicy;
import com.cocky.cockyserver.domain.round.repository.RoundRepository;
import com.cocky.cockyserver.domain.submission.repository.SubmissionRepository;
import com.cocky.cockyserver.domain.topic.entity.Topic;
import com.cocky.cockyserver.domain.topic.repository.TopicRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 기간(회차/주간/월간) 총평 진입점. 통계는 여기서 DB로 집계해 {@link PeriodStats}로 채운 뒤
 * {@link PeriodFeedbackProvider}(ai.port)에만 넘긴다 — ai.service/ai.demo 구현체는 AiConfig가
 * 빈으로 무엇을 주입했는지에 따라 갈리므로 이 클래스는 절대 알 필요가 없다.
 *
 * <p>⚠️ 채점 아키텍처와 마찬가지로 AI 모듈도 포트 인터페이스 뒤로 숨기는 원칙이 있다
 * (CLAUDE.md 8.5절과 같은 취지). {@link PeriodFeedbackProvider}는
 * {@link com.cocky.cockyserver.ai.port.InstantFeedbackFailedException}같은 전용 실패 계약
 * 예외가 아직 없다 — summarize()가 내부적으로 IllegalStateException을 던질 수 있는데(JSON
 * 파싱 실패 등), 지금은 그 예외를 여기서 캐치하지 않고 그대로 흘려보낸다. 즉시 피드백 쪽처럼
 * "실패해도 나머지는 저장" 폴백이 필요하면 ai.port에 전용 예외를 먼저 추가해야 한다.
 */
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final SubmissionRepository submissionRepository;
    private final RoundRepository roundRepository;
    private final TopicRepository topicRepository;
    private final PeriodFeedbackProvider periodFeedbackProvider;
    private final Clock clock;

    /** {@link PeriodStats}를 집계해 AI 포트에 넘기고 총평을 받아온다 — 이 메서드가 조립 진입점이다. */
    public PeriodFeedback getPeriodicFeedback(Long userId, Period period) {
        PeriodStats stats = aggregateStats(userId, period);
        return periodFeedbackProvider.summarize(period, stats);
    }

    /** {@link PeriodFeedbackProvider#summarize} 호출에 필요한 4개 필드를 DB에서 집계한다. */
    public PeriodStats aggregateStats(Long userId, Period period) {
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<PeriodWindow> window = resolveWindow(period, now);
        if (window.isEmpty()) {
            // ROUND인데 아직 마감된 회차가 하나도 없는 경우(서비스 극초반) — 빈 통계로 응답한다.
            return new PeriodStats(Map.of(), Map.of(), Map.of(), null);
        }

        LocalDateTime start = window.get().start();
        LocalDateTime end = window.get().end();

        Map<Language, Integer> languageCounts = new EnumMap<>(Language.class);
        submissionRepository.aggregateLanguageCountsByUserAndPeriod(userId, start, end)
                .forEach(row -> languageCounts.put(toAiLanguage(row.getLanguage()), row.getCount().intValue()));

        Map<Difficulty, Integer> difficultyCounts = new EnumMap<>(Difficulty.class);
        submissionRepository.aggregateDifficultyCountsByUserAndPeriod(userId, start, end)
                .forEach(row -> difficultyCounts.put(toAiDifficulty(row.getDifficulty()), row.getCount().intValue()));

        Map<String, Integer> wrongTypeCounts = new LinkedHashMap<>();
        submissionRepository.aggregateWrongVerdictCountsByUserAndPeriod(userId, start, end)
                .forEach(row -> wrongTypeCounts.put(row.getVerdict().name(), row.getCount().intValue()));

        String nextTopic = resolveNextTopic(period, now);

        return new PeriodStats(languageCounts, difficultyCounts, wrongTypeCounts, nextTopic);
    }

    /**
     * 기간별 시작/종료 시각. ROUND는 가장 최근에 마감된 회차의 open~close, MONTHLY는
     * {@code RankingBatchService}가 스냅샷 배치에 쓰는 것과 같은 관례(지난달 1일~말일)를 그대로
     * 따른다.
     *
     * <p>WEEKLY는 {@code RankingBatchService}와 다르다 — 배치는 "트리거가 일요일 00시"라는
     * 전제로 now-6일/now-1일을 그냥 뺐지만, 여기는 학생이 아무 요일에나 온디맨드로 호출한다.
     * 그 전제를 그대로 가져오면 수요일에 호출했을 때 목~화(미래 포함) 구간이 잡히는 버그가
     * 났다. 그래서 "오늘 이전(당일 제외)의 가장 최근 토요일"을 먼저 찾고 거기서 6일을
     * 빼는 방식으로 요일에 무관하게 항상 "가장 최근에 끝난 월~토"를 잡는다. 토요일 당일에
     * 호출해도 그날은 아직 안 끝난 주이므로 previousOrSame이 아니라 previous를 써서 지난주로
     * 밀어낸다.
     */
    private Optional<PeriodWindow> resolveWindow(Period period, LocalDateTime now) {
        return switch (period) {
            case ROUND -> roundRepository.findTopByCloseAtLessThanEqualOrderByCloseAtDesc(now)
                    .map(r -> new PeriodWindow(r.getOpenAt(), r.getCloseAt()));
            case WEEKLY -> {
                LocalDate saturday = now.toLocalDate().with(TemporalAdjusters.previous(DayOfWeek.SATURDAY));
                LocalDate monday = saturday.minusDays(5);
                yield Optional.of(new PeriodWindow(monday.atStartOfDay(), saturday.plusDays(1).atStartOfDay()));
            }
            case MONTHLY -> {
                LocalDate firstDay = now.toLocalDate().minusMonths(1).withDayOfMonth(1);
                LocalDate lastDay = now.toLocalDate().withDayOfMonth(1).minusDays(1);
                yield Optional.of(new PeriodWindow(firstDay.atStartOfDay(), lastDay.plusDays(1).atStartOfDay()));
            }
        };
    }

    /**
     * 다음 기간 대주제. ROUND는 예습 추천이 없어(Period 문서 참고) null로 비워 불필요한 조회를
     * 생략한다. WEEKLY/MONTHLY는 가장 최근에 "마감된" 라운드의 주제 topicOrder를 기준으로
     * {@link TopicRotationPolicy#next}(회차 스케줄러와 공유하는 도메인 규칙)로 다음 순번을 구한 뒤
     * topic 이름을 찾는다.
     *
     * <p>{@code findTopByOrderByRoundDateDesc}(마감 여부 무관, 단순 최신)가 아니라
     * {@code findTopByCloseAtLessThanEqualOrderByCloseAtDesc}를 쓴다 — {@code resolveWindow}의
     * ROUND 분기, 랭킹 배치와 같은 기준이다. {@code RoundSchedulerService}가 23시에 익일 라운드를
     * {@code active=true}로 미리 만들어 두므로, 마감 여부를 안 보면 23시 이후엔 아직 시작도
     * 안 한 다음 라운드가 "최신"으로 잡혀 다음 주제가 한 주 더 밀리는 버그가 있었다.
     */
    private String resolveNextTopic(Period period, LocalDateTime now) {
        if (period == Period.ROUND) {
            return null;
        }
        return roundRepository.findTopByCloseAtLessThanEqualOrderByCloseAtDesc(now)
                .map(latest -> TopicRotationPolicy.next(latest.getTopic().getTopicOrder()))
                .flatMap(topicRepository::findByTopicOrder)
                .map(Topic::getName)
                .orElse(null);
    }

    /**
     * domain.problem.entity.Language → ai.dto.Language(AI 모듈 계약, judge0Id 보유) 변환.
     * 두 enum은 의도적으로 분리된 타입이라 통합하지 않는다 — {@code valueOf(name())} 대신 switch로
     * 명시 매핑해서, 도메인에 새 언어가 추가돼도(default 없음) 여기서 컴파일 에러로 바로 드러나게 한다.
     */
    private static Language toAiLanguage(com.cocky.cockyserver.domain.problem.entity.Language domainLanguage) {
        return switch (domainLanguage) {
            case PYTHON -> Language.PYTHON;
            case C -> Language.C;
            case JAVA -> Language.JAVA;
        };
    }

    /** {@link #toAiLanguage}와 같은 이유로 switch 명시 매핑 — domain.problem.entity.Difficulty → ai.dto.Difficulty. */
    private static Difficulty toAiDifficulty(com.cocky.cockyserver.domain.problem.entity.Difficulty domainDifficulty) {
        return switch (domainDifficulty) {
            case EASY -> Difficulty.EASY;
            case NORMAL -> Difficulty.NORMAL;
            case HARD -> Difficulty.HARD;
        };
    }

    private record PeriodWindow(LocalDateTime start, LocalDateTime end) {
    }
}
