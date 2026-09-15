package com.cocky.cockyserver.domain.submission.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cocky.cockyserver.ai.port.InstantFeedbackProvider;
import com.cocky.cockyserver.domain.problem.entity.Difficulty;
import com.cocky.cockyserver.domain.problem.entity.Language;
import com.cocky.cockyserver.domain.problem.entity.Problem;
import com.cocky.cockyserver.domain.problem.entity.TestCase;
import com.cocky.cockyserver.domain.problem.exception.ProblemNotFoundException;
import com.cocky.cockyserver.domain.problem.repository.ProblemRepository;
import com.cocky.cockyserver.domain.problem.repository.TestCaseRepository;
import com.cocky.cockyserver.domain.round.entity.Round;
import com.cocky.cockyserver.domain.submission.dto.SubmissionRequest;
import com.cocky.cockyserver.domain.submission.dto.SubmissionResponse;
import com.cocky.cockyserver.domain.submission.entity.Verdict;
import com.cocky.cockyserver.domain.submission.exception.LanguageMismatchException;
import com.cocky.cockyserver.domain.submission.exception.RoundClosedException;
import com.cocky.cockyserver.domain.submission.exception.TestCaseNotConfiguredException;
import com.cocky.cockyserver.domain.submission.judge.JudgeResult;
import com.cocky.cockyserver.domain.submission.judge.JudgeService;
import com.cocky.cockyserver.domain.submission.entity.Submission;
import com.cocky.cockyserver.domain.submission.repository.SubmissionRepository;
import com.cocky.cockyserver.domain.topic.entity.Topic;
import com.cocky.cockyserver.domain.user.entity.Role;
import com.cocky.cockyserver.domain.user.entity.User;
import com.cocky.cockyserver.domain.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long PROBLEM_ID = 10L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 8, 10, 0);

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private TestCaseRepository testCaseRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JudgeService judgeService;

    @Mock
    private InstantFeedbackProvider instantFeedbackProvider;

    @Mock
    private PlatformTransactionManager transactionManager;

    private SubmissionService submissionService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        submissionService = new SubmissionService(problemRepository, testCaseRepository, submissionRepository,
                userRepository, judgeService, instantFeedbackProvider, clock, transactionManager);
    }

    private Round activeRound() {
        Round round = new Round(new Topic("배열", 1),
                LocalDate.of(2026, 7, 8), LocalDateTime.of(2026, 7, 8, 0, 0), LocalDateTime.of(2026, 7, 9, 0, 0));
        round.activate();
        return round;
    }

    private Problem problem(Round round, Language language, Difficulty difficulty) {
        return new Problem(round, "제목", "내용", language, difficulty, false);
    }

    private SubmissionRequest request(Language language) {
        return new SubmissionRequest(PROBLEM_ID, language, "print(1)", false);
    }

    private SubmissionRequest request(Language language, Boolean isAnonymous) {
        return new SubmissionRequest(PROBLEM_ID, language, "print(1)", isAnonymous);
    }

    @Test
    void problemNotFound_throws404() {
        when(problemRepository.findById(PROBLEM_ID)).thenReturn(Optional.empty());

        assertThrows(ProblemNotFoundException.class,
                () -> submissionService.submit(USER_ID, request(Language.PYTHON)));
    }

    @Test
    void languageMismatch_throws400() {
        Problem problem = problem(activeRound(), Language.JAVA, Difficulty.NORMAL);
        when(problemRepository.findById(PROBLEM_ID)).thenReturn(Optional.of(problem));

        assertThrows(LanguageMismatchException.class,
                () -> submissionService.submit(USER_ID, request(Language.PYTHON)));
    }

    @Test
    void inactiveRound_throws403() {
        Round round = new Round(new Topic("배열", 1),
                LocalDate.of(2026, 7, 8), LocalDateTime.of(2026, 7, 8, 0, 0), LocalDateTime.of(2026, 7, 9, 0, 0));
        Problem problem = problem(round, Language.PYTHON, Difficulty.NORMAL);
        when(problemRepository.findById(PROBLEM_ID)).thenReturn(Optional.of(problem));

        assertThrows(RoundClosedException.class,
                () -> submissionService.submit(USER_ID, request(Language.PYTHON)));
    }

    @Test
    void pastCloseAt_throws403() {
        Round round = new Round(new Topic("배열", 1),
                LocalDate.of(2026, 7, 6), LocalDateTime.of(2026, 7, 6, 0, 0), LocalDateTime.of(2026, 7, 7, 0, 0));
        round.activate();
        Problem problem = problem(round, Language.PYTHON, Difficulty.NORMAL);
        when(problemRepository.findById(PROBLEM_ID)).thenReturn(Optional.of(problem));

        assertThrows(RoundClosedException.class,
                () -> submissionService.submit(USER_ID, request(Language.PYTHON)));
    }

    @Test
    void acVerdictAwardsDifficultyBaseScoreAndRotatesLatest() {
        Problem problem = problem(activeRound(), Language.PYTHON, Difficulty.HARD);
        User user = new User(100L, "student@gsm.hs.kr", "홍길동", 2, 3, 15, "SW과", Role.STUDENT);
        when(problemRepository.findById(PROBLEM_ID)).thenReturn(Optional.of(problem));
        when(problemRepository.getReferenceById(any())).thenReturn(problem);
        when(testCaseRepository.findByProblemIdOrderByIdAsc(any()))
                .thenReturn(List.of(new TestCase(problem, "1", "1", true)));
        when(judgeService.judge(any())).thenReturn(new JudgeResult(Verdict.AC, 1, 1, 50, 1024));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        when(submissionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SubmissionResponse response = submissionService.submit(USER_ID, request(Language.PYTHON));

        assertEquals(Verdict.AC, response.verdict());
        assertEquals(new BigDecimal("70.00"), response.score());
        assertEquals(1, response.passedCount());
        assertEquals(1, response.totalCount());
        verify(submissionRepository).markPreviousNotLatest(USER_ID, problem.getId());
    }

    @Test
    void nonAcVerdictScoresZero() {
        Problem problem = problem(activeRound(), Language.PYTHON, Difficulty.HARD);
        User user = new User(100L, "student@gsm.hs.kr", "홍길동", 2, 3, 15, "SW과", Role.STUDENT);
        when(problemRepository.findById(PROBLEM_ID)).thenReturn(Optional.of(problem));
        when(problemRepository.getReferenceById(any())).thenReturn(problem);
        when(testCaseRepository.findByProblemIdOrderByIdAsc(any()))
                .thenReturn(List.of(new TestCase(problem, "1", "1", true), new TestCase(problem, "2", "2", false)));
        when(judgeService.judge(any())).thenReturn(new JudgeResult(Verdict.WA, 1, 2, 50, 1024));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        when(submissionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SubmissionResponse response = submissionService.submit(USER_ID, request(Language.PYTHON));

        assertEquals(Verdict.WA, response.verdict());
        assertEquals(new BigDecimal("0.00"), response.score());
        assertEquals(1, response.passedCount());
        assertEquals(2, response.totalCount());
    }

    @Test
    void noTestCasesThrowsTestCaseNotConfigured() {
        Problem problem = problem(activeRound(), Language.PYTHON, Difficulty.HARD);
        when(problemRepository.findById(PROBLEM_ID)).thenReturn(Optional.of(problem));
        when(testCaseRepository.findByProblemIdOrderByIdAsc(any())).thenReturn(List.of());

        assertThrows(TestCaseNotConfiguredException.class,
                () -> submissionService.submit(USER_ID, request(Language.PYTHON)));
    }

    /** isAnonymous가 null이면 요청 시점 user.isAnonymousDefault() 값이 그대로 적용된다. */
    @Test
    void 요청의_isAnonymous가_null이면_사용자_기본값이_적용된다() {
        Problem problem = problem(activeRound(), Language.PYTHON, Difficulty.HARD);
        User user = new User(100L, "student@gsm.hs.kr", "홍길동", 2, 3, 15, "SW과", Role.STUDENT);
        user.updateAnonymousDefault(true);
        when(problemRepository.findById(PROBLEM_ID)).thenReturn(Optional.of(problem));
        when(problemRepository.getReferenceById(any())).thenReturn(problem);
        when(testCaseRepository.findByProblemIdOrderByIdAsc(any()))
                .thenReturn(List.of(new TestCase(problem, "1", "1", true)));
        when(judgeService.judge(any())).thenReturn(new JudgeResult(Verdict.AC, 1, 1, 50, 1024));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        when(submissionRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        submissionService.submit(USER_ID, request(Language.PYTHON, null));

        assertTrue(captor.getValue().isAnonymous());
    }

    /** isAnonymous가 null이 아니면 요청 값이 사용자 기본값보다 우선한다. */
    @Test
    void 요청의_isAnonymous가_null이_아니면_요청_값이_우선한다() {
        Problem problem = problem(activeRound(), Language.PYTHON, Difficulty.HARD);
        User user = new User(100L, "student@gsm.hs.kr", "홍길동", 2, 3, 15, "SW과", Role.STUDENT);
        // 사용자 기본값은 false지만 요청이 true를 명시 — 요청 값이 이겨야 한다.
        when(problemRepository.findById(PROBLEM_ID)).thenReturn(Optional.of(problem));
        when(problemRepository.getReferenceById(any())).thenReturn(problem);
        when(testCaseRepository.findByProblemIdOrderByIdAsc(any()))
                .thenReturn(List.of(new TestCase(problem, "1", "1", true)));
        when(judgeService.judge(any())).thenReturn(new JudgeResult(Verdict.AC, 1, 1, 50, 1024));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        when(submissionRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        submissionService.submit(USER_ID, request(Language.PYTHON, true));

        assertTrue(captor.getValue().isAnonymous());
    }

    /** 사용자 기본값이 true여도 요청이 명시적으로 false면 실명(비익명)으로 저장된다. */
    @Test
    void 사용자_기본값이_true여도_요청이_false면_실명이다() {
        Problem problem = problem(activeRound(), Language.PYTHON, Difficulty.HARD);
        User user = new User(100L, "student@gsm.hs.kr", "홍길동", 2, 3, 15, "SW과", Role.STUDENT);
        user.updateAnonymousDefault(true);
        when(problemRepository.findById(PROBLEM_ID)).thenReturn(Optional.of(problem));
        when(problemRepository.getReferenceById(any())).thenReturn(problem);
        when(testCaseRepository.findByProblemIdOrderByIdAsc(any()))
                .thenReturn(List.of(new TestCase(problem, "1", "1", true)));
        when(judgeService.judge(any())).thenReturn(new JudgeResult(Verdict.AC, 1, 1, 50, 1024));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        when(submissionRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        submissionService.submit(USER_ID, request(Language.PYTHON, false));

        assertFalse(captor.getValue().isAnonymous());
    }
}
