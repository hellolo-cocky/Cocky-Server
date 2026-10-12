-- 내 풀이 기록 조회(WHERE user_id = ? ORDER BY submitted_at DESC)용 복합 인덱스.
-- user_id 단일 FK 인덱스만으로는 해당 유저 제출 전체를 정렬해야 한다.
CREATE INDEX idx_submission_user_submitted_at
    ON submission (user_id, submitted_at);
