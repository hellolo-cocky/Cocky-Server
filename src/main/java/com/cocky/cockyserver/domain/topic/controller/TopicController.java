package com.cocky.cockyserver.domain.topic.controller;

import com.cocky.cockyserver.domain.topic.dto.TopicResponse;
import com.cocky.cockyserver.domain.topic.service.TopicService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/topics")
@RequiredArgsConstructor
public class TopicController {

    private final TopicService topicService;

    @GetMapping("/current")
    public ResponseEntity<TopicResponse> getCurrentTopic() {
        return ResponseEntity.ok(topicService.getCurrentTopic());
    }

    @GetMapping("/next")
    public ResponseEntity<TopicResponse> getNextTopic() {
        return ResponseEntity.ok(topicService.getNextTopic());
    }
}
