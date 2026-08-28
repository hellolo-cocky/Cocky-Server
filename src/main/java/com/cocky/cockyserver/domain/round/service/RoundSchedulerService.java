package com.cocky.cockyserver.domain.round.service;

import com.cocky.cockyserver.ai.dto.ExampleIo;
import com.cocky.cockyserver.ai.dto.GeneratedProblem;
import com.cocky.cockyserver.ai.dto.GenerationItem;
import com.cocky.cockyserver.ai.dto.GenerationOutcome;
import com.cocky.cockyserver.ai.dto.GenerationRequest;
import com.cocky.cockyserver.ai.port.ProblemGenerator;
import com.cocky.cockyserver.domain.admin.entity.AiGenerationLog;
import com.cocky.cockyserver.domain.admin.repository.AiGenerationLogRepository;
import com.cocky.cockyserver.domain.problem.entity.Difficulty;
import com.cocky.cockyserver.domain.problem.entity.Language;
import com.cocky.cockyserver.domain.problem.entity.Problem;
import com.cocky.cockyserver.domain.problem.entity.TestCase;
import com.cocky.cockyserver.domain.problem.repository.ProblemRepository;
import com.cocky.cockyserver.domain.problem.repository.TestCaseRepository;
import com.cocky.cockyserver.domain.round.RoundSubtypes;
import com.cocky.cockyserver.domain.round.dto.RoundGenerationResult;
import com.cocky.cockyserver.domain.round.entity.Round;
import com.cocky.cockyserver.domain.round.repository.RoundRepository;
import com.cocky.cockyserver.domain.topic.entity.Topic;
import com.cocky.cockyserver.domain.topic.repository.TopicRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 매일 23시, 다음 날 회차 + 문제 9개를 자동 생성한다.
 *
 * <p>AI 생성 호출({@link ProblemGenerator#generate})은 초 단위로 걸릴 수 있어 DB
 * 트랜잭션 밖에서 수행한다 — {@code SubmissionService}와 같은 원칙(트랜잭션 안에서
 * 외부 API를 기다리면 커넥션 풀이 고갈된다). 다음 회차 계획 수립(짧은 읽기 트랜잭션)
 * → AI 호출(트랜잭션 밖) → 회차/문제/로그 저장(짧은 쓰기 트랜잭션) 순으로 쪼갠다.
 */
@Service
public class RoundSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(RoundSchedulerService.class);
    private static final int TOTAL_TOPICS = 8;

    private final RoundRepository roundRepository;
    private final TopicRepository topicRepository;
    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;
    private final AiGenerationLogRepository aiGenerationLogRepository;
    private final ProblemGenerator problemGenerator;
    private final Clock clock;
    private final TransactionTemplate readOnlyTransaction;
    private final TransactionTemplate writeTransaction;

    public RoundSchedulerService(RoundRepository roundRepository, TopicRepository topicRepository,
                                 ProblemRepository problemRepository, TestCaseRepository testCaseRepository,
                                 AiGenerationLogRepository aiGenerationLogRepository,
                                 ProblemGenerator problemGenerator, Clock clock,
                                 PlatformTransactionManager transactionManager) {
        this.roundRepository = roundRepository;
        this.topicRepository = topicRepository;
        this.problemRepository = problemRepository;
        this.testCaseRepository = testCaseRepository;
        this.aiGenerationLogRepository = aiGenerationLogRepository;
        this.problemGenerator = problemGenerator;
        this.clock = clock;
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    @Scheduled(cron = "0 0 23 * * *", zone = "Asia/Seoul")
    public void scheduledRoundGeneration() {
        triggerRoundGeneration();
    }

    /**
     * 실제 회차 생성 로직. 크론뿐 아니라 관리자 트리거 API·단위 테스트에서도 직접 호출한다.
     */
    public RoundGenerationResult triggerRoundGeneration() {
        LocalDate targetDate = LocalDate.now(clock).plusDays(1);
        if (targetDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            log.info("스케줄러: {}는 일요일 — 출제 스킵", targetDate);
            return RoundGenerationResult.skipped(targetDate, "일요일은 출제/제출이 비활성입니다.");
        }

        Plan plan = readOnlyTransaction.execute(status -> planNextRound(targetDate));
        if (plan == null) {
            return RoundGenerationResult.skipped(targetDate, "해당 날짜 라운드가 이미 존재합니다.");
        }

        GenerationRequest request = GenerationRequest.fullRound(
                plan.topic().getName(), plan.roundSubtype(), plan.pastTypes(), plan.pastStatements());

        GenerationOutcome outcome;
        try {
            outcome = problemGenerator.generate(request);
        } catch (RuntimeException e) {
            // ai.port.ProblemGenerator 계약상 조합 단위 실패는 예외 없이
            // GenerationOutcome.failures()로 흡수된다 — 여기까지 예외가 올라오면
            // 계약 밖의 상황(AI 모듈 버그 등)이므로 회차 생성을 중단하고 바로 드러낸다.
            log.error("스케줄러: 문제 생성기 호출 자체가 실패 — {} 회차 생성 중단", targetDate, e);
            return RoundGenerationResult.skipped(targetDate, "문제 생성기 호출 실패: " + e.getMessage());
        }

        return writeTransaction.execute(status -> persist(targetDate, plan, outcome));
    }

    /** 오늘 라운드 중복 여부 확인, 다음 주제 결정, pastTypes/pastStatements 수집 — 짧은 읽기 트랜잭션. */
    private Plan planNextRound(LocalDate targetDate) {
        if (roundRepository.findByRoundDate(targetDate).isPresent()) {
            log.info("스케줄러: {} 라운드가 이미 존재 — 스킵", targetDate);
            return null;
        }

        int nextWeekOrder = roundRepository.findTopByOrderByRoundDateDesc()
                .map(prev -> nextWeekOrder(prev.getTopic().getWeekOrder()))
                .orElse(1);
        Topic topic = topicRepository.findByWeekOrder(nextWeekOrder)
                .orElseThrow(() -> new IllegalStateException(
                        "week_order=" + nextWeekOrder + "인 topic이 없습니다 — V5 시드 확인 필요"));

        List<String> pastTypes = aiGenerationLogRepository.findTop30BySubtypeIsNotNullOrderByCreatedAtDesc().stream()
                .map(AiGenerationLog::getSubtype)
                .toList();
        List<String> pastStatements = problemRepository.findTop20ByOrderByCreatedAtDesc().stream()
                .map(Problem::getContent)
                .toList();

        String roundSubtype = pickRoundSubtype(topic.getName(), pastTypes);
        return new Plan(topic, roundSubtype, pastTypes, pastStatements);
    }

    private String pickRoundSubtype(String topicName, List<String> pastTypes) {
        List<String> candidates = RoundSubtypes.CANDIDATES.get(topicName);
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("topic '" + topicName + "'에 대한 roundSubtype 후보가 없습니다");
        }
        List<String> fresh = candidates.stream()
                .filter(candidate -> !pastTypes.contains(candidate))
                .toList();
        List<String> pool = fresh.isEmpty() ? candidates : fresh;
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    /** 회차 생성 + 문제/테스트케이스/생성로그 저장 — 짧은 쓰기 트랜잭션. */
    private RoundGenerationResult persist(LocalDate targetDate, Plan plan, GenerationOutcome outcome) {
        List<GenerationItem> items = outcome.items();
        if (items.stream().noneMatch(GenerationItem::success)) {
            log.error("스케줄러: {} 회차 문제 생성 전체 실패 — 회차를 생성하지 않음", targetDate);
            return RoundGenerationResult.skipped(targetDate, "전체 문제 생성 실패");
        }

        LocalDateTime openAt = targetDate.atStartOfDay();
        LocalDateTime closeAt = targetDate.atTime(23, 59, 59);
        Round round = new Round(plan.topic(), targetDate, openAt, closeAt);
        round.activate();
        try {
            roundRepository.save(round);
        } catch (DataIntegrityViolationException e) {
            log.info("스케줄러: {} 라운드 저장 중 유니크 제약 위반 — 동시 실행으로 이미 생성됨", targetDate);
            return RoundGenerationResult.skipped(targetDate, "동시 실행으로 라운드가 이미 생성됨");
        }

        List<GenerationItem> failures = new ArrayList<>();
        int successCount = 0;
        for (int i = 0; i < items.size(); i++) {
            GenerationItem item = items.get(i);
            int sequenceNo = i + 1;
            if (item.success()) {
                saveSuccess(round, sequenceNo, item);
                successCount++;
            } else {
                failures.add(item);
                aiGenerationLogRepository.save(
                        AiGenerationLog.failure(round, sequenceNo, item.failReason(), item.attempts()));
            }
        }

        if (!failures.isEmpty()) {
            log.error("스케줄러: {} 회차 문제 생성 중 {}건 실패 - {}", targetDate, failures.size(),
                    failures.stream()
                            .map(f -> "%s/%s: %s".formatted(f.language(), f.difficulty(), f.failReason()))
                            .toList());
        }

        return RoundGenerationResult.completed(targetDate, successCount, failures.size());
    }

    private void saveSuccess(Round round, int sequenceNo, GenerationItem item) {
        GeneratedProblem generated = item.problem();
        Problem problem = new Problem(round, generated.title(), generated.statement(),
                Language.valueOf(generated.language().name()),
                Difficulty.valueOf(generated.difficulty().name()),
                true);
        problemRepository.save(problem);

        List<ExampleIo> examples = generated.examples();
        for (int i = 0; i < examples.size(); i++) {
            ExampleIo example = examples.get(i);
            testCaseRepository.save(new TestCase(problem, example.input(), example.output(), i == 0));
        }

        aiGenerationLogRepository.save(
                AiGenerationLog.success(round, problem, sequenceNo, generated.subtype(), item.attempts()));
    }

    /**
     * 다음 주차 계산: 현재 weekOrder(1~8) 기준 다음 주차. 8주 순환이라 8 다음은 1로 돌아간다.
     * 회차 스케줄러와 기간 피드백 통계 집계({@code FeedbackService})가 함께 쓰는 단일 계산식이라
     * static으로 꺼내 두었다 — 새 회차를 실제로 만들지 않고 "다음 주제가 뭔지"만 알고 싶을 때 재사용한다.
     */
    public static int nextWeekOrder(int currentWeekOrder) {
        return (currentWeekOrder % TOTAL_TOPICS) + 1;
    }

    private record Plan(Topic topic, String roundSubtype, List<String> pastTypes, List<String> pastStatements) {
    }
}
