package com.cocky.cockyserver.infra.judge0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.cocky.cockyserver.infra.judge0.dto.Judge0SubmissionRequest;
import com.cocky.cockyserver.infra.judge0.dto.Judge0SubmissionResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Judge0Client가 실제로 내보내는 HTTP 바디를 검증한다. 과거 classic
 * {@code MappingJackson2HttpMessageConverter}를 수동 주입했을 때는 진단 로그(직렬화 결과)는
 * 정상이었지만 RestClient가 실제로 전송하는 바디는 비어 있었다 — 그 회귀를 잡기 위한 테스트.
 */
class Judge0ClientTest {

    @Test
    void submitSendsSourceCodeAndLanguageIdInActualHttpBody() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Judge0Properties properties = new Judge0Properties("http://localhost:2358", "");
        Judge0Client client = new Judge0Client(builder, properties);

        server.expect(requestTo(containsString("/submissions")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(allOf(
                        containsString("\"source_code\":\"cHJpbnQoMSsxKQ==\""),
                        containsString("\"language_id\":71"))))
                .andRespond(withSuccess(
                        "{\"status\":{\"id\":3,\"description\":\"Accepted\"},\"time\":\"0.01\",\"memory\":1000}",
                        MediaType.APPLICATION_JSON));

        Judge0SubmissionRequest request = new Judge0SubmissionRequest(
                "cHJpbnQoMSsxKQ==", 71, "", "", 2.0, 131072);

        Judge0SubmissionResult result = client.submit(request);

        assertThat(result.status().id()).isEqualTo(3);
        server.verify();
    }
}