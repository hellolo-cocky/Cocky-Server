package com.cocky.cockyserver.infra.judge0;

import com.cocky.cockyserver.domain.submission.entity.Verdict;
import com.cocky.cockyserver.domain.submission.judge.JudgeExecutionException;
import com.cocky.cockyserver.domain.submission.judge.JudgeRequest;
import com.cocky.cockyserver.domain.submission.judge.JudgeResult;
import com.cocky.cockyserver.domain.submission.judge.JudgeService;
import com.cocky.cockyserver.domain.submission.judge.RunRequest;
import com.cocky.cockyserver.domain.submission.judge.RunResult;
import com.cocky.cockyserver.domain.submission.judge.TestCaseIO;
import com.cocky.cockyserver.infra.judge.JudgeProperties;
import com.cocky.cockyserver.infra.judge0.dto.Judge0SubmissionRequest;
import com.cocky.cockyserver.infra.judge0.dto.Judge0SubmissionResult;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link JudgeService}의 Judge0 구현체. 테스트케이스마다 Judge0에 개별 제출하고, 전부
 * AC면 AC, 아니면 첫 실패 케이스의 verdict를 최종 결과로 삼는다.
 *
 * <p>Judge0 의존(클라이언트, DTO, language_id 매핑)은 이 클래스와 {@link Judge0Client},
 * {@link LanguageMapper} 밖으로 새어나가면 안 된다(CLAUDE.md §8.5).
 */
public class Judge0Adapter implements JudgeService {

    private static final Logger log = LoggerFactory.getLogger(Judge0Adapter.class);

    private final Judge0Client client;
    private final LanguageMapper languageMapper;
    private final JudgeProperties properties;

    public Judge0Adapter(Judge0Client client, LanguageMapper languageMapper, JudgeProperties properties) {
        this.client = client;
        this.languageMapper = languageMapper;
        this.properties = properties;
    }

    /** Judge0 엔진은 단순 실행(/run)을 지원하지 않는다 — 호출 측(RunService)이 502로 변환한다. */
    @Override
    public RunResult run(RunRequest request) {
        throw new UnsupportedOperationException(
                "Judge0 엔진은 코드 단순 실행(/run)을 지원하지 않습니다. JUDGE_ENGINE=runner를 사용하세요.");
    }

    @Override
    public JudgeResult judge(JudgeRequest request) {
        int languageId = languageMapper.toJudge0LanguageId(request.language());
        double cpuTimeLimitSeconds = properties.defaultTimeLimitMs() / 1000.0;
        String encodedCode = encode(request.code());

        int total = request.cases().size();
        int passed = 0;
        Verdict overallVerdict = Verdict.AC;
        boolean failureRecorded = false;
        Integer maxTimeMs = null;
        Integer maxMemoryKb = null;

        for (TestCaseIO testCase : request.cases()) {
            Judge0SubmissionRequest submissionRequest = new Judge0SubmissionRequest(
                    encodedCode,
                    languageId,
                    encode(testCase.input()),
                    encode(testCase.expectedOutput()),
                    cpuTimeLimitSeconds,
                    properties.defaultMemoryLimitKb());
            Judge0SubmissionResult result = client.submit(submissionRequest);
            Verdict verdict = mapVerdict(result);

            if (verdict == Verdict.AC) {
                passed++;
            } else if (!failureRecorded) {
                overallVerdict = verdict;
                failureRecorded = true;
            }

            maxTimeMs = maxOf(maxTimeMs, parseTimeMs(result.time()));
            maxMemoryKb = maxOf(maxMemoryKb, result.memory());

            // 컴파일 에러는 모든 케이스에서 동일하게 재현되므로 나머지 호출을 생략한다.
            if (verdict == Verdict.CE) {
                break;
            }
        }

        return new JudgeResult(overallVerdict, passed, total, maxTimeMs, maxMemoryKb);
    }

    private Verdict mapVerdict(Judge0SubmissionResult result) {
        int statusId = result.status().id();
        return switch (statusId) {
            case 3 -> Verdict.AC;
            case 4 -> Verdict.WA;
            case 5 -> Verdict.TLE;
            case 6 -> Verdict.CE;
            case 7, 8, 9, 10, 11, 12 -> Verdict.RE;
            default -> {
                log.warn("Judge0 처리 불가 상태: statusId={}, description={}", statusId, result.status().description());
                throw new JudgeExecutionException("Judge0 채점 상태를 처리할 수 없습니다: " + result.status().description());
            }
        };
    }

    private Integer parseTimeMs(String timeSeconds) {
        if (timeSeconds == null || timeSeconds.isBlank()) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(timeSeconds) * 1000);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer maxOf(Integer current, Integer candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null ? candidate : Math.max(current, candidate);
    }

    private String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
