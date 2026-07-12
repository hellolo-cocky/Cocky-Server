-- 스케줄러 중복 실행/admin 수동 트리거 동시 호출 시 같은 라운드에 중복 스냅샷이
-- 삽입되는 것을 막는 최종 방어선. 서비스 계층의 existsBy 체크는 그대로 두고, 이 제약은
-- race condition에 대비한 보험이다.
ALTER TABLE ranking_snapshot
    ADD CONSTRAINT uk_ranking_snapshot_period_scope_round_user
    UNIQUE (period_type, scope_type, round_id, user_id);
