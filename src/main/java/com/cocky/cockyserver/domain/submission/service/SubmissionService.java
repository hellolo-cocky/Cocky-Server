package com.cocky.cockyserver.domain.submission.service;

import com.cocky.cockyserver.ai.dto.FeedbackItem;
import com.cocky.cockyserver.ai.dto.InstantFeedback;
import com.cocky.cockyserver.ai.port.InstantFeedbackFailedException;
import com.cocky.cockyserver.ai.port.InstantFeedbackProvider;
import com.cocky.cockyserver.domain.problem.entity.Difficulty;
import com.cocky.cockyserver.domain.problem.entity.Problem;
import com.cocky.cockyserver.domain.problem.entity.TestCase;
import com.cocky.cockyserver.domain.problem.exception.ProblemNotFoundException;
import com.cocky.cockyserver.domain.problem.repository.ProblemRepository;
import com.cocky.cockyserver.domain.problem.repository.TestCaseRepository;
import com.cocky.cockyserver.domain.round.entity.Round;
import com.cocky.cockyserver.domain.submission.dto.SubmissionDetailResponse;
import com.cocky.cockyserver.domain.submission.dto.SubmissionFeedbackResponse;
import com.cocky.cockyserver.domain.submission.dto.SubmissionRequest;
import com.cocky.cockyserver.domain.submission.dto.SubmissionResponse;
import com.cocky.cockyserver.domain.submission.dto.SubmissionSummaryResponse;
import com.cocky.cockyserver.domain.submission.entity.Submission;
import com.cocky.cockyserver.domain.submission.entity.Verdict;
import com.cocky.cockyserver.domain.submission.exception.LanguageMismatchException;
import com.cocky.cockyserver.domain.submission.exception.RoundClosedException;
import com.cocky.cockyserver.domain.submission.exception.SubmissionAccessDeniedException;
import com.cocky.cockyserver.domain.submission.exception.SubmissionNotFoundException;
import com.cocky.cockyserver.domain.submission.exception.TestCaseNotConfiguredException;
import com.cocky.cockyserver.domain.submission.judge.JudgeRequest;
import com.cocky.cockyserver.domain.submission.judge.JudgeResult;
import com.cocky.cockyserver.domain.submission.judge.JudgeService;
import com.cocky.cockyserver.domain.submission.judge.TestCaseIO;
import com.cocky.cockyserver.domain.submission.repository.SubmissionRepository;
import com.cocky.cockyserver.domain.user.entity.User;
import com.cocky.cockyserver.domain.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Judge0 동기 채점(wait=true)과 AI 즉시 피드백 호출은 모두 초 단위로 걸릴 수 있어
 * DB 트랜잭션 안에 두지 않는다 — 커넥션을 물고 외부 API를 기다리면 마감 직전 동시
 * 제출이 몰릴 때 커넥션 풀이 고갈된다. 그래서 검증/조회(짧은 읽기 트랜잭션) →
 * Judge0 채점(트랜잭션 밖) → AI 즉시 피드백(트랜잭션 밖) → is_latest 갱신·저장·
 * 피드백 반영(짧은 쓰기 트랜잭션) 순으로 쪼갠다.
 *
 * <p>같은 클래스의 메서드를 {@code @Transactional}로만 나누면 self-invocation 때문에
 * 프록시가 트랜잭션을 적용하지 못하므로, {@link TransactionTemplate}으로 명시적으로 경계를
 * 긋는다.
 */
@Service
public class SubmissionService {

    private static final Logger log = LoggerFactory.getLogger(SubmissionService.class);

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final JudgeService judgeService;
    private final InstantFeedbackProvider instantFeedbackProvider;
    private final Clock clock;
    private final TransactionTemplate readOnlyTransaction;
    private final TransactionTemplate writeTransaction;

    public SubmissionService(ProblemRepository problemRepository, TestCaseRepository testCaseRepository,
                             SubmissionRepository submissionRepository, UserRepository userRepository,
                             JudgeService judgeService, InstantFeedbackProvider instantFeedbackProvider,
                             Clock clock, PlatformTransactionManager transactionManager) {
        this.problemRepository = problemRepository;
        this.testCaseRepository = testCaseRepository;
        this.submissionRepository = submissionRepository;
        this.userRepository = userRepository;
        this.judgeService = judgeService;
        this.instantFeedbackProvider = instantFeedbackProvider;
        this.clock = clock;
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    public SubmissionResponse submit(Long userId, SubmissionRequest request) {
        Validated validated = readOnlyTransaction.execute(status -> validate(request));
        JudgeResult judgeResult = judgeService.judge(validated.judgeRequest());
        InstantFeedback feedback = evaluateFeedback(request, validated);
        return writeTransaction.execute(status -> persist(userId, request, validated, judgeResult, feedback));
    }

    /**
     * AI 즉시 피드백 호출. Judge0와 같은 이유로 트랜잭션 밖에서 수행 —
     * 외부 API 대기 중 DB 커넥션을 물고 있지 않는다. 모듈 내부 재시도가
     * 소진되어 {@link InstantFeedbackFailedException}이 나면 null로 흡수하고
     * 채점 결과 저장 자체는 막지 않는다.
     */
    private InstantFeedback evaluateFeedback(SubmissionRequest request, Validated validated) {
        com.cocky.cockyserver.ai.dto.Submission aiSubmission = new com.cocky.cockyserver.ai.dto.Submission(
                com.cocky.cockyserver.ai.dto.Language.valueOf(request.language().name()),
                com.cocky.cockyserver.ai.dto.Difficulty.valueOf(validated.difficulty().name()),
                validated.problemStatement(),
                request.code());
        try {
            return instantFeedbackProvider.evaluate(aiSubmission);
        } catch (InstantFeedbackFailedException e) {
            log.warn("AI 즉시 피드백 실패, 채점 결과만 저장: {}", e.getMessage());
            return null;
        }
    }

    /** 문제 조회·검증(404/400/403)과 테스트케이스 로딩까지만 담당 — 짧은 읽기 트랜잭션. */
    private Validated validate(SubmissionRequest request) {
        Problem problem = problemRepository.findById(request.problemId())
                .orElseThrow(() -> new ProblemNotFoundException(
                        "존재하지 않는 문제입니다. problemId=" + request.problemId()));

        if (request.language() != problem.getLanguage()) {
            throw new LanguageMismatchException(
                    "문제 언어(%s)와 제출 언어(%s)가 다릅니다.".formatted(problem.getLanguage(), request.language()));
        }

        Round round = problem.getRound();
        if (!round.isActive() || LocalDateTime.now(clock).isAfter(round.getCloseAt())) {
            throw new RoundClosedException("마감된 회차의 문제입니다. roundId=" + round.getId());
        }

        List<TestCaseIO> cases = testCaseRepository.findByProblemIdOrderByIdAsc(problem.getId()).stream()
                .map(this::toTestCaseIO)
                .toList();
        if (cases.isEmpty()) {
            throw new TestCaseNotConfiguredException("문제에 테스트케이스가 없습니다. problemId=" + problem.getId());
        }
        JudgeRequest judgeRequest = new JudgeRequest(request.language(), request.code(), cases);
        return new Validated(problem.getId(), problem.getDifficulty(), problem.getContent(), judgeRequest);
    }

    /** 채점 결과를 받은 뒤 점수 산정·is_latest 갱신·저장까지 — 짧은 쓰기 트랜잭션. */
    private SubmissionResponse persist(Long userId, SubmissionRequest request, Validated validated,
                                       JudgeResult judgeResult, InstantFeedback feedback) {
        BigDecimal baseScore = judgeResult.verdict() == Verdict.AC
                ? validated.difficulty().baseScore()
                : BigDecimal.ZERO.setScale(2);

        // 벌크 UPDATE라 영속성 컨텍스트를 거치지 않고 DB에 바로 반영된다. 이 트랜잭션
        // 안에서 기존 Submission을 다시 읽지 않고 새 엔티티만 insert하므로
        // clearAutomatically/flushAutomatically 없이도 순서가 어긋나지 않는다.
        submissionRepository.markPreviousNotLatest(userId, validated.problemId());

        User user = userRepository.getReferenceById(userId);
        Problem problem = problemRepository.getReferenceById(validated.problemId());
        Submission submission = new Submission(user, problem, request.language(), request.code());

        // 엔티티는 "이 점수로 세팅해"라는 최종 지시만 받는다 — base/피드백 가산점의 합산은
        // 서비스 책임이고, 엔티티는 그 산출 과정을 몰라도 된다.
        BigDecimal finalScore = feedback != null
                ? baseScore.add(applyFeedback(submission, judgeResult.verdict(), feedback))
                : baseScore;
        submission.updateResult(judgeResult.verdict(), finalScore);

        submissionRepository.save(submission);

        return SubmissionResponse.of(submission, judgeResult);
    }

    /**
     * WA일 땐 세 항목 모두 0점으로 가산점을 0에 묶어 두되, 코멘트는 그대로 노출한다.
     * AC일 땐 items를 category(enum)로 매칭해 순서에 의존하지 않고 세 점수를 뽑는다 —
     * evaluate()가 성공 리턴했는데도 셋 중 하나라도 빠졌다면 AI 모듈 계약 위반이므로
     * 조용히 넘기지 않고 즉시 실패시킨다.
     *
     * @return 세 항목 점수 합(피드백 가산점) — 최종 점수 계산에 쓰인다.
     */
    private BigDecimal applyFeedback(Submission submission, Verdict verdict, InstantFeedback feedback) {
        if (verdict != Verdict.AC) {
            BigDecimal zero = new BigDecimal("0.00");
            submission.applyFeedback(zero, zero, zero, feedback.personality());
            return zero;
        }

        BigDecimal timeScore = null;
        BigDecimal readabilityScore = null;
        BigDecimal originalityScore = null;
        for (FeedbackItem item : feedback.items()) {
            switch (item.category()) {
                case TIME -> timeScore = item.score();
                case READABILITY -> readabilityScore = item.score();
                case ORIGINALITY -> originalityScore = item.score();
            }
        }
        if (timeScore == null || readabilityScore == null || originalityScore == null) {
            throw new IllegalStateException(
                    "AI 피드백 항목이 3개(TIME/READABILITY/ORIGINALITY) 모두 있어야 합니다: " + feedback);
        }
        submission.applyFeedback(timeScore, readabilityScore, originalityScore, feedback.personality());
        return timeScore.add(readabilityScore).add(originalityScore);
    }

    public Page<SubmissionSummaryResponse> getMySubmissions(Long userId, Pageable pageable) {
        return submissionRepository.findByUserId(userId, pageable).map(SubmissionSummaryResponse::from);
    }

    public SubmissionDetailResponse getSubmissionDetail(Long userId, Long submissionId) {
        return SubmissionDetailResponse.from(findOwnedSubmission(userId, submissionId));
    }

    /** 세 점수 컬럼이 하나라도 null이면 AI 피드백이 아직 준비되지 않은 것으로 보고 비운다 — 컨트롤러가 202로 처리. */
    public Optional<SubmissionFeedbackResponse> getFeedback(Long userId, Long submissionId) {
        Submission submission = findOwnedSubmission(userId, submissionId);
        if (submission.getTimeScore() == null || submission.getReadabilityScore() == null
                || submission.getOriginalityScore() == null) {
            return Optional.empty();
        }
        return Optional.of(SubmissionFeedbackResponse.from(submission));
    }

    private Submission findOwnedSubmission(Long userId, Long submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new SubmissionNotFoundException(
                        "존재하지 않는 제출입니다. submissionId=" + submissionId));
        if (!submission.getUser().getId().equals(userId)) {
            throw new SubmissionAccessDeniedException("본인의 제출만 조회할 수 있습니다. submissionId=" + submissionId);
        }
        return submission;
    }

    private TestCaseIO toTestCaseIO(TestCase testCase) {
        return new TestCaseIO(testCase.getInput(), testCase.getExpectedOutput());
    }

    private record Validated(Long problemId, Difficulty difficulty, String problemStatement,
                             JudgeRequest judgeRequest) {
    }
}
