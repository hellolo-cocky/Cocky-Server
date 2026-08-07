package com.cocky.cockyserver.infra.stub;

import com.cocky.cockyserver.domain.submission.entity.Verdict;
import com.cocky.cockyserver.domain.submission.judge.JudgeRequest;
import com.cocky.cockyserver.domain.submission.judge.JudgeResult;
import com.cocky.cockyserver.domain.submission.judge.JudgeService;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link JudgeService}의 개발용 스텁 구현체 — 학교 채점 VM이 만료되어 11월 재구축 전까지
 * Judge0 없이 백엔드/프론트 개발이 막히지 않도록 존재한다.
 *
 * <p>실제 코드 실행/채점을 전혀 수행하지 않는다. 기본 동작은 전체 케이스 AC(만점)이고,
 * 제출 코드 첫 줄의 매직 주석으로만 다른 verdict를 흉내낸다. 스텁이라는 사실을 절대
 * 숨기지 않는다 — 기동 시 배너, 호출마다 WARN 로그를 남긴다(CLAUDE.md §8.5: 채점 엔진은
 * 12월 이후 자체 엔진으로 교체 예정이며, Judge0/스텁 어느 쪽 구현 세부사항도
 * {@link JudgeService} 인터페이스 밖으로 새면 안 된다).
 *
 * <p>주의: 채점 호출({@link #judge})은 {@code SubmissionService}가 아직 Submission
 * 엔티티를 저장하기 전에 실행되어 실제 제출(submission) PK가 없다. 그래서 로그에는 DB
 * 제출 ID 대신 호출마다 새로 발급하는 상관관계 ID(requestId)를 남긴다.
 */
public class StubJudgeService implements JudgeService {

    private static final Logger log = LoggerFactory.getLogger(StubJudgeService.class);

    /**
     * 제출 코드 첫 줄의 매직 주석으로 verdict를 강제한다. Java/C 스타일({@code //})과
     * Python 스타일({@code #}) 주석을 모두 인식한다.
     *
     * <p>{@link Verdict}의 6개 값 중 AC(기본 동작)와 PENDING(judge() 호출 전 초기 상태 —
     * {@code Submission} 생성 시에만 쓰이고 채점 결과로는 절대 나오지 않음, {@code Submission.java}
     * 참고)을 뺀 나머지 4개(WA/TLE/CE/RE)를 전부 매직 주석으로 재현한다 — 프론트가 렌더링해야
     * 하는 채점 상태 UI를 전부 커버하기 위함.
     *
     * <p>⚠️ 이 매직 주석 목록(WA/TLE/CE/RE)은 임시 초안이다 — 팀 검토 필요.
     */
    private static final Pattern MAGIC_COMMENT =
            Pattern.compile("^\\s*(?://|#)\\s*STUB:(WA|TLE|CE|RE)\\s*$", Pattern.CASE_INSENSITIVE);

    private static final int DUMMY_TIME_MS = 120;
    private static final int DUMMY_MEMORY_KB = 2048;
    private static final int DUMMY_TLE_TIME_MS = 5000;

    public StubJudgeService() {
        log.warn("============================================================");
        log.warn(" [STUB] StubJudgeService 활성화 — Judge0가 연동되어 있지 않습니다.");
        log.warn(" [STUB] 모든 채점 결과는 가짜(기본 AC/만점)이며 코드가 실제로 실행되지 않습니다.");
        log.warn(" [STUB] JUDGE0_URL을 설정하면 실제 Judge0 채점으로 전환됩니다.");
        log.warn("============================================================");
    }

    @Override
    public JudgeResult judge(JudgeRequest request) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        int total = request.cases().size();

        Verdict forced = extractForcedVerdict(request.code());
        JudgeResult result = forced == null
                ? new JudgeResult(Verdict.AC, total, total, DUMMY_TIME_MS, DUMMY_MEMORY_KB)
                : buildForcedResult(forced, total);

        log.warn("[STUB 채점] requestId={}, language={}, verdict={} ({}/{} 통과) — 가짜 채점 결과입니다(Judge0 미연동).",
                requestId, request.language(), result.verdict(), result.passedCount(), result.totalCount());

        return result;
    }

    private Verdict extractForcedVerdict(String code) {
        if (code == null) {
            return null;
        }
        String firstLine = code.lines().findFirst().orElse("");
        Matcher matcher = MAGIC_COMMENT.matcher(firstLine);
        if (!matcher.matches()) {
            return null;
        }
        return Verdict.valueOf(matcher.group(1).toUpperCase());
    }

    private JudgeResult buildForcedResult(Verdict verdict, int total) {
        return switch (verdict) {
            case WA -> new JudgeResult(Verdict.WA, partialPass(total), total, DUMMY_TIME_MS, DUMMY_MEMORY_KB);
            case TLE -> new JudgeResult(Verdict.TLE, 0, total, DUMMY_TLE_TIME_MS, DUMMY_MEMORY_KB);
            case CE -> new JudgeResult(Verdict.CE, 0, total, null, null);
            // RE(런타임 에러)도 CE처럼 정상 케이스를 하나도 통과하지 못한 것으로 본다 — 단,
            // CE와 달리 컴파일은 성공해 실행은 됐다는 차이를 살려 시간/메모리는 더미값을 채운다.
            case RE -> new JudgeResult(Verdict.RE, 0, total, DUMMY_TIME_MS, DUMMY_MEMORY_KB);
            default -> throw new IllegalStateException("StubJudgeService가 처리할 수 없는 매직 주석 verdict: " + verdict);
        };
    }

    /**
     * WA를 "일부만 통과"로 흉내내기 위한 통과 개수.
     *
     * <p>total/2였다가 total-1로 변경(팀 피드백) — 테스트케이스가 1개인 문제에서 total/2는
     * 0이 되어 CE(0/1)와 화면상 구분이 안 됐다. total-1이면 케이스가 2개 이상일 때 "거의 다
     * 맞았지만 1개는 틀림"이 되어 WA임이 더 뚜렷하다. total이 1이면 부분 통과 자체가
     * 불가능하므로 0통과는 그대로 둔다(WA와 CE가 1케이스 문제에서는 로그의 verdict 값으로만
     * 구분됨 — 프론트 화면에도 verdict 라벨이 별도로 노출된다는 전제).
     *
     * <p>⚠️ 이 통과 개수 정책도 임시 초안이다 — 팀 검토 필요.
     */
    private int partialPass(int total) {
        return total <= 1 ? 0 : total - 1;
    }
}
