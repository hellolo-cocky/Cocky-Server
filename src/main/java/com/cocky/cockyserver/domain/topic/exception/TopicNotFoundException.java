package com.cocky.cockyserver.domain.topic.exception;

/** 조회할 회차/주제가 하나도 없는 경우(404)를 나타낸다. */
public class TopicNotFoundException extends RuntimeException {

    public TopicNotFoundException(String message) {
        super(message);
    }
}
