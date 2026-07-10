package com.cocky.cockyserver.ai.service;

import com.cocky.cockyserver.ai.client.OpenAiClient;
import com.cocky.cockyserver.ai.config.AiProperties;
import com.cocky.cockyserver.ai.dto.Difficulty;
import com.cocky.cockyserver.ai.dto.ExampleIo;
import com.cocky.cockyserver.ai.dto.ExecRequest;
import com.cocky.cockyserver.ai.dto.ExecResult;
import com.cocky.cockyserver.ai.dto.GeneratedProblem;
import com.cocky.cockyserver.ai.dto.GenerationItem;
import com.cocky.cockyserver.ai.dto.GenerationOutcome;
import com.cocky.cockyserver.ai.dto.GenerationRequest;
import com.cocky.cockyserver.ai.dto.Language;
import com.cocky.cockyserver.ai.exec.DemoExecutor;
import com.cocky.cockyserver.ai.port.CodeExecutor;
import com.cocky.cockyserver.ai.port.ProblemGenerator;
import com.cocky.cockyserver.ai.prompt.PromptTemplates;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 문제 생성 오케스트레이션: 생성 → 중복검사 → 정답검증 → 난이도확인, 조합당 재시도.
 */
public class ProblemGeneratorService implements ProblemGenerator {

    private static final Logger log = LoggerFactory.getLogger(ProblemGeneratorService.class);

    private final OpenAiClient openAi;
    private final CodeExecutor executor;
    private final SimilarityChecker similarity;
    private final AiProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    public ProblemGeneratorService(OpenAiClient openAi, CodeExecutor executor,
                                   SimilarityChecker similarity, AiProperties props) {
        this.openAi = openAi;
        this.executor = executor;
        this.similarity = similarity;
        this.props = props;
    }

    @Override
    public GenerationOutcome generate(GenerationRequest request) {
        List<GenerationItem> items = new ArrayList<>();
        List<String> seenStatements = new ArrayList<>(request.pastStatements());
        for (Language lang : request.languages()) {
            for (Difficulty diff : request.difficulties()) {
                GenerationItem item = generateOne(request, lang, diff, seenStatements);
                items.add(item);
                if (item.success()) {
                    seenStatements.add(item.problem().statement());
                }
            }
        }
        return new GenerationOutcome(items);
    }

    /**
     * 조합 하나 생성. 재시도 소진 시 예외 대신 실패 레코드 반환
     * — 다른 조합의 성공분을 보존한다(백엔드 ai_generation_log 부분 저장 대응).
     */
    private GenerationItem generateOne(GenerationRequest req, Language lang, Difficulty diff,
                                       List<String> seenStatements) {
        int maxRetries = props.generation().maxRetries();
        String lastReason = "알 수 없음";
        Parsed lastFailed = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // 재시도면 직전 실패 사유를 프롬프트에 붙여 같은 실수 재생산을 막는다.
                String feedback = attempt == 1 ? null : lastReason;
                String json = openAi.chatJson(props.models().generation(),
                        PromptTemplates.GENERATION_SYSTEM,
                        PromptTemplates.generationUser(req, lang, diff, feedback));
                Parsed parsed = parse(json);

                if (similarity.isDuplicate(parsed.statement, seenStatements,
                        props.generation().similarityThreshold())) {
                    lastReason = "기존 문제와 유사도 초과";
                    lastFailed = parsed;
                    log.info("[{}/{}] 재생성 — {} (시도 {})", lang, diff, lastReason, attempt);
                    continue;
                }

                VerifyOutcome verified = verifyExamples(lang, parsed);
                if (!verified.ok()) {
                    // 백지 재생성 전에 1회 수리: 지문·예제 고정, answerCode만 교정.
                    Parsed repaired = tryRepair(lang, parsed, verified.reason());
                    VerifyOutcome reverified = repaired == null ? null : verifyExamples(lang, repaired);
                    if (reverified == null || !reverified.ok()) {
                        lastReason = reverified != null ? reverified.reason() : verified.reason();
                        lastFailed = parsed;
                        log.info("[{}/{}] 재생성 — 수리 불발, {} (시도 {})", lang, diff, lastReason, attempt);
                        continue;
                    }
                    log.info("[{}/{}] 정답 코드 수리로 통과 (시도 {})", lang, diff, attempt);
                    parsed = repaired;
                    verified = reverified;
                }

                recheckDifficulty(diff, parsed.statement);
                GeneratedProblem problem = new GeneratedProblem(lang, diff, parsed.title,
                        parsed.statement, verified.examples(), parsed.answerCode, req.roundSubtype());
                return GenerationItem.success(problem, attempt);
            } catch (RuntimeException e) {
                lastReason = e.getMessage();
                log.warn("[{}/{}] 생성 시도 {} 실패: {}", lang, diff, attempt, e.getMessage());
            }
        }
        log.error("[{}/{}] 재시도 {}회 소진 — 실패 조합으로 보고(성공분 보존): {}",
                lang, diff, maxRetries, lastReason);
        if (lastFailed != null) {
            // 사후 분석용 — 실패 문제의 지문이 없으면 불일치 원인 역추정이 안 된다.
            log.error("[{}/{}] 마지막 실패 문제: 제목=[{}] 지문=[{}]",
                    lang, diff, lastFailed.title, abbreviate(lastFailed.statement, 400));
        }
        return GenerationItem.failure(lang, diff, maxRetries, lastReason);
    }

    /** 각 예제 input으로 정답 코드를 실행해 declared output과 일치하면 실행 출력으로 확정. */
    private VerifyOutcome verifyExamples(Language lang, Parsed parsed) {
        if (executor instanceof DemoExecutor) {
            // 로컬 툴체인 부재 등 — 실행 검증 생략(선언된 예제 신뢰). 운영에서 local 실행기 권장.
            log.warn("데모 실행기 사용 중 — 정답 실행 검증을 생략한다");
            return VerifyOutcome.ok(parsed.examples);
        }
        List<ExampleIo> confirmed = new ArrayList<>();
        for (ExampleIo ex : parsed.examples) {
            ExecResult r = executor.run(new ExecRequest(lang, parsed.answerCode, ex.input()));
            if (!r.success()) {
                String detail = r.timedOut() ? "타임아웃"
                        : r.compileError() != null ? abbreviate(r.compileError())
                        : "비정상 종료(exit=" + r.exitCode() + ")";
                return VerifyOutcome.fail("정답 코드 실행 실패 — " + detail);
            }
            String expected = normalize(ex.output());
            String actual = normalize(r.stdout());
            if (!expected.equals(actual)) {
                // 비교에 쓴 정규화 값을 그대로 찍는다 — 공백/개행 차이도 escape로 가시화.
                return VerifyOutcome.fail("정답 코드 출력 불일치 — 기대=[%s] 실제=[%s]"
                        .formatted(visible(expected), visible(actual)));
            }
            confirmed.add(new ExampleIo(ex.input(), r.stdout().strip()));
        }
        if (confirmed.isEmpty()) {
            return VerifyOutcome.fail("검증할 예제가 없음");
        }
        return VerifyOutcome.ok(confirmed);
    }

    /** 예제 검증 결과. reason이 null이면 성공, 아니면 실패 사유(원인별 구분). */
    private record VerifyOutcome(List<ExampleIo> examples, String reason) {
        boolean ok() {
            return reason == null;
        }

        static VerifyOutcome ok(List<ExampleIo> examples) {
            return new VerifyOutcome(examples, null);
        }

        static VerifyOutcome fail(String reason) {
            return new VerifyOutcome(null, reason);
        }
    }

    /** 출력 불일치 로그용: 제어문자를 escape로 가시화하고 120자 제한. 원문 공백 보존. */
    private static String visible(String s) {
        String escaped = s.replace("\\", "\\\\").replace("\n", "\\n")
                .replace("\r", "\\r").replace("\t", "\\t");
        return escaped.length() <= 120 ? escaped : escaped.substring(0, 117) + "...";
    }

    /**
     * 검증 실패한 answerCode를 지문·예제 고정 상태로 1회 수리 시도.
     * 실패(호출 오류·빈 코드)면 null — 호출부가 백지 재생성으로 폴백한다.
     */
    private Parsed tryRepair(Language lang, Parsed parsed, String failReason) {
        try {
            String json = openAi.chatJson(props.models().generation(),
                    PromptTemplates.REPAIR_SYSTEM,
                    PromptTemplates.repairUser(lang, parsed.statement, parsed.examples,
                            parsed.answerCode, failReason));
            String code = mapper.readTree(json).path("answerCode").asText("");
            if (code.isBlank()) {
                return null;
            }
            return new Parsed(parsed.title, parsed.statement, code, parsed.examples);
        } catch (com.fasterxml.jackson.core.JsonProcessingException | RuntimeException e) {
            log.debug("정답 코드 수리 호출 실패: {}", e.getMessage());
            return null;
        }
    }

    /** 컴파일/실행 오류 로그용 축약: 개행 제거 후 120자 제한. */
    private static String abbreviate(String s) {
        return abbreviate(s, 120);
    }

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        String oneLine = s.strip().replaceAll("\\s*\\R\\s*", " ⏎ ");
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max - 3) + "...";
    }

    private void recheckDifficulty(Difficulty requested, String statement) {
        try {
            String answer = openAi.chatText(props.models().generation(),
                    PromptTemplates.DIFFICULTY_SYSTEM,
                    PromptTemplates.difficultyUser(statement)).strip().toUpperCase();
            Difficulty rated = Difficulty.valueOf(answer);
            if (rated.ordinal() < requested.ordinal()) {
                // 운영 규칙: 낮게 나와도 허용하되 로그만 남긴다.
                log.info("난이도 자문 낮음: 요청={} 자문={} (허용)", requested, rated);
            }
        } catch (RuntimeException e) {
            // 자문 파싱 실패(예: 예상 밖 응답)나 호출 오류는 비치명 — 원 난이도 유지.
            log.debug("난이도 자문 스킵: {}", e.getMessage());
        }
    }

    private Parsed parse(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            String title = root.path("title").asText("");
            String statement = root.path("statement").asText("");
            String answerCode = root.path("answerCode").asText("");
            if (statement.isBlank() || answerCode.isBlank()) {
                throw new IllegalStateException("생성 JSON 필수 필드 누락");
            }
            List<ExampleIo> examples = new ArrayList<>();
            for (JsonNode ex : root.path("examples")) {
                examples.add(new ExampleIo(ex.path("input").asText(""), ex.path("output").asText("")));
            }
            if (examples.isEmpty()) {
                throw new IllegalStateException("예제가 없음");
            }
            return new Parsed(title, statement, answerCode, examples);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("생성 JSON 파싱 실패: " + e.getMessage(), e);
        }
    }

    private String normalize(String s) {
        return s == null ? "" : s.strip().replace("\r\n", "\n");
    }

    private record Parsed(String title, String statement, String answerCode, List<ExampleIo> examples) {
    }
}
