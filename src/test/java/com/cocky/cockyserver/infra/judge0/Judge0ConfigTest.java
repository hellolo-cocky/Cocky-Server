package com.cocky.cockyserver.infra.judge0;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocky.cockyserver.domain.submission.judge.JudgeService;
import com.cocky.cockyserver.infra.stub.StubJudgeService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * judge0.url 값에 따라 {@link JudgeService} 빈이 {@link Judge0Adapter}/{@link StubJudgeService}
 * 중 어느 쪽으로 주입되는지 검증하는 슬라이스 테스트. {@code StubJudgeServiceTest}(순수 단위
 * 테스트)는 스텁 로직 자체만 검증할 뿐 빈 선택 분기는 전혀 건드리지 않아서, 이 분기가 실제로
 * 깨져도 그 테스트는 계속 통과한다 — 그래서 여기서 {@link Judge0Config}만 별도로 올려
 * 분기를 확인한다. 전체 {@code @SpringBootTest}는 무거워 쓰지 않는다.
 */
class Judge0ConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(Judge0Config.class);

    @Test
    void judge0_url이_비어있으면_StubJudgeService가_주입된다() {
        contextRunner.withPropertyValues(
                        "judge0.url=",
                        "judge0.token=",
                        "judge0.default-time-limit-ms=2000",
                        "judge0.default-memory-limit-kb=131072")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(JudgeService.class)).isInstanceOf(StubJudgeService.class);
                });
    }

    @Test
    void judge0_url이_설정되면_Judge0Adapter가_주입된다() {
        contextRunner.withPropertyValues(
                        "judge0.url=http://localhost:2358",
                        "judge0.token=",
                        "judge0.default-time-limit-ms=2000",
                        "judge0.default-memory-limit-kb=131072")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(JudgeService.class)).isInstanceOf(Judge0Adapter.class);
                });
    }

    /**
     * StubJudgeService가 JudgeService 타입 후보로 잡혀 모호성이 생길 수 있는 지점 —
     * {@code judgeService} 빈의 {@code @Primary}로 해결됐는지 명시적으로 확인한다.
     * (getBeansOfType은 후보를 세기 위해 두 빈을 전부 생성시키므로 여기서만 쓴다.)
     */
    @Test
    void JudgeService_타입_후보가_2개여도_Primary_덕분에_getBean은_모호하지_않다() {
        contextRunner.withPropertyValues(
                        "judge0.url=",
                        "judge0.token=",
                        "judge0.default-time-limit-ms=2000",
                        "judge0.default-memory-limit-kb=131072")
                .run(context -> {
                    assertThat(context.getBeansOfType(JudgeService.class)).hasSize(2);
                    assertThat(context.getBean(JudgeService.class)).isInstanceOf(StubJudgeService.class);
                });
    }

    /**
     * ObjectProvider는 judgeService() 팩토리 "주입 시점"만 늦출 뿐, {@code @Bean}은 기본이
     * 즉시 생성(non-lazy) 싱글턴이라 {@code stubJudgeService}가 컨테이너 refresh 시점에
     * 먼저 만들어질 수 있다 — 그러면 Judge0Adapter를 쓰는 환경에서도 스텁 기동 배너가
     * 찍혀 오해를 준다(리뷰 지적, 실측으로 재현 확인함). {@code @Lazy}로 실제로 필요할 때만
     * 생성되는지 여기서 검증한다.
     */
    @Test
    void judge0_url이_설정되면_stubJudgeService는_생성되지_않는다() {
        contextRunner.withPropertyValues(
                        "judge0.url=http://localhost:2358",
                        "judge0.token=",
                        "judge0.default-time-limit-ms=2000",
                        "judge0.default-memory-limit-kb=131072")
                .run(context -> assertThat(context.getBeanFactory().containsSingleton("stubJudgeService"))
                        .as("Judge0Adapter 사용 환경에서는 stubJudgeService가 eager 생성되면 안 된다")
                        .isFalse());
    }
}