package com.cocky.cockyserver.domain.round;

/**
 * 주제(topic.week_order 컬럼, 코드상 topicOrder) 순환 규칙. 총 8개 주제가 회차마다 순차로
 * 순환하며, 8번째 다음은 1번으로 돌아간다.
 *
 * <p>⚠️ "주간"이 아니라 "회차별" 순환이다 — 스케줄러가 매일(일요일 제외) 회차를 생성할 때마다
 * 이 정책이 한 칸씩 넘어가므로, 8개 주제는 달력상 약 8~9일 만에 소진된다("1주=1주제"로 읽지
 * 말 것. 2026-09 라운드 주기 결정 A안 논의 참고).
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

    /** 현재 순번(1~8) 다음 순번을 반환한다. 8 다음은 1로 순환한다. */
    public static int next(int currentTopicOrder) {
        return (currentTopicOrder % TOTAL_TOPICS) + 1;
    }
}
