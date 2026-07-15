package com.cocky.cockyserver.domain.user.exception;

/** 존재하지 않는 userId로 조회한 경우(404)를 나타낸다. */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String message) {
        super(message);
    }
}