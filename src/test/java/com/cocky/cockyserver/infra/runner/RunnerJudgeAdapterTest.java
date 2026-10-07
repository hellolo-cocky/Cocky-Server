package com.cocky.cockyserver.infra.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.cocky.cockyserver.domain.problem.entity.Language;
import com.cocky.cockyserver.domain.submission.entity.Verdict;
import com.cocky.cockyserver.domain.submission.judge.JudgeExecutionException;
import com.cocky.cockyserver.domain.submission.judge.JudgeRequest;
import com.cocky.cockyserver.domain.submission.judge.JudgeResult;
import com.cocky.cockyserver.domain.submission.judge.RunRequest;
import com.cocky.cockyserver.domain.submission.judge.RunResult;
import com.cocky.cockyserver.domain.submission.judge.RunStatus;
import com.cocky.cockyserver.domain.submission.judge.TestCaseIO;
import com.cocky.cockyserver.infra.judge.JudgeProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RunnerJudgeAdapterTest {

    private static final String BASE_URL = "http://runner.test";
    private static final String TOKEN = "secret-token";
    private static final JudgeProperties JUDGE_PROPERTIES = new JudgeProperties("runner", 2000, 131072);

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final RunnerJudgeAdapter adapter = new RunnerJudgeAdapter(
            builder.baseUrl(BASE_URL).defaultHeader(RunnerConfig.TOKEN_HEADER, TOKEN).build(), JUDGE_PROPERTIES);

    private final JudgeRequest judgeRequest = new JudgeRequest(Language.PYTHON, "print(1)",
            List.of(new TestCaseIO("in1", "out1"), new TestCaseIO("in2", "out2")));
    private final RunRequest runRequest = new RunRequest(Language.JAVA, "class Main {}", "hello");

    // ---- judge ----

    @Test
    void judge_요청_바디와_토큰_헤더가_계약대로_나가고_AC가_매핑된다() {
        server.expect(requestTo(BASE_URL + "/internal/v1/judge"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(RunnerConfig.TOKEN_HEADER, TOKEN))
                .andExpect(content().string(allOf(
                        containsString("\"language\":\"python\""),
                        containsString("\"sourceCode\":\"print(1)\""),
                        containsString("\"timeLimitMs\":2000"),
                        containsString("\"memoryLimitKb\":131072"),
                        containsString("\"testCases\":[{\"input\":\"in1\",\"expectedOutput\":\"out1\"}"))))
                .andRespond(withSuccess(
                        "{\"verdict\":\"AC\",\"passedCount\":2,\"totalCount\":2,\"maxTimeMs\":35,\"maxMemoryKb\":null,"
                                + "\"compileOutput\":null}", MediaType.APPLICATION_JSON));

        JudgeResult result = adapter.judge(judgeRequest);

        // runner는 메모리를 측정하지 않아 maxMemoryKb는 항상 null이다.
        assertThat(result).isEqualTo(new JudgeResult(Verdict.AC, 2, 2, 35, null));
        server.verify();
    }

    @Test
    void judge_MLE는_RE로_매핑되고_maxMemoryKb가_null이어도_된다() {
        server.expect(requestTo(BASE_URL + "/internal/v1/judge"))
                .andRespond(withSuccess(
                        "{\"verdict\":\"MLE\",\"passedCount\":1,\"totalCount\":2,\"maxTimeMs\":120}",
                        MediaType.APPLICATION_JSON));

        JudgeResult result = adapter.judge(judgeRequest);

        assertThat(result.verdict()).isEqualTo(Verdict.RE);
        assertThat(result.passedCount()).isEqualTo(1);
        assertThat(result.maxMemoryKb()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"WA", "TLE", "RE", "CE"})
    void judge_나머지_verdict는_그대로_매핑된다(String runnerVerdict) {
        server.expect(requestTo(BASE_URL + "/internal/v1/judge"))
                .andRespond(withSuccess(
                        "{\"verdict\":\"" + runnerVerdict + "\",\"passedCount\":0,\"totalCount\":2}",
                        MediaType.APPLICATION_JSON));

        assertThat(adapter.judge(judgeRequest).verdict()).isEqualTo(Verdict.valueOf(runnerVerdict));
    }

    @Test
    void judge_계약_밖_verdict는_502_예외다() {
        server.expect(requestTo(BASE_URL + "/internal/v1/judge"))
                .andRespond(withSuccess(
                        "{\"verdict\":\"INTERNAL_ERROR\",\"passedCount\":0,\"totalCount\":2}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.judge(judgeRequest)).isInstanceOf(JudgeExecutionException.class);
    }

    @Test
    void judge_passedCount가_없으면_예외다() {
        server.expect(requestTo(BASE_URL + "/internal/v1/judge"))
                .andRespond(withSuccess("{\"verdict\":\"AC\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.judge(judgeRequest)).isInstanceOf(JudgeExecutionException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 429, 500})
    void judge_runner_에러코드는_전부_JudgeExecutionException이다(int status) {
        server.expect(requestTo(BASE_URL + "/internal/v1/judge"))
                .andRespond(withStatus(HttpStatus.valueOf(status)));

        assertThatThrownBy(() -> adapter.judge(judgeRequest))
                .isInstanceOf(JudgeExecutionException.class)
                .hasMessageNotContaining(TOKEN);
    }

    @Test
    void judge_빈_응답은_예외다() {
        server.expect(requestTo(BASE_URL + "/internal/v1/judge"))
                .andRespond(withSuccess());

        assertThatThrownBy(() -> adapter.judge(judgeRequest)).isInstanceOf(JudgeExecutionException.class);
    }

    // ---- run ----

    @Test
    void run_요청_바디가_계약대로_나가고_OK가_매핑된다() {
        server.expect(requestTo(BASE_URL + "/internal/v1/run"))
                .andExpect(header(RunnerConfig.TOKEN_HEADER, TOKEN))
                .andExpect(content().string(allOf(
                        containsString("\"language\":\"java\""),
                        containsString("\"stdin\":\"hello\""),
                        containsString("\"timeLimitMs\":2000"),
                        containsString("\"memoryLimitKb\":131072"))))
                .andRespond(withSuccess(
                        "{\"status\":\"OK\",\"stdout\":\"hi\\n\",\"stderr\":\"\",\"compileOutput\":null,\"timeMs\":40}",
                        MediaType.APPLICATION_JSON));

        RunResult result = adapter.run(runRequest);

        assertThat(result).isEqualTo(new RunResult(RunStatus.OK, "hi\n", "", null, 40));
    }

    @Test
    void run_MLE는_RE로_매핑된다() {
        server.expect(requestTo(BASE_URL + "/internal/v1/run"))
                .andRespond(withSuccess(
                        "{\"status\":\"MLE\",\"stdout\":\"\",\"stderr\":\"oom\",\"timeMs\":10}",
                        MediaType.APPLICATION_JSON));

        assertThat(adapter.run(runRequest).status()).isEqualTo(RunStatus.RE);
    }

    @Test
    void run_CE는_compileOutput을_담고_null_stdout은_빈문자열이_된다() {
        server.expect(requestTo(BASE_URL + "/internal/v1/run"))
                .andRespond(withSuccess(
                        "{\"status\":\"CE\",\"compileOutput\":\"error: ';' expected\"}", MediaType.APPLICATION_JSON));

        RunResult result = adapter.run(runRequest);

        assertThat(result.status()).isEqualTo(RunStatus.CE);
        assertThat(result.compileOutput()).isEqualTo("error: ';' expected");
        assertThat(result.stdout()).isEmpty();
        assertThat(result.stderr()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 429, 500})
    void run_runner_에러코드는_전부_JudgeExecutionException이다(int status) {
        server.expect(requestTo(BASE_URL + "/internal/v1/run"))
                .andRespond(withStatus(HttpStatus.valueOf(status)));

        assertThatThrownBy(() -> adapter.run(runRequest)).isInstanceOf(JudgeExecutionException.class);
    }

    @Test
    void run_계약_밖_status는_예외다() {
        server.expect(requestTo(BASE_URL + "/internal/v1/run"))
                .andRespond(withSuccess("{\"status\":\"WHAT\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.run(runRequest)).isInstanceOf(JudgeExecutionException.class);
    }

    // ---- 실제 소켓: 연결 실패 / 읽기 타임아웃 (RunnerConfig가 만든 RestClient) ----

    @Test
    void 연결_실패는_JudgeExecutionException이다() {
        // 열려 있지 않은 포트: 포트를 하나 잡았다가 바로 닫는다.
        int closedPort = freePort();
        RunnerJudgeAdapter unreachable = realAdapter("http://localhost:" + closedPort, 500, 500);

        assertThatThrownBy(() -> unreachable.judge(judgeRequest)).isInstanceOf(JudgeExecutionException.class);
    }

    @Test
    void 읽기_타임아웃이면_JudgeExecutionException이다() throws Exception {
        HttpServer slowServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        slowServer.createContext("/internal/v1/judge", exchange -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        slowServer.start();
        try {
            RunnerJudgeAdapter slow = realAdapter("http://localhost:" + slowServer.getAddress().getPort(), 500, 300);

            long start = System.nanoTime();
            assertThatThrownBy(() -> slow.judge(judgeRequest)).isInstanceOf(JudgeExecutionException.class);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertThat(elapsedMs).as("읽기 타임아웃(300ms)에 걸려 서버 응답(2000ms)을 기다리지 않아야 한다").isLessThan(1800);
        } finally {
            slowServer.stop(0);
        }
    }

    private RunnerJudgeAdapter realAdapter(String url, long connectTimeoutMs, long readTimeoutMs) {
        RunnerProperties properties = new RunnerProperties(url, TOKEN, connectTimeoutMs, readTimeoutMs);
        RestClient restClient = new RunnerConfig().runnerRestClient(RestClient.builder(), properties);
        return new RunnerJudgeAdapter(restClient, JUDGE_PROPERTIES);
    }

    private static int freePort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
