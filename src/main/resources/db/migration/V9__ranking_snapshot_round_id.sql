-- TWO_DAY 스냅샷은 직전 마감 라운드(9문제) 단위로 생성된다. WEEKLY/MONTHLY는 특정
-- 라운드에 묶이지 않으므로 nullable로 둔다.
ALTER TABLE ranking_snapshot
    ADD COLUMN round_id BIGINT NULL AFTER user_id,
    ADD CONSTRAINT fk_ranking_snapshot_round FOREIGN KEY (round_id) REFERENCES round (id);

-- 배치가 "이 라운드 스냅샷 이미 생성됐는지" 중복 체크에 사용.
CREATE INDEX idx_ranking_snapshot_period_scope_round
    ON ranking_snapshot (period_type, scope_type, round_id);
