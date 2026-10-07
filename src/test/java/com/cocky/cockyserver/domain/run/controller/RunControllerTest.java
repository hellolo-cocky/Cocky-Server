package com.cocky.cockyserver.domain.run.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cocky.cockyserver.domain.submission.judge.JudgeExecutionException;
import com.cocky.cockyserver.domain.submission.judge.JudgeService;
import com.cocky.cockyserver.domain.submission.judge.RunRequest;
import com.cocky.cockyserver.domain.submission.judge.RunResult;
import com.cocky.cockyserver.domain.submission.judge.RunStatus;
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
 * POST /api/v1/run — 실제 보안 필터 체인(JWT)·GlobalExceptionHandler·RunService·레이트리밋을 전부 올리고
 * 채점 엔진({@link JudgeService})만 목으로 바꾼 통합 슬라이스. 레이트리밋은 3회로 낮춰 429를 재현한다.
 * 유저별 카운터가 테스트 간에 섞이지 않도록 테스트마다 서로 다른 userId로 토큰을 발급한다.
 */
@SpringBootTest(properties = "run.rate-limit-per-minute=3")
@AutoConfigureMockMvc
class RunControllerTest {

    private static final String BODY = "{\"language\":\"PYTHON\",\"code\":\"print(input())\",\"stdin\":\"hi\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean(name = "judgeService")
    private JudgeService judgeService;

    private static long nextUserId = 1000;

    private String bearer;

    @BeforeEach
    void setUp() {
        reset(judgeService);
        bearer = "Bearer " + jwtProvider.generateAccessToken(nextUserId++, Role.STUDENT);
    }

    @Test
    void 토큰이_없으면_401이다() throws Exception {
        mockMvc.perform(post("/api/v1/run").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());

        verify(judgeService, never()).run(any());
    }

    @Test
    void 정상_요청은_200과_실행_결과를_내려준다() throws Exception {
        when(judgeService.run(any(RunRequest.class)))
                .thenReturn(new RunResult(RunStatus.OK, "hi\n", "", null, 33));

        mockMvc.perform(post("/api/v1/run")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.stdout").value("hi\n"))
                .andExpect(jsonPath("$.stderr").value(""))
                .andExpect(jsonPath("$.compileOutput").doesNotExist())
                .andExpect(jsonPath("$.timeMs").value(33));
    }

    @Test
    void stdin이_없어도_빈_입력으로_실행된다() throws Exception {
        when(judgeService.run(any(RunRequest.class)))
                .thenReturn(new RunResult(RunStatus.OK, "", "", null, 1));

        mockMvc.perform(post("/api/v1/run")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"C\",\"code\":\"int main(){}\"}"))
                .andExpect(status().isOk());

        verify(judgeService).run(new RunRequest(
                com.cocky.cockyserver.domain.problem.entity.Language.C, "int main(){}", ""));
    }

    @Test
    void code가_64KB를_넘으면_400이고_엔진을_호출하지_않는다() throws Exception {
        String tooLarge = "a".repeat(64 * 1024 + 1);

        mockMvc.perform(post("/api/v1/run")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"PYTHON\",\"code\":\"" + tooLarge + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(judgeService, never()).run(any());
    }

    @Test
    void stdin이_64KB를_넘으면_400이다() throws Exception {
        String tooLarge = "a".repeat(64 * 1024 + 1);

        mockMvc.perform(post("/api/v1/run")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"PYTHON\",\"code\":\"print(1)\",\"stdin\":\"" + tooLarge + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 정확히_64KB는_허용된다() throws Exception {
        when(judgeService.run(any(RunRequest.class)))
                .thenReturn(new RunResult(RunStatus.OK, "", "", null, 1));
        String exact = "a".repeat(64 * 1024);

        mockMvc.perform(post("/api/v1/run")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"PYTHON\",\"code\":\"" + exact + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void code가_비어_있으면_400이다() throws Exception {
        mockMvc.perform(post("/api/v1/run")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"PYTHON\",\"code\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 분당_제한을_넘으면_429이고_Retry_After가_내려온다() throws Exception {
        when(judgeService.run(any(RunRequest.class)))
                .thenReturn(new RunResult(RunStatus.OK, "", "", null, 1));

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/run")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/v1/run")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void 레이트리밋은_유저별로_따로_센다() throws Exception {
        when(judgeService.run(any(RunRequest.class)))
                .thenReturn(new RunResult(RunStatus.OK, "", "", null, 1));
        String otherUser = "Bearer " + jwtProvider.generateAccessToken(nextUserId++, Role.STUDENT);

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/run")
                    .header(HttpHeaders.AUTHORIZATION, bearer)
                    .contentType(MediaType.APPLICATION_JSON).content(BODY));
        }

        mockMvc.perform(post("/api/v1/run")
                        .header(HttpHeaders.AUTHORIZATION, otherUser)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void 크기_초과_요청은_레이트리밋을_소모하지_않는다() throws Exception {
        when(judgeService.run(any(RunRequest.class)))
                .thenReturn(new RunResult(RunStatus.OK, "", "", null, 1));
        String tooLarge = "a".repeat(64 * 1024 + 1);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/run")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"language\":\"PYTHON\",\"code\":\"" + tooLarge + "\"}"))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/api/v1/run")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void 엔진_오류는_502_JUDGE_EXECUTION_FAILED다() throws Exception {
        when(judgeService.run(any(RunRequest.class)))
                .thenThrow(new JudgeExecutionException("채점 서버 호출에 실패했습니다."));

        mockMvc.perform(post("/api/v1/run")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("JUDGE_EXECUTION_FAILED"));
    }

    @Test
    void 엔진이_run을_지원하지_않으면_502다() throws Exception {
        when(judgeService.run(any(RunRequest.class))).thenThrow(new UnsupportedOperationException("no run"));

        mockMvc.perform(post("/api/v1/run")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("JUDGE_EXECUTION_FAILED"));
    }
}
