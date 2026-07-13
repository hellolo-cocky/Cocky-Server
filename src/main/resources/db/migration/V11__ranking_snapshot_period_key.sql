-- WEEKLY/MONTHLY는 round_id가 NULL이라 V10의 유니크 제약(period_type, scope_type,
-- round_id, user_id)으로는 중복 생성을 막을 수 없다(MySQL UNIQUE는 NULL끼리 서로
-- 다른 값으로 취급). round_id 대신 절대 NULL이 되지 않는 period_key로 유니크 제약을
-- 다시 잡는다.
ALTER TABLE ranking_snapshot
    ADD COLUMN period_start DATE NULL AFTER round_id,
    ADD COLUMN period_end DATE NULL AFTER period_start,
    ADD COLUMN period_key VARCHAR(20) NULL AFTER period_end;

-- 기존 TWO_DAY row 백필: period_key = round_id 문자열.
UPDATE ranking_snapshot
    SET period_key = CAST(round_id AS CHAR)
    WHERE period_key IS NULL;

ALTER TABLE ranking_snapshot
    MODIFY COLUMN period_key VARCHAR(20) NOT NULL;

ALTER TABLE ranking_snapshot
    DROP INDEX uk_ranking_snapshot_period_scope_round_user;

ALTER TABLE ranking_snapshot
    ADD CONSTRAINT uk_ranking_snapshot_period_scope_key_user
    UNIQUE (period_type, scope_type, period_key, user_id);
