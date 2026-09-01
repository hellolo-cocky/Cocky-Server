package com.cocky.cockyserver.domain.topic.repository;

import com.cocky.cockyserver.domain.topic.entity.Topic;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    Optional<Topic> findByTopicOrder(int topicOrder);
}
