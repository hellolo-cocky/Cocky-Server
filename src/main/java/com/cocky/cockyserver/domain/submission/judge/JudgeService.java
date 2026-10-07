package com.cocky.cockyserver.domain.submission.judge;

/**
 * 채점 포트. 백엔드(SubmissionService, RunService)는 이 인터페이스에만 의존한다.
 *
 * <p>엔진 구현체(Judge0, cocky-runner, 스텁)는 {@code judge.engine} 설정으로 선택된다
 * ({@code infra.judge.JudgeConfig}) — 엔진 관련 타입/설정이 이 인터페이스 밖으로 새어나가면 안 된다
 * (CLAUDE.md §8.5).
 */
public interface JudgeService {

    JudgeResult judge(JudgeRequest request);

    /**
     * 채점 없이 코드를 한 번 실행한다(POST /api/v1/run). 이 기능을 지원하지 않는 엔진은
     * {@link UnsupportedOperationException}을 던진다.
     */
    RunResult run(RunRequest request);
}
