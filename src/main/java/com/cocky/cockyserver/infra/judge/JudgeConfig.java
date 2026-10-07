package com.cocky.cockyserver.infra.judge;

import com.cocky.cockyserver.domain.submission.judge.JudgeService;
import com.cocky.cockyserver.infra.judge0.Judge0Adapter;
import com.cocky.cockyserver.infra.judge0.Judge0Config;
import com.cocky.cockyserver.infra.judge0.Judge0Properties;
import com.cocky.cockyserver.infra.runner.RunnerConfig;
import com.cocky.cockyserver.infra.runner.RunnerJudgeAdapter;
import com.cocky.cockyserver.infra.runner.RunnerProperties;
import com.cocky.cockyserver.infra.stub.StubConfig;
import com.cocky.cockyserver.infra.stub.StubJudgeService;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.web.client.RestClient;

/**
 * 채점 엔진 선택 — 엔진 공통의 중립 설정. {@code judge.engine}으로 {@link JudgeService} 구현체를 고른다.
 *
 * <ul>
 *   <li>{@code runner}: cocky-runner. runner.url/token이 비어 있으면 기동 실패.</li>
 *   <li>{@code judge0}: Judge0. judge0.url이 비어 있으면 스텁으로 폴백(기존 동작).</li>
 *   <li>{@code stub}(기본): 항상 가짜 결과. 채점 VM이 없는 개발/데모 환경용.</li>
 * </ul>
 *
 * <p>각 구현체 빈({@code Judge0Adapter}/{@code RunnerJudgeAdapter}/{@code StubJudgeService})은 자기
 * 설정 클래스가 {@code @Lazy}로 등록하고, 여기서는 {@link ObjectProvider}로 고른 하나만 실제로
 * 생성한다. 이 구현체들이 모두 {@link JudgeService} 타입 후보라 {@link #judgeService}에
 * {@link Primary}를 붙여 {@code SubmissionService} 같은 주입 지점의 모호성을 없앤다.
 */
@Configuration
@Import({Judge0Config.class, RunnerConfig.class, StubConfig.class})
@EnableConfigurationProperties(JudgeProperties.class)
public class JudgeConfig {

    private static final Logger log = LoggerFactory.getLogger(JudgeConfig.class);

    /**
     * 이 프로젝트는 spring-boot-starter-web이 아니라 spring-boot-starter-webmvc만 쓰는데,
     * Boot 4.1은 RestClient.Builder 자동구성을 별도 모듈(spring-boot-starter-restclient)로
     * 분리해놔서 webmvc 스타터만으로는 이 빈이 생기지 않는다. 의존성을 늘리는 대신 여기서
     * 직접 등록한다. 매 주입마다 새 Builder를 받도록 프로토타입 스코프로 둔다(빌더는
     * baseUrl/헤더 설정으로 상태를 바꾸는 1회용 객체라 싱글톤으로 공유하면 안 됨).
     */
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @Primary
    public JudgeService judgeService(JudgeProperties judgeProperties,
                                     Judge0Properties judge0Properties,
                                     RunnerProperties runnerProperties,
                                     ObjectProvider<Judge0Adapter> judge0AdapterProvider,
                                     ObjectProvider<RunnerJudgeAdapter> runnerAdapterProvider,
                                     ObjectProvider<StubJudgeService> stubProvider) {
        String engine = judgeProperties.engine() == null
                ? "" : judgeProperties.engine().trim().toLowerCase(Locale.ROOT);

        return switch (engine) {
            case "runner" -> {
                if (isBlank(runnerProperties.url()) || isBlank(runnerProperties.token())) {
                    throw new IllegalStateException(
                            "judge.engine=runner이지만 RUNNER_URL/RUNNER_TOKEN이 설정되지 않았습니다.");
                }
                log.info("채점 엔진: RunnerJudgeAdapter (url={})", runnerProperties.url());
                yield runnerAdapterProvider.getObject();
            }
            case "judge0" -> {
                if (isBlank(judge0Properties.url())) {
                    log.warn("judge.engine=judge0이지만 JUDGE0_URL 미설정 — StubJudgeService로 폴백합니다.");
                    yield stubProvider.getObject();
                }
                log.info("채점 엔진: Judge0Adapter (url={})", judge0Properties.url());
                yield judge0AdapterProvider.getObject();
            }
            case "stub", "" -> {
                log.warn("채점 엔진: StubJudgeService (judge.engine={})", engine.isEmpty() ? "(미설정)" : engine);
                yield stubProvider.getObject();
            }
            default -> throw new IllegalStateException(
                    "지원하지 않는 judge.engine 값입니다: '" + judgeProperties.engine() + "' (judge0 | runner | stub)");
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
