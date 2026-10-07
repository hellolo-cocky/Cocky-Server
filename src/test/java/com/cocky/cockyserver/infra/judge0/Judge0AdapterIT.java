package com.cocky.cockyserver.infra.judge0;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cocky.cockyserver.domain.problem.entity.Language;
import com.cocky.cockyserver.domain.submission.entity.Verdict;
import com.cocky.cockyserver.domain.submission.judge.JudgeRequest;
import com.cocky.cockyserver.domain.submission.judge.JudgeResult;
import com.cocky.cockyserver.domain.submission.judge.TestCaseIO;
import com.cocky.cockyserver.infra.judge.JudgeProperties;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * 실제 Judge0 인스턴스가 필요한 수동 검증 — language_id 매핑/base64 인코딩/status 매핑이
 * 진짜로 맞는지는 mock으로는 확인할 수 없어 여기서만 확인한다.
 *
 * <p>기본 {@code ./gradlew test}에서는 제외된다(judge0-it 태그, build.gradle 참고). 로컬에서
 * Judge0를 띄운 뒤 {@code ./gradlew test --tests "*Judge0AdapterIT" -PincludeJudge0IT}로 실행.
 *
 * <p>Spring 컨텍스트를 띄우지 않으므로 application.yml의 judge0.* 플레이스홀더는 관여하지
 * 않는다 — 환경변수 {@code JUDGE0_URL}/{@code JUDGE0_TOKEN}을 직접 읽는다(둘 다 없으면
 * application.yml 기본값과 동일하게 localhost:2358, 토큰 없음으로 동작).
 */
@Tag("judge0-it")
class Judge0AdapterIT {

    private final Judge0Properties properties = new Judge0Properties(
            envOrDefault("JUDGE0_URL", "http://localhost:2358"),
            envOrDefault("JUDGE0_TOKEN", ""));
    private final JudgeProperties judgeProperties = new JudgeProperties("judge0", 2000, 131072);
    private final Judge0Client client = new Judge0Client(RestClient.builder(), properties);
    private final LanguageMapper languageMapper = new LanguageMapper();
    private final Judge0Adapter adapter = new Judge0Adapter(client, languageMapper, judgeProperties);

    @Test
    void pythonCorrectAnswerIsAccepted() {
        JudgeRequest request = new JudgeRequest(Language.PYTHON, "print(1 + 1)",
                List.of(new TestCaseIO("", "2\n")));

        JudgeResult result = adapter.judge(request);

        assertEquals(Verdict.AC, result.verdict());
        assertEquals(1, result.passedCount());
        assertEquals(1, result.totalCount());
    }

    @Test
    void pythonWrongAnswerIsRejected() {
        JudgeRequest request = new JudgeRequest(Language.PYTHON, "print(1 + 1)",
                List.of(new TestCaseIO("", "5\n")));

        JudgeResult result = adapter.judge(request);

        assertEquals(Verdict.WA, result.verdict());
        assertEquals(0, result.passedCount());
        assertEquals(1, result.totalCount());
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}
