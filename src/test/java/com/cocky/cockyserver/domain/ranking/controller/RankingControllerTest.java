package com.cocky.cockyserver.domain.ranking.controller;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cocky.cockyserver.domain.ranking.service.RankingQueryService;
import com.cocky.cockyserver.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * GET /api/v1/rankings 슬라이스 테스트. standalone MockMvc라 Security 필터 체인을 타지 않으므로
 * (401이 먼저 나지 않음) 인증 처리 없이 GlobalExceptionHandler의 파라미터 누락 응답만 검증한다.
 */
class RankingControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RankingController(mock(RankingQueryService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void period_없이_호출하면_400과_code_message_형식이_내려온다() throws Exception {
        mockMvc.perform(get("/api/v1/rankings").param("scope", "SCHOOL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("필수 파라미터 'period'가 없습니다."));
    }
}
