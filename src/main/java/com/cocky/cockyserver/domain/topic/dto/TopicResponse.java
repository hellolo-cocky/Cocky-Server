package com.cocky.cockyserver.domain.topic.dto;

import com.cocky.cockyserver.domain.topic.entity.Topic;

public record TopicResponse(Integer topicOrder, String topic) {

    public static TopicResponse from(Topic topic) {
        return new TopicResponse(topic.getTopicOrder(), topic.getName());
    }
}
