package com.cocky.cockyserver.domain.round;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TopicRotationPolicyTest {

    @Test
    void 팔주차_다음은_일주차로_순환한다() {
        assertThat(TopicRotationPolicy.next(8)).isEqualTo(1);
    }

    @Test
    void 중간_주차는_그냥_1_증가한다() {
        assertThat(TopicRotationPolicy.next(1)).isEqualTo(2);
        assertThat(TopicRotationPolicy.next(4)).isEqualTo(5);
        assertThat(TopicRotationPolicy.next(7)).isEqualTo(8);
    }
}
