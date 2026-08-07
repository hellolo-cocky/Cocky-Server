package com.cocky.cockyserver.infra.judge0;

import com.cocky.cockyserver.domain.submission.judge.JudgeService;
import com.cocky.cockyserver.infra.stub.StubJudgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.web.client.RestClient;

/**
 * 채점 엔진 선택.
 * - JUDGE0_URL이 설정돼 있으면 → Judge0Adapter(실채점).
 * - JUDGE0_URL이 비어 있으면(미설정) → StubJudgeService 폴백. 학교 채점 VM 재구축(11월)
 *   전까지 Judge0 없이 개발이 막히지 않게 하기 위함 — ai.executor의 OPENAI_API_KEY 부재 시
 *   데모 모드 폴백과 동일한 패턴({@link com.cocky.cockyserver.ai.config.ExecutorConfig}).
 *
 * <p>{@link StubJudgeService}는 {@code new}로 직접 만들지 않고 {@link #stubJudgeService()}
 * 빈으로 등록해 Spring 컨테이너 관리 하에 둔다(향후 의존성 주입 필요 시 확장 가능하도록).
 * 이 경우 {@code StubJudgeService}가 {@link JudgeService}를 구현하므로 타입 기준 조회
 * 시 이 빈도 후보에 걸려({@code getBeansOfType(JudgeService.class)}가 2개를 반환)
 * {@code SubmissionService}처럼 {@code JudgeService} 하나만 기대하는 주입 지점에서
 * {@code NoUniqueBeanDefinitionException}이 날 수 있다 — 그래서 실제 채점에 쓰이는
 * {@link #judgeService} 빈에 {@link Primary @Primary}를 붙여 모호성을 없앤다.
 *
 * <p>{@code stubJudgeService} 빈은 {@link Lazy @Lazy}로 선언한다. {@code @Bean}은 기본이
 * 즉시 생성(non-lazy) 싱글턴이라, {@link ObjectProvider}로만 받으면 "주입 시점"은
 * {@link #judgeService} 팩토리 실행까지 늦춰지지만 "빈 생성 자체"는 늦춰지지 않는다 —
 * 컨테이너가 {@code refresh()}의 {@code preInstantiateSingletons()} 단계에서 이 빈을
 * 무조건 먼저 만들어 버린다. 그러면 JUDGE0_URL이 설정된(Judge0Adapter를 쓰는) 환경에서도
 * {@code StubJudgeService} 생성자의 기동 배너(WARN "스텁 활성화")가 찍혀, 실채점 중인데
 * 스텁이 켜져 있다는 오해를 준다(실측 확인 — {@code Judge0ConfigTest} 참고). {@code @Lazy}로
 * 실제로 {@link ObjectProvider#getObject()}가 호출될 때만 생성되게 막는다.
 */
@Configuration
@EnableConfigurationProperties(Judge0Properties.class)
public class Judge0Config {

    private static final Logger log = LoggerFactory.getLogger(Judge0Config.class);

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
    public LanguageMapper languageMapper() {
        return new LanguageMapper();
    }

    @Bean
    public Judge0Client judge0Client(RestClient.Builder restClientBuilder, Judge0Properties properties) {
        return new Judge0Client(restClientBuilder, properties);
    }

    /**
     * Judge0Adapter 미사용 구간을 위한 스텁. Spring 빈으로 등록해 두되, {@code @Lazy}로
     * 실제로 쓰이는 상황({@link #judgeService}가 {@link ObjectProvider#getObject()}로 이
     * 빈을 실제로 요청할 때)에서만 생성되게 한다 — 클래스 javadoc 참고.
     */
    @Bean
    @Lazy
    public StubJudgeService stubJudgeService() {
        return new StubJudgeService();
    }

    /**
     * {@code @Primary}: {@link #stubJudgeService}도 {@link JudgeService} 타입 후보라
     * 명시하지 않으면 {@code SubmissionService}의 {@code JudgeService} 주입이
     * {@code NoUniqueBeanDefinitionException}으로 깨진다 — 실채점에 쓰이는 이 빈을
     * 우선권자로 고정한다(클래스 javadoc 참고).
     */
    @Bean
    @Primary
    public JudgeService judgeService(Judge0Client judge0Client, LanguageMapper languageMapper,
                                     Judge0Properties properties,
                                     ObjectProvider<StubJudgeService> stubJudgeServiceProvider) {
        if (properties.url() == null || properties.url().isBlank()) {
            log.warn("JUDGE0_URL 미설정 — StubJudgeService로 폴백합니다.");
            return stubJudgeServiceProvider.getObject();
        }
        log.info("채점 엔진: Judge0Adapter (url={})", properties.url());
        return new Judge0Adapter(judge0Client, languageMapper, properties);
    }
}
