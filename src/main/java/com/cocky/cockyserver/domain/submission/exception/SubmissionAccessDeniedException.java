package com.cocky.cockyserver.domain.submission.exception;

/** 본인 제출이 아닌 제출을 조회하려는 경우(403)를 나타낸다. */
public class SubmissionAccessDeniedException extends RuntimeException {

    public SubmissionAccessDeniedException(String message) {
        super(message);
    }
}