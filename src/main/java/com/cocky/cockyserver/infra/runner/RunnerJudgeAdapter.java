package com.cocky.cockyserver.infra.runner;

import com.cocky.cockyserver.domain.problem.entity.Language;
import com.cocky.cockyserver.domain.submission.entity.Verdict;
import com.cocky.cockyserver.domain.submission.judge.JudgeExecutionException;
import com.cocky.cockyserver.domain.submission.judge.JudgeRequest;
import com.cocky.cockyserver.domain.submission.judge.JudgeResult;
import com.cocky.cockyserver.domain.submission.judge.JudgeService;
import com.cocky.cockyserver.domain.submission.judge.RunRequest;
import com.cocky.cockyserver.domain.submission.judge.RunResult;
import com.cocky.cockyserver.domain.submission.judge.RunStatus;
import com.cocky.cockyserver.infra.judge.JudgeProperties;
import com.cocky.cockyserver.infra.runner.dto.RunnerJudgeRequest;
import com.cocky.cockyserver.infra.runner.dto.RunnerJudgeResponse;
import com.cocky.cockyserver.infra.runner.dto.RunnerRunRequest;
import com.cocky.cockyserver.infra.runner.dto.RunnerRunResponse;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * {@link JudgeService}의 cocky-runner 구현체. runner는 케이스 전체를 한 번에 받아 동기로
 * 채점하므로 Judge0Adapter와 달리 호출이 1회다.
 *
 * <p>runner의 verdict/status를 Cocky의 {@link Verdict}/{@link RunStatus}로 바꾸는 변환은 이
 * 클래스 안에서만 한다 — MLE는 Cocky에 대응 값이 없어 RE로 접는다(DB ENUM 유지). runner 쪽
 * 오류(401/400/429/500), 타임아웃, 연결 실패, 빈/계약 밖 응답은 전부 {@link JudgeExecutionException}
 * (502)이며 성공으로 위장하지 않는다.
 *
 * <p>baseUrl, 인증 헤더(X-Runner-Token), 연결/읽기 타임아웃은 {@code restClient}에 이미 설정돼
 * 있다({@link RunnerConfig}).
 */
public class RunnerJudgeAdapter implements JudgeService {

    private static final Logger log = LoggerFactory.getLogger(RunnerJudgeAdapter.class);

    private static final String JUDGE_PATH = "/internal/v1/judge";
    private static final String RUN_PATH = "/internal/v1/run";

    private final RestClient restClient;
    private final JudgeProperties properties;

    public RunnerJudgeAdapter(RestClient restClient, JudgeProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public JudgeResult judge(JudgeRequest request) {
        List<RunnerJudgeRequest.TestCase> testCases = request.cases().stream()
                .map(c -> new RunnerJudgeRequest.TestCase(c.input(), c.expectedOutput()))
                .toList();
        RunnerJudgeRequest body = new RunnerJudgeRequest(
                toRunnerLanguage(request.language()), request.code(),
                properties.defaultTimeLimitMs(), properties.defaultMemoryLimitKb(), testCases);

        RunnerJudgeResponse response = post(JUDGE_PATH, body, RunnerJudgeResponse.class);

        if (response.passedCount() == null || response.totalCount() == null) {
            throw new JudgeExecutionException("채점 서버 응답에 passedCount/totalCount가 없습니다.");
        }
        return new JudgeResult(mapVerdict(response.verdict()), response.passedCount(), response.totalCount(),
                response.maxTimeMs(), response.maxMemoryKb());
    }

    @Override
    public RunResult run(RunRequest request) {
        RunnerRunRequest body = new RunnerRunRequest(
                toRunnerLanguage(request.language()), request.code(), request.stdin(),
                properties.defaultTimeLimitMs(), properties.defaultMemoryLimitKb());

        RunnerRunResponse response = post(RUN_PATH, body, RunnerRunResponse.class);

        return new RunResult(mapRunStatus(response.status()),
                nullToEmpty(response.stdout()), nullToEmpty(response.stderr()),
                response.compileOutput(), response.timeMs());
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        try {
            T response = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(responseType);
            if (response == null) {
                throw new JudgeExecutionException("채점 서버 응답이 비어 있습니다.");
            }
            return response;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.warn("cocky-runner 오류 응답: path={}, status={}", path, status);
            if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
                throw new JudgeExecutionException("채점 서버가 혼잡합니다. 잠시 후 다시 시도해주세요.", e);
            }
            throw new JudgeExecutionException("채점 서버 오류입니다. (HTTP " + status + ")", e);
        } catch (RestClientException e) {
            // 연결 실패/타임아웃(ResourceAccessException)과 응답 파싱 실패를 모두 포함한다.
            log.warn("cocky-runner 호출 실패: path={}, error={}", path, e.getMessage());
            throw new JudgeExecutionException("채점 서버 호출에 실패했습니다.", e);
        }
    }

    private Verdict mapVerdict(String runnerVerdict) {
        return switch (normalize(runnerVerdict)) {
            case "AC" -> Verdict.AC;
            case "WA" -> Verdict.WA;
            case "TLE" -> Verdict.TLE;
            case "RE", "MLE" -> Verdict.RE;
            case "CE" -> Verdict.CE;
            default -> throw new JudgeExecutionException("채점 서버가 알 수 없는 verdict를 반환했습니다: " + runnerVerdict);
        };
    }

    private RunStatus mapRunStatus(String runnerStatus) {
        return switch (normalize(runnerStatus)) {
            case "OK" -> RunStatus.OK;
            case "TLE" -> RunStatus.TLE;
            case "RE", "MLE" -> RunStatus.RE;
            case "CE" -> RunStatus.CE;
            default -> throw new JudgeExecutionException("채점 서버가 알 수 없는 status를 반환했습니다: " + runnerStatus);
        };
    }

    private String toRunnerLanguage(Language language) {
        return language.name().toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
