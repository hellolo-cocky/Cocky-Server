package com.cocky.cockyserver.infra.judge;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocky.cockyserver.domain.submission.judge.JudgeService;
import com.cocky.cockyserver.infra.judge0.Judge0Adapter;
import com.cocky.cockyserver.infra.runner.RunnerJudgeAdapter;
import com.cocky.cockyserver.infra.stub.StubJudgeService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * judge.engine 값에 따라 {@link JudgeService} 빈이 어떤 구현체로 주입되는지 검증하는 슬라이스 테스트.
 * 전체 {@code @SpringBootTest}는 무거워 {@link JudgeConfig}만 올린다.
 */
class JudgeConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(JudgeConfig.class);

    @Test
    void engine_미설정이면_기본값_stub이다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(JudgeService.class)).isInstanceOf(StubJudgeService.class);
        });
    }

    @Test
    void engine_stub이면_StubJudgeService가_주입된다() {
        contextRunner.withPropertyValues("judge.engine=stub").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(JudgeService.class)).isInstanceOf(StubJudgeService.class);
        });
    }

    @Test
    void engine_judge0이고_url이_있으면_Judge0Adapter가_주입된다() {
        contextRunner.withPropertyValues("judge.engine=judge0", "judge0.url=http://localhost:2358")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(JudgeService.class)).isInstanceOf(Judge0Adapter.class);
                });
    }

    @Test
    void engine_judge0이어도_url이_비어있으면_Stub으로_폴백한다() {
        contextRunner.withPropertyValues("judge.engine=judge0", "judge0.url=").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(JudgeService.class)).isInstanceOf(StubJudgeService.class);
        });
    }

    @Test
    void engine_stub이면_judge0_url이_있어도_Stub이다() {
        contextRunner.withPropertyValues("judge.engine=stub", "judge0.url=http://localhost:2358")
                .run(context -> assertThat(context.getBean(JudgeService.class))
                        .isInstanceOf(StubJudgeService.class));
    }

    @Test
    void engine_runner이고_url_token이_있으면_RunnerJudgeAdapter가_주입된다() {
        contextRunner.withPropertyValues(
                        "judge.engine=runner", "runner.url=http://localhost:8081", "runner.token=t")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(JudgeService.class)).isInstanceOf(RunnerJudgeAdapter.class);
                });
    }

    @Test
    void engine_runner인데_url이_비면_기동_실패한다() {
        contextRunner.withPropertyValues("judge.engine=runner", "runner.url=", "runner.token=t")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(
                            "judge.engine=runner이지만 RUNNER_URL/RUNNER_TOKEN이 설정되지 않았습니다.");
                });
    }

    @Test
    void engine_runner인데_token이_비면_기동_실패한다() {
        contextRunner.withPropertyValues("judge.engine=runner", "runner.url=http://localhost:8081", "runner.token=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 알_수_없는_engine_값이면_기동_실패한다() {
        contextRunner.withPropertyValues("judge.engine=docker").run(context -> assertThat(context).hasFailed());
    }

    /** 사용하지 않는 엔진 구현체(특히 스텁 기동 배너)가 eager 생성되면 안 된다. */
    @Test
    void 선택되지_않은_구현체는_생성되지_않는다() {
        contextRunner.withPropertyValues(
                        "judge.engine=runner", "runner.url=http://localhost:8081", "runner.token=t")
                .run(context -> {
                    assertThat(context.getBeanFactory().containsSingleton("stubJudgeService")).isFalse();
                    assertThat(context.getBeanFactory().containsSingleton("judge0Adapter")).isFalse();
                    assertThat(context.getBeanFactory().containsSingleton("judge0Client")).isFalse();
                });
    }
}
