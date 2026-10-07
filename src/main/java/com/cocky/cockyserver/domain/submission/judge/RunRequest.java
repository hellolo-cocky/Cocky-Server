package com.cocky.cockyserver.domain.submission.judge;

import com.cocky.cockyserver.domain.problem.entity.Language;

/** 코드 단순 실행 요청 — 채점 없이 stdin을 넣어 실행 결과만 받는다. */
public record RunRequest(Language language, String code, String stdin) {
}
