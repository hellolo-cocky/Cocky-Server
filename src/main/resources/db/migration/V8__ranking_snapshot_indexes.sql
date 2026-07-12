-- ranking_snapshot 테이블은 V1에서 이미 생성됨(period_type/scope_type 포함).
-- 배치 스케줄러가 "최신 스냅샷 조회"(period_type, scope_type, calculated_at MAX)와
-- "유저별 이력 조회"를 반복 수행하므로 조회 인덱스만 추가한다.
CREATE INDEX idx_ranking_snapshot_period_scope_calculated
    ON ranking_snapshot (period_type, scope_type, calculated_at);

CREATE INDEX idx_ranking_snapshot_user_calculated
    ON ranking_snapshot (user_id, calculated_at);
