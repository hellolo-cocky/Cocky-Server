package com.cocky.cockyserver.domain.run.service;

import com.cocky.cockyserver.domain.run.dto.CodeRunRequest;
import com.cocky.cockyserver.domain.submission.judge.JudgeExecutionException;
import com.cocky.cockyserver.domain.submission.judge.JudgeService;
import com.cocky.cockyserver.domain.submission.judge.RunRequest;
import com.cocky.cockyserver.domain.submission.judge.RunResult;
import com.cocky.cockyserver.global.validation.InputSizeGuard;
import org.springframework.stereotype.Service;

/**
 * 채점 없는 코드 단순 실행. DB에 저장하지 않으며 {@link JudgeService}에만 의존한다. 검증(크기) →
 * 레이트리밋 순서라 잘못된 요청은 호출 한도를 소모하지 않는다.
 */
@Service
public class RunService {

    private final JudgeService judgeService;
    private final RunRateLimiter rateLimiter;

    public RunService(JudgeService judgeService, RunRateLimiter rateLimiter) {
        this.judgeService = judgeService;
        this.rateLimiter = rateLimiter;
    }

    public RunResult run(Long userId, CodeRunRequest request) {
        InputSizeGuard.requireWithinLimit("code", request.code());
        InputSizeGuard.requireWithinLimit("stdin", request.stdin());
        rateLimiter.check(userId);

        String stdin = request.stdin() == null ? "" : request.stdin();
        try {
            return judgeService.run(new RunRequest(request.language(), request.code(), stdin));
        } catch (UnsupportedOperationException e) {
            // 현재 엔진(예: judge0)이 /run을 지원하지 않는 설정 문제 — 클라이언트 잘못이 아니라 502로 알린다.
            throw new JudgeExecutionException("현재 채점 엔진은 코드 실행을 지원하지 않습니다.", e);
        }
    }
}
