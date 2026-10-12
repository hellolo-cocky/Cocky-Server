package com.cocky.cockyserver.domain.feedback.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocky.cockyserver.ai.dto.Period;
import com.cocky.cockyserver.domain.feedback.FeedbackPeriod;
import com.cocky.cockyserver.domain.feedback.dto.PeriodFeedbackResponse;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * FeedbackPeriod(API 값) ↔ ai.dto.Period(AI 모듈 계약) 변환이 누락 없이 1:1로 대응하는지 확인한다.
 * 변환부가 switch 식이라 새 값 추가는 컴파일 에러로도 드러나지만, "값 이름이 같아야 API 응답이
 * 바뀌지 않는다"는 계약까지는 컴파일러가 못 잡으므로 여기서 고정한다.
 */
class FeedbackPeriodMappingTest {

    @ParameterizedTest
    @EnumSource(FeedbackPeriod.class)
    void FeedbackPeriod의_모든_값이_이름이_같은_AI_Period로_변환된다(FeedbackPeriod period) {
        Period ai = FeedbackService.toAiPeriod(period);

        assertThat(ai).isNotNull();
        assertThat(ai.name()).isEqualTo(period.name());
    }

    @ParameterizedTest
    @EnumSource(Period.class)
    void AI_Period의_모든_값이_이름이_같은_FeedbackPeriod로_역변환된다(Period aiPeriod) {
        FeedbackPeriod period = PeriodFeedbackResponse.fromAiPeriod(aiPeriod);

        assertThat(period).isNotNull();
        assertThat(period.name()).isEqualTo(aiPeriod.name());
    }

    @Test
    void 두_enum의_값_집합이_같아_매핑에서_빠지는_값이_없다() {
        Set<String> api = names(FeedbackPeriod.values());
        Set<String> ai = names(Period.values());

        assertThat(api).containsExactlyInAnyOrderElementsOf(ai);
        // API 계약: 요청·응답에 쓰이는 문자열 그대로.
        assertThat(api).containsExactlyInAnyOrder("ROUND", "WEEKLY", "MONTHLY");
    }

    @Test
    void 정변환과_역변환을_왕복해도_원래_값으로_돌아온다() {
        EnumSet.allOf(FeedbackPeriod.class).forEach(period ->
                assertThat(PeriodFeedbackResponse.fromAiPeriod(FeedbackService.toAiPeriod(period)))
                        .isEqualTo(period));
    }

    private static Set<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(java.util.stream.Collectors.toSet());
    }
}
