package com.cocky.cockyserver.domain.round;

import java.util.List;
import java.util.Map;

/**
 * 임시 초안 — 팀 검토 필요.
 *
 * <p>주제(topic.name)별 회차 세부 유형(roundSubtype) 후보 목록. 스케줄러가
 * 매일 이 목록에서 최근 출제와 안 겹치는 것 하나를 골라 AI 생성 요청에 넘긴다.
 */
public final class RoundSubtypes {

    public static final Map<String, List<String>> CANDIDATES = Map.ofEntries(
            Map.entry("구현", List.of("시뮬레이션", "완전탐색", "브루트포스")),
            Map.entry("자료구조", List.of("스택", "큐", "배열 조작")),
            Map.entry("정렬·탐색", List.of("이분탐색", "정렬응용", "투포인터")),
            Map.entry("그리디", List.of("구간스케줄링", "우선순위선택", "교환논증")),
            Map.entry("DFS·BFS", List.of("그래프탐색", "백트래킹", "연결요소")),
            Map.entry("동적 프로그래밍", List.of("메모이제이션", "타뷸레이션", "상태압축")),
            Map.entry("그래프", List.of("최단경로", "최소신장트리", "위상정렬")),
            Map.entry("문자열", List.of("패턴매칭", "파싱", "해싱"))
    );

    private RoundSubtypes() {
    }
}
