package com.cocky.cockyserver.domain.submission.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cocky.cockyserver.domain.problem.entity.Difficulty;
import com.cocky.cockyserver.domain.problem.entity.Language;
import com.cocky.cockyserver.domain.problem.entity.Problem;
import com.cocky.cockyserver.domain.round.entity.Round;
import com.cocky.cockyserver.domain.submission.entity.Submission;
import com.cocky.cockyserver.domain.submission.entity.Verdict;
import com.cocky.cockyserver.domain.topic.entity.Topic;
import com.cocky.cockyserver.domain.user.entity.Role;
import com.cocky.cockyserver.domain.user.entity.User;
import com.cocky.cockyserver.global.security.jwt.JwtProvider;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * GET /api/v1/submissions/me, /me/{id} — 보안 필터·서비스·GlobalExceptionHandler를 실제로 올린 통합 슬라이스.
 * 테스트 메서드 단위로 롤백되므로 데이터는 각 테스트가 직접 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SubmissionHistoryControllerTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 7, 6, 0, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private EntityManager entityManager;

    private User me;
    private User other;
    private Problem problem;
    private String bearer;

    @BeforeEach
    void setUp() {
        me = persistUser(1001L, "me@gsm.hs.kr");
        other = persistUser(1002L, "other@gsm.hs.kr");
        Topic topic = new Topic("구현", 1);
        entityManager.persist(topic);
        Round round = new Round(topic, LocalDate.of(2026, 7, 6), BASE, BASE.plusDays(1));
        entityManager.persist(round);
        problem = new Problem(round, "두 수의 합", "내용", Language.JAVA, Difficulty.EASY, false);
        entityManager.persist(problem);
        bearer = "Bearer " + jwtProvider.generateAccessToken(me.getId(), Role.STUDENT);
    }

    private User persistUser(Long datagsmId, String email) {
        User user = new User(datagsmId, email, "학생", 2, 3, 1, "SW과", Role.STUDENT);
        entityManager.persist(user);
        return user;
    }

    private Submission persistSubmission(User owner, Verdict verdict, int hoursAfterBase) {
        Submission submission = new Submission(owner, problem, Language.JAVA, "print(1)", false);
        submission.updateResult(verdict, new BigDecimal("10.00"));
        ReflectionTestUtils.setField(submission, "submittedAt", BASE.plusHours(hoursAfterBase));
        entityManager.persist(submission);
        return submission;
    }

    @Test
    void 내_목록은_본인_제출만_최신순으로_내려준다() throws Exception {
        persistSubmission(me, Verdict.WA, 1);
        persistSubmission(me, Verdict.PENDING, 2);
        persistSubmission(other, Verdict.AC, 3);

        mockMvc.perform(get("/api/v1/submissions/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].verdict").value("PENDING"))
                .andExpect(jsonPath("$.content[1].verdict").value("WA"))
                .andExpect(jsonPath("$.content[0].problemTitle").value("두 수의 합"))
                .andExpect(jsonPath("$.content[0].difficulty").value("EASY"))
                .andExpect(jsonPath("$.content[0].language").value("JAVA"))
                .andExpect(jsonPath("$.content[0].aiScore").doesNotExist());
    }

    @Test
    void 페이징_파라미터대로_잘라서_내려준다() throws Exception {
        for (int i = 1; i <= 5; i++) {
            persistSubmission(me, Verdict.AC, i);
        }

        mockMvc.perform(get("/api/v1/submissions/me")
                        .param("page", "1").param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.number").value(1));
    }

    @Test
    void 클라이언트가_보낸_sort는_무시하고_최신순을_유지한다() throws Exception {
        persistSubmission(me, Verdict.WA, 1);
        persistSubmission(me, Verdict.AC, 2);

        mockMvc.perform(get("/api/v1/submissions/me")
                        .param("sort", "verdict,asc")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].verdict").value("AC"));
    }

    @Test
    void 본인_제출_상세는_코드와_문제_정보를_내려준다() throws Exception {
        Submission mine = persistSubmission(me, Verdict.AC, 1);
        mine.applyFeedback(new BigDecimal("3.00"), new BigDecimal("2.00"), new BigDecimal("1.00"), "깔끔해요");

        mockMvc.perform(get("/api/v1/submissions/me/{id}", mine.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submissionId").value(mine.getId()))
                .andExpect(jsonPath("$.problemTitle").value("두 수의 합"))
                .andExpect(jsonPath("$.code").value("print(1)"))
                .andExpect(jsonPath("$.aiScore").value(6.0))
                .andExpect(jsonPath("$.comment").value("깔끔해요"));
    }

    @Test
    void 타인_제출_상세는_403이_아니라_404다() throws Exception {
        Submission others = persistSubmission(other, Verdict.AC, 1);

        mockMvc.perform(get("/api/v1/submissions/me/{id}", others.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SUBMISSION_NOT_FOUND"));
    }

    @Test
    void 없는_제출과_타인_제출은_같은_응답이다() throws Exception {
        Submission others = persistSubmission(other, Verdict.AC, 1);
        long missingId = others.getId() + 1000;

        String existingBody = mockMvc.perform(get("/api/v1/submissions/me/{id}", others.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andReturn().getResponse().getContentAsString();
        String missingBody = mockMvc.perform(get("/api/v1/submissions/me/{id}", missingId)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andReturn().getResponse().getContentAsString();

        // 메시지에 id가 들어가므로 id만 맞춰 비교한다 — 상태·code·문구가 같아야 존재 여부를 알 수 없다.
        org.assertj.core.api.Assertions.assertThat(existingBody.replace(String.valueOf(others.getId()), "ID"))
                .isEqualTo(missingBody.replace(String.valueOf(missingId), "ID"));
    }

    @Test
    void 기존_경로와_feedback도_타인_제출이면_404다() throws Exception {
        Submission others = persistSubmission(other, Verdict.AC, 1);

        mockMvc.perform(get("/api/v1/submissions/{id}", others.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/submissions/{id}/feedback", others.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound());
    }

    @Test
    void 토큰이_없으면_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/submissions/me")).andExpect(status().isUnauthorized());
    }
}
