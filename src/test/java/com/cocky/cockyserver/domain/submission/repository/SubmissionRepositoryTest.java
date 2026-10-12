package com.cocky.cockyserver.domain.submission.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocky.cockyserver.domain.problem.entity.Difficulty;
import com.cocky.cockyserver.domain.problem.entity.Language;
import com.cocky.cockyserver.domain.problem.entity.Problem;
import com.cocky.cockyserver.domain.round.entity.Round;
import com.cocky.cockyserver.domain.submission.entity.Submission;
import com.cocky.cockyserver.domain.submission.entity.Verdict;
import com.cocky.cockyserver.domain.topic.entity.Topic;
import com.cocky.cockyserver.domain.user.entity.Role;
import com.cocky.cockyserver.domain.user.entity.User;
import com.cocky.cockyserver.global.config.JpaAuditingConfig;
import com.cocky.cockyserver.domain.submission.dto.SubmissionSummaryResponse;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.assertj.core.groups.Tuple;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * PeriodStats 집계 쿼리(languageCounts/difficultyCounts/wrongTypeCounts) 정확성 검증 +
 * 내 풀이 기록 조회(목록 프로젝션/상세 fetch join) 검증.
 * submittedAt은 엔티티 생성자가 {@code LocalDateTime.now()}를 직접 찍어버려 원하는 시각을
 * 주입할 수 없으므로, persist 이후 리플렉션으로 덮어써 기간 경계 케이스를 재현한다
 * (ProblemRepositoryTest의 id 리플렉션 세팅과 같은 이유).
 */
// User의 createdAt/updatedAt은 JPA Auditing(@CreatedDate)이 채운다 — @DataJpaTest 슬라이스는
// 기본적으로 JpaAuditingConfig를 스캔하지 않으므로 명시적으로 가져와야 NOT NULL 제약을 만족한다.
// generate_statistics: 풀이 기록 목록이 N+1 없이 고정된 쿼리 수로 나가는지(prepareStatementCount) 확인하려고 켠다.
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import(JpaAuditingConfig.class)
class SubmissionRepositoryTest {

    private static final LocalDateTime PERIOD_START = LocalDateTime.of(2026, 7, 6, 0, 0);
    private static final LocalDateTime PERIOD_END = LocalDateTime.of(2026, 7, 12, 0, 0);

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private EntityManager entityManager;

    private User persistUser() {
        return persistUser(1001L, "student@gsm.hs.kr");
    }

    private User persistUser(Long datagsmId, String email) {
        User user = new User(datagsmId, email, "학생", 2, 3, 1, "SW과", Role.STUDENT);
        entityManager.persist(user);
        return user;
    }

    private Problem persistProblem(Language language, Difficulty difficulty) {
        return persistProblem(language, difficulty, "제목");
    }

    private Problem persistProblem(Language language, Difficulty difficulty, String title) {
        Topic topic = new Topic("구현", 1);
        entityManager.persist(topic);
        Round round = new Round(topic, LocalDate.of(2026, 7, 6),
                PERIOD_START, PERIOD_END);
        entityManager.persist(round);
        Problem problem = new Problem(round, title, "내용", language, difficulty, false);
        entityManager.persist(problem);
        return problem;
    }

    /**
     * submittedAt을 임의 시각으로 고정한 제출을 만들어 저장한다. id 생성 전략이 IDENTITY라
     * persist() 시점에 즉시 INSERT가 나가므로, 리플렉션으로 필드를 덮어쓰는 건 반드시
     * persist() 호출 "전"에 해야 한다 — 이후에 덮어쓰면 이미 나간 INSERT에는 반영되지 않는다.
     */
    private Submission persistSubmission(User user, Problem problem, Language language, Verdict verdict,
                                          LocalDateTime submittedAt) {
        Submission submission = new Submission(user, problem, language, "print(1)", false);
        submission.updateResult(verdict, BigDecimal.ZERO.setScale(2));
        ReflectionTestUtils.setField(submission, "submittedAt", submittedAt);
        entityManager.persist(submission);
        return submission;
    }

    @Test
    void 언어와_난이도와_오답유형을_기간내_제출만_집계한다() {
        User user = persistUser();
        Problem easyJava = persistProblem(Language.JAVA, Difficulty.EASY);
        Problem normalPython = persistProblem(Language.PYTHON, Difficulty.NORMAL);
        Problem hardC = persistProblem(Language.C, Difficulty.HARD);

        // 기간 내 제출 4건: JAVA 2, PYTHON 1, C 1 / EASY 2, NORMAL 1, HARD 1 / AC 1, WA 1, TLE 1, CE 1
        persistSubmission(user, easyJava, Language.JAVA, Verdict.AC, PERIOD_START.plusHours(1));
        persistSubmission(user, normalPython, Language.PYTHON, Verdict.WA, PERIOD_START.plusHours(2));
        persistSubmission(user, hardC, Language.C, Verdict.TLE, PERIOD_START.plusHours(3));
        persistSubmission(user, easyJava, Language.JAVA, Verdict.CE, PERIOD_START.plusDays(2));

        // 기간 경계 밖: 시작 직전, 종료 시각(배타적 상한이라 제외되어야 함)
        persistSubmission(user, normalPython, Language.PYTHON, Verdict.WA, PERIOD_START.minusHours(1));
        persistSubmission(user, normalPython, Language.PYTHON, Verdict.AC, PERIOD_END);

        var languageCounts = submissionRepository.aggregateLanguageCountsByUserAndPeriod(
                user.getId(), PERIOD_START, PERIOD_END);
        var difficultyCounts = submissionRepository.aggregateDifficultyCountsByUserAndPeriod(
                user.getId(), PERIOD_START, PERIOD_END);
        var wrongTypeCounts = submissionRepository.aggregateWrongVerdictCountsByUserAndPeriod(
                user.getId(), PERIOD_START, PERIOD_END);

        assertThat(languageCounts)
                .extracting(SubmissionRepository.LanguageCount::getLanguage, SubmissionRepository.LanguageCount::getCount)
                .containsExactlyInAnyOrder(
                        Tuple.tuple(Language.JAVA, 2L),
                        Tuple.tuple(Language.PYTHON, 1L),
                        Tuple.tuple(Language.C, 1L));

        assertThat(difficultyCounts)
                .extracting(SubmissionRepository.DifficultyCount::getDifficulty,
                        SubmissionRepository.DifficultyCount::getCount)
                .containsExactlyInAnyOrder(
                        Tuple.tuple(Difficulty.EASY, 2L),
                        Tuple.tuple(Difficulty.NORMAL, 1L),
                        Tuple.tuple(Difficulty.HARD, 1L));

        // AC 1건(easyJava)은 오답 유형이 아니므로 wrongTypeCounts에 없어야 한다.
        assertThat(wrongTypeCounts)
                .extracting(SubmissionRepository.VerdictCount::getVerdict, SubmissionRepository.VerdictCount::getCount)
                .containsExactlyInAnyOrder(
                        Tuple.tuple(Verdict.WA, 1L),
                        Tuple.tuple(Verdict.TLE, 1L),
                        Tuple.tuple(Verdict.CE, 1L));
    }

    @Test
    void PENDING_제출은_wrongTypeCounts에서_제외된다() {
        User user = persistUser();
        Problem problem = persistProblem(Language.JAVA, Difficulty.EASY);

        // 채점이 아직 안 끝난 PENDING은 "오답 유형"이 아니므로 집계에서 빠져야 한다.
        persistSubmission(user, problem, Language.JAVA, Verdict.PENDING, PERIOD_START.plusHours(1));
        persistSubmission(user, problem, Language.JAVA, Verdict.WA, PERIOD_START.plusHours(2));

        var wrongTypeCounts = submissionRepository.aggregateWrongVerdictCountsByUserAndPeriod(
                user.getId(), PERIOD_START, PERIOD_END);

        assertThat(wrongTypeCounts)
                .extracting(SubmissionRepository.VerdictCount::getVerdict, SubmissionRepository.VerdictCount::getCount)
                .containsExactly(Tuple.tuple(Verdict.WA, 1L));
    }

    @Test
    void 기간_내_제출이_없으면_빈_리스트를_반환한다() {
        User user = persistUser();
        Problem problem = persistProblem(Language.JAVA, Difficulty.EASY);
        persistSubmission(user, problem, Language.JAVA, Verdict.AC, PERIOD_START.minusDays(10));

        var languageCounts = submissionRepository.aggregateLanguageCountsByUserAndPeriod(
                user.getId(), PERIOD_START, PERIOD_END);

        assertThat(languageCounts).isEmpty();
    }

    // ── 내 풀이 기록 (findHistoryByUserId / findDetailByIdAndUserId) ─────────────────────

    private Statistics statistics() {
        return entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    }

    @Test
    void 풀이기록은_본인_제출만_최신순으로_제목과_난이도와_함께_조회한다() {
        User me = persistUser();
        User other = persistUser(1002L, "other@gsm.hs.kr");
        Problem easy = persistProblem(Language.JAVA, Difficulty.EASY, "두 수의 합");
        Problem hard = persistProblem(Language.PYTHON, Difficulty.HARD, "최단 경로");

        persistSubmission(me, easy, Language.JAVA, Verdict.WA, PERIOD_START.plusHours(1));
        persistSubmission(me, hard, Language.PYTHON, Verdict.PENDING, PERIOD_START.plusHours(3));
        persistSubmission(me, easy, Language.JAVA, Verdict.AC, PERIOD_START.plusHours(2));
        persistSubmission(other, easy, Language.JAVA, Verdict.AC, PERIOD_START.plusHours(4));

        Page<SubmissionRepository.SubmissionHistoryRow> page =
                submissionRepository.findHistoryByUserId(me.getId(), PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(3);
        // 최신순: 3시간(PENDING) → 2시간(AC) → 1시간(WA). PENDING도 기록에는 보인다.
        assertThat(page.getContent())
                .extracting(SubmissionRepository.SubmissionHistoryRow::getVerdict,
                        SubmissionRepository.SubmissionHistoryRow::getProblemTitle,
                        SubmissionRepository.SubmissionHistoryRow::getDifficulty)
                .containsExactly(
                        Tuple.tuple(Verdict.PENDING, "최단 경로", Difficulty.HARD),
                        Tuple.tuple(Verdict.AC, "두 수의 합", Difficulty.EASY),
                        Tuple.tuple(Verdict.WA, "두 수의 합", Difficulty.EASY));
    }

    @Test
    void 풀이기록은_페이지_단위로_잘라서_조회한다() {
        User me = persistUser();
        Problem problem = persistProblem(Language.JAVA, Difficulty.EASY);
        for (int i = 1; i <= 5; i++) {
            persistSubmission(me, problem, Language.JAVA, Verdict.AC, PERIOD_START.plusHours(i));
        }

        Page<SubmissionRepository.SubmissionHistoryRow> second =
                submissionRepository.findHistoryByUserId(me.getId(), PageRequest.of(1, 2));

        assertThat(second.getTotalElements()).isEqualTo(5);
        assertThat(second.getTotalPages()).isEqualTo(3);
        // 최신순 5,4 | 3,2 | 1 — 두 번째 페이지는 3시간, 2시간 제출.
        assertThat(second.getContent())
                .extracting(SubmissionRepository.SubmissionHistoryRow::getSubmittedAt)
                .containsExactly(PERIOD_START.plusHours(3), PERIOD_START.plusHours(2));
    }

    @Test
    void 풀이기록_목록은_문제가_달라도_쿼리가_목록과_count_두_번뿐이다() {
        User me = persistUser();
        for (int i = 1; i <= 5; i++) {
            Problem problem = persistProblem(Language.JAVA, Difficulty.EASY, "문제" + i);
            persistSubmission(me, problem, Language.JAVA, Verdict.AC, PERIOD_START.plusHours(i));
        }
        entityManager.flush();
        entityManager.clear();
        statistics().clear();

        Page<SubmissionRepository.SubmissionHistoryRow> page =
                submissionRepository.findHistoryByUserId(me.getId(), PageRequest.of(0, 2));
        // 제목까지 읽어도 추가 쿼리가 없어야 한다(엔티티 + lazy problem 조회였다면 제출 수만큼 늘어난다).
        page.getContent().forEach(row -> assertThat(row.getProblemTitle()).startsWith("문제"));

        assertThat(statistics().getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    void aiScore는_세_점수의_합이고_피드백이_없으면_null이다() {
        User me = persistUser();
        Problem problem = persistProblem(Language.JAVA, Difficulty.EASY);
        Submission withFeedback = persistSubmission(me, problem, Language.JAVA, Verdict.AC,
                PERIOD_START.plusHours(2));
        withFeedback.applyFeedback(new BigDecimal("3.00"), new BigDecimal("2.50"), new BigDecimal("1.00"), "좋아요");
        persistSubmission(me, problem, Language.JAVA, Verdict.AC, PERIOD_START.plusHours(1));
        entityManager.flush();
        entityManager.clear();

        var page = submissionRepository.findHistoryByUserId(me.getId(), PageRequest.of(0, 20));

        assertThat(page.getContent().stream().map(SubmissionSummaryResponse::from).toList())
                .extracting(SubmissionSummaryResponse::aiScore)
                .containsExactly(new BigDecimal("6.50"), null);
    }

    @Test
    void 상세는_본인_제출이면_problem을_함께_가져오고_타인_제출이면_빈_결과다() {
        User me = persistUser();
        User other = persistUser(1002L, "other@gsm.hs.kr");
        Problem problem = persistProblem(Language.JAVA, Difficulty.EASY, "두 수의 합");
        Submission mine = persistSubmission(me, problem, Language.JAVA, Verdict.AC, PERIOD_START.plusHours(1));
        entityManager.flush();
        entityManager.clear();

        var found = submissionRepository.findDetailByIdAndUserId(mine.getId(), me.getId());

        assertThat(found).isPresent();
        assertThat(Hibernate.isInitialized(found.get().getProblem())).isTrue();
        assertThat(found.get().getProblem().getTitle()).isEqualTo("두 수의 합");
        // 남의 제출과 없는 제출은 구분할 수 없어야 한다(둘 다 빈 결과 → 서비스가 404).
        assertThat(submissionRepository.findDetailByIdAndUserId(mine.getId(), other.getId())).isEmpty();
        assertThat(submissionRepository.findDetailByIdAndUserId(999_999L, me.getId())).isEmpty();
    }
}
