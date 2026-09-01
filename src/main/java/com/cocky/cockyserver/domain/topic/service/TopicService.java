package com.cocky.cockyserver.domain.topic.service;

import com.cocky.cockyserver.domain.round.TopicRotationPolicy;
import com.cocky.cockyserver.domain.round.entity.Round;
import com.cocky.cockyserver.domain.round.exception.RoundNotFoundException;
import com.cocky.cockyserver.domain.round.repository.RoundRepository;
import com.cocky.cockyserver.domain.round.service.RoundService;
import com.cocky.cockyserver.domain.topic.dto.TopicResponse;
import com.cocky.cockyserver.domain.topic.entity.Topic;
import com.cocky.cockyserver.domain.topic.exception.TopicNotFoundException;
import com.cocky.cockyserver.domain.topic.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TopicService {

    private final RoundService roundService;
    private final RoundRepository roundRepository;
    private final TopicRepository topicRepository;

    @Transactional(readOnly = true)
    public TopicResponse getCurrentTopic() {
        return TopicResponse.from(resolveCurrentRound().getTopic());
    }

    /** 다음 주제 topicOrder 계산은 {@link TopicRotationPolicy}(RoundSchedulerService와 공유하는 도메인 규칙)를 따른다. */
    @Transactional(readOnly = true)
    public TopicResponse getNextTopic() {
        int currentTopicOrder = resolveCurrentRound().getTopic().getTopicOrder();
        int nextTopicOrder = TopicRotationPolicy.next(currentTopicOrder);
        Topic nextTopic = topicRepository.findByTopicOrder(nextTopicOrder)
                .orElseThrow(() -> new TopicNotFoundException("topicOrder=" + nextTopicOrder + "인 주제가 없습니다."));
        return TopicResponse.from(nextTopic);
    }

    /** 활성 회차가 없으면(일요일/마감 후 등) 가장 최근 회차의 topic으로 폴백 — 스케줄러와 동일 기준. */
    private Round resolveCurrentRound() {
        try {
            return roundService.getCurrentActiveRound();
        } catch (RoundNotFoundException e) {
            return roundRepository.findTopByOrderByRoundDateDesc()
                    .orElseThrow(() -> new TopicNotFoundException("등록된 회차가 없어 주제를 조회할 수 없습니다."));
        }
    }
}
