package com.cocky.cockyserver.infra.stub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.cocky.cockyserver.domain.problem.entity.Language;
import com.cocky.cockyserver.domain.submission.entity.Verdict;
import com.cocky.cockyserver.domain.submission.judge.JudgeRequest;
import com.cocky.cockyserver.domain.submission.judge.JudgeResult;
import com.cocky.cockyserver.domain.submission.judge.TestCaseIO;
import java.util.List;
import org.junit.jupiter.api.Test;

class StubJudgeServiceTest {

    private final StubJudgeService stub = new StubJudgeService();

    private static List<TestCaseIO> cases(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new TestCaseIO("in" + i, "out" + i))
                .toList();
    }

    @Test
    void 매직주석이_없으면_전체_통과로_AC를_반환한다() {
        JudgeRequest request = new JudgeRequest(Language.PYTHON, "print(1)", cases(3));

        JudgeResult result = stub.judge(request);

        assertEquals(Verdict.AC, result.verdict());
        assertEquals(3, result.passedCount());
        assertEquals(3, result.totalCount());
    }

    @Test
    void 자바_스타일_주석으로_WA를_강제할_수_있다() {
        String code = "//STUB:WA\npublic class Main {}";
        JudgeRequest request = new JudgeRequest(Language.JAVA, code, cases(4));

        JudgeResult result = stub.judge(request);

        assertEquals(Verdict.WA, result.verdict());
        // total-1 정책 — 4케이스면 3개는 통과, 마지막 1개만 틀린 것으로 흉내낸다.
        assertEquals(3, result.passedCount());
        assertEquals(4, result.totalCount());
    }

    @Test
    void 테스트케이스가_1개뿐이면_WA도_0통과다() {
        String code = "//STUB:WA\npublic class Main {}";
        JudgeRequest request = new JudgeRequest(Language.JAVA, code, cases(1));

        JudgeResult result = stub.judge(request);

        assertEquals(Verdict.WA, result.verdict());
        assertEquals(0, result.passedCount());
    }

    @Test
    void RE를_강제할_수_있고_CE와_달리_시간_메모리는_채워진다() {
        String code = "//STUB:RE\npublic class Main {}";
        JudgeRequest request = new JudgeRequest(Language.JAVA, code, cases(3));

        JudgeResult result = stub.judge(request);

        assertEquals(Verdict.RE, result.verdict());
        assertEquals(0, result.passedCount());
        assertNotNull(result.maxTimeMs());
        assertNotNull(result.maxMemoryKb());
    }

    @Test
    void 파이썬_스타일_주석으로_TLE를_강제할_수_있다() {
        String code = "#STUB:TLE\nwhile True: pass";
        JudgeRequest request = new JudgeRequest(Language.PYTHON, code, cases(2));

        JudgeResult result = stub.judge(request);

        assertEquals(Verdict.TLE, result.verdict());
        assertEquals(0, result.passedCount());
    }

    @Test
    void CE를_강제하면_시간_메모리는_null이다() {
        String code = "//STUB:CE\nint main() {";
        JudgeRequest request = new JudgeRequest(Language.C, code, cases(2));

        JudgeResult result = stub.judge(request);

        assertEquals(Verdict.CE, result.verdict());
        assertEquals(0, result.passedCount());
        assertNull(result.maxTimeMs());
        assertNull(result.maxMemoryKb());
    }

    @Test
    void 첫줄이_아니면_매직주석은_무시된다() {
        String code = "public class Main {\n//STUB:WA\n}";
        JudgeRequest request = new JudgeRequest(Language.JAVA, code, cases(2));

        JudgeResult result = stub.judge(request);

        assertEquals(Verdict.AC, result.verdict());
        assertEquals(2, result.passedCount());
    }
}
