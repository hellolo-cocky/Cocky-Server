-- 랭킹 등에서 본명 대신 노출할 수 있는 익명 닉네임 토글. anonymous_nickname은 AI가 생성해
-- 한 번 채워지면 고정(재생성 안 함) — is_anonymous만 켜고 끄는 방식이라 토글 자체는 자유롭다.
-- 기존 행은 anonymous_nickname NULL / is_anonymous FALSE(컬럼 기본값)로 채워진다.
ALTER TABLE `user`
    ADD COLUMN anonymous_nickname VARCHAR(50) NULL AFTER refresh_token,
    ADD COLUMN is_anonymous BOOLEAN NOT NULL DEFAULT FALSE AFTER anonymous_nickname,
    ADD CONSTRAINT uk_user_anonymous_nickname UNIQUE (anonymous_nickname);
