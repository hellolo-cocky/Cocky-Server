package com.cocky.cockyserver.domain.topic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.cocky.cockyserver.domain.round.entity.Round;
import com.cocky.cockyserver.domain.round.exception.RoundNotFoundException;
import com.cocky.cockyserver.domain.round.repository.RoundRepository;
import com.cocky.cockyserver.domain.round.service.RoundService;
import com.cocky.cockyserver.domain.topic.dto.TopicResponse;
import com.cocky.cockyserver.domain.topic.entity.Topic;
import com.cocky.cockyserver.domain.topic.exception.TopicNotFoundException;
import com.cocky.cockyserver.domain.topic.repository.TopicRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TopicServiceTest {

    @Mock
    private RoundService roundService;

    @Mock
    private RoundRepository roundRepository;

    @Mock
    private TopicRepository topicRepository;

    private TopicService topicService;

    private Round round(Topic topic) {
        return new Round(topic, LocalDate.of(2026, 7, 8),
                LocalDateTime.of(2026, 7, 8, 0, 0), LocalDateTime.of(2026, 7, 9, 0, 0));
    }

    @Test
    void currentTopic_usesActiveRoundTopic() {
        topicService = new TopicService(roundService, roundRepository, topicRepository);
        Topic topic = new Topic("배열", 3);
        when(roundService.getCurrentActiveRound()).thenReturn(round(topic));

        TopicResponse response = topicService.getCurrentTopic();

        assertEquals(3, response.topicOrder());
        assertEquals("배열", response.topic());
    }

    @Test
    void currentTopic_fallsBackToMostRecentRoundWhenNoActiveRound() {
        topicService = new TopicService(roundService, roundRepository, topicRepository);
        Topic topic = new Topic("그래프", 5);
        when(roundService.getCurrentActiveRound()).thenThrow(new RoundNotFoundException("없음"));
        when(roundRepository.findTopByOrderByRoundDateDesc()).thenReturn(Optional.of(round(topic)));

        TopicResponse response = topicService.getCurrentTopic();

        assertEquals(5, response.topicOrder());
        assertEquals("그래프", response.topic());
    }

    @Test
    void currentTopic_throwsWhenNoRoundExistsAtAll() {
        topicService = new TopicService(roundService, roundRepository, topicRepository);
        when(roundService.getCurrentActiveRound()).thenThrow(new RoundNotFoundException("없음"));
        when(roundRepository.findTopByOrderByRoundDateDesc()).thenReturn(Optional.empty());

        assertThrows(TopicNotFoundException.class, () -> topicService.getCurrentTopic());
    }

    @Test
    void nextTopic_incrementsTopicOrder() {
        topicService = new TopicService(roundService, roundRepository, topicRepository);
        Topic current = new Topic("배열", 3);
        Topic next = new Topic("그래프", 4);
        when(roundService.getCurrentActiveRound()).thenReturn(round(current));
        when(topicRepository.findByTopicOrder(4)).thenReturn(Optional.of(next));

        TopicResponse response = topicService.getNextTopic();

        assertEquals(4, response.topicOrder());
        assertEquals("그래프", response.topic());
    }

    @Test
    void nextTopic_cyclesFromTopicOrder8To1() {
        topicService = new TopicService(roundService, roundRepository, topicRepository);
        Topic current = new Topic("마무리", 8);
        Topic first = new Topic("구현", 1);
        when(roundService.getCurrentActiveRound()).thenReturn(round(current));
        when(topicRepository.findByTopicOrder(1)).thenReturn(Optional.of(first));

        TopicResponse response = topicService.getNextTopic();

        assertEquals(1, response.topicOrder());
        assertEquals("구현", response.topic());
    }

    @Test
    void nextTopic_throwsWhenNextTopicMissing() {
        topicService = new TopicService(roundService, roundRepository, topicRepository);
        Topic current = new Topic("마무리", 8);
        when(roundService.getCurrentActiveRound()).thenReturn(round(current));
        when(topicRepository.findByTopicOrder(1)).thenReturn(Optional.empty());

        assertThrows(TopicNotFoundException.class, () -> topicService.getNextTopic());
    }
}
