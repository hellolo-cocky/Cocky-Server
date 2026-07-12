package com.cocky.cockyserver.domain.ranking.exception;

/** 배치가 아직 생성하지 않는 period/scope 조합 요청(400)을 나타낸다. "지원 안 함"과 "데이터 없음"을 구분하기 위함. */
public class UnsupportedRankingCombinationException extends RuntimeException {

    public UnsupportedRankingCombinationException(String message) {
        super(message);
    }
}
