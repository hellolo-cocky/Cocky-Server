package com.cocky.cockyserver.ai.dto;

/**
 * 언어×난이도 조합 하나의 생성 결과. 백엔드 ai_generation_log 한 행에 대응
 * (problem은 실패 시 null, attempts = retry_count).
 *
 * @param language   대상 언어.
 * @param difficulty 난이도.
 * @param problem    생성된 문제(실패 시 null).
 * @param attempts   소요 시도 횟수(1~maxAttempts).
 * @param failReason 실패 사유(성공 시 null).
 */
public record GenerationItem(
        Language language,
        Difficulty difficulty,
        GeneratedProblem problem,
        int attempts,
        String failReason
) {
    public boolean success() {
        return problem != null;
    }

    public static GenerationItem success(GeneratedProblem problem, int attempts) {
        return new GenerationItem(problem.language(), problem.difficulty(), problem, attempts, null);
    }

    public static GenerationItem failure(Language language, Difficulty difficulty,
                                         int attempts, String reason) {
        return new GenerationItem(language, difficulty, null, attempts, reason);
    }
}
