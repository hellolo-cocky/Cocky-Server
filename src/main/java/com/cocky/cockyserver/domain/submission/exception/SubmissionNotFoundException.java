package com.cocky.cockyserver.domain.submission.exception;

/** 존재하지 않는 submissionId로 조회한 경우(404)를 나타낸다. */
public class SubmissionNotFoundException extends RuntimeException {

    public SubmissionNotFoundException(String message) {
        super(message);
    }
}