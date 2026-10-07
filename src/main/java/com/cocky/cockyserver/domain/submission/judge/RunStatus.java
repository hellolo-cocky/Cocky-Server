package com.cocky.cockyserver.domain.submission.judge;

/**
 * 코드 단순 실행(/run) 결과 상태. DB에 저장하지 않으므로 {@link com.cocky.cockyserver.domain.submission.entity.Verdict}와
 * 별개로 둔다 — 메모리 초과(MLE)는 엔진 어댑터가 RE로 접어서 내려준다.
 */
public enum RunStatus {
    OK,
    TLE,
    RE,
    CE
}
