-- 익명 모드 2단 구조: 사용자 기본값(user.is_anonymous_default) + 제출별 덮어쓰기(submission.is_anonymous).
-- anonymous_nickname은 AI가 생성해 한 번 채워지면 고정(재생성 안 함) —
-- is_anonymous_default만 켜고 끄는 방식이라 토글 자체는 자유롭다.
-- 기존 행은 anonymous_nickname NULL / is_anonymous_default FALSE(컬럼 기본값)로 채워진다.
ALTER TABLE `user`
    ADD COLUMN anonymous_nickname VARCHAR(50) NULL AFTER refresh_token,
    ADD COLUMN is_anonymous_default BOOLEAN NOT NULL DEFAULT FALSE AFTER anonymous_nickname,
    ADD CONSTRAINT uk_user_anonymous_nickname UNIQUE (anonymous_nickname);

-- 제출 단위 익명 덮어쓰기. 요청에 isAnonymous가 없으면 서비스 계층이 제출 시점
-- user.is_anonymous_default 값을 그대로 채워 저장한다 — 컬럼 자체는 항상 NOT NULL.
ALTER TABLE submission
    ADD COLUMN is_anonymous BOOLEAN NOT NULL DEFAULT FALSE AFTER is_latest;
