package com.cocky.cockyserver.domain.round;

/**
 * 주간 주제(topic.week_order) 순환 규칙. 8주 순환이므로 8주차 다음은 1주차로 돌아간다.
 *
 * <p>회차 스케줄링과는 별개의 도메인 규칙("다음 주제가 뭔지")이라 별도 정책 객체로 분리했다.
 * {@link com.cocky.cockyserver.domain.round.service.RoundSchedulerService}(다음 회차 계획)와
 * {@link com.cocky.cockyserver.domain.feedback.service.FeedbackService}(다음 기간 예습 추천)가
 * 함께 참조한다. 상태 없는 순수 계산이라 빈으로 등록하지 않고 static 유틸리티로 둔다.
 */
public final class TopicRotationPolicy {

    private static final int TOTAL_TOPICS = 8;

    private TopicRotationPolicy() {
    }

    /** 현재 주차(1~8) 다음 주차를 반환한다. 8 다음은 1로 순환한다. */
    public static int next(int currentWeekOrder) {
        return (currentWeekOrder % TOTAL_TOPICS) + 1;
    }
}
