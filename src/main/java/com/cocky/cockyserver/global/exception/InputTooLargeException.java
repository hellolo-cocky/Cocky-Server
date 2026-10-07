package com.cocky.cockyserver.global.exception;

/** 요청의 code/stdin이 크기 제한(64KB)을 넘었다(400, INVALID_REQUEST). /run과 /submissions가 공용으로 쓴다. */
public class InputTooLargeException extends RuntimeException {

    public InputTooLargeException(String message) {
        super(message);
    }
}
