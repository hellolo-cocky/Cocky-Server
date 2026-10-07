package com.cocky.cockyserver.domain.submission.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cocky.cockyserver.domain.submission.judge.JudgeRequest;
import com.cocky.cockyserver.domain.submission.judge.JudgeService;
import com.cocky.cockyserver.domain.user.entity.Role;
import com.cocky.cockyserver.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * POST /api/v1/submissions의 코드 크기 검사(64KB, UTF-8 바이트) — 보안 필터·GlobalExceptionHandler·
 * SubmissionService를 실제로 올리고 채점 엔진만 목으로 바꾼 통합 슬라이스. 크기 검사는 DB 조회보다
 * 먼저 일어나므로 문제 데이터를 준비할 필요가 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SubmissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean(name = "judgeService")
    private JudgeService judgeService;

    private String bearer;

    @BeforeEach
    void setUp() {
        reset(judgeService);
        bearer = "Bearer " + jwtProvider.generateAccessToken(2000L, Role.STUDENT);
    }

    private String body(String code) {
        return "{\"problemId\":1,\"language\":\"PYTHON\",\"code\":\"" + code + "\"}";
    }

    @Test
    void code가_64KB를_넘으면_400이고_엔진을_호출하지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/submissions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("a".repeat(64 * 1024 + 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(judgeService, never()).judge(org.mockito.ArgumentMatchers.any(JudgeRequest.class));
    }

    @Test
    void 한글_코드는_문자수가_아니라_바이트로_세어_400이다() throws Exception {
        mockMvc.perform(post("/api/v1/submissions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("가".repeat(21_846))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 토큰이_없으면_401이다() throws Exception {
        mockMvc.perform(post("/api/v1/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("print(1)")))
                .andExpect(status().isUnauthorized());
    }
}
