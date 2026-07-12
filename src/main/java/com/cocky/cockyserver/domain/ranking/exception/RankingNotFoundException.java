package com.cocky.cockyserver.domain.ranking.exception;

/** 아직 생성된 랭킹 스냅샷이 없는 경우(404)를 나타낸다. */
public class RankingNotFoundException extends RuntimeException {

    public RankingNotFoundException(String message) {
        super(message);
    }
}
