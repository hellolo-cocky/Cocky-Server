-- topic.week_order → topic_order 리네임. "주차"가 아니라 회차 순번(1~8 순환)이라는 실제 의미에 맞춘다.
-- 이 컬럼에 걸린 인덱스/제약은 없다(V1: TINYINT NOT NULL).
ALTER TABLE topic RENAME COLUMN week_order TO topic_order;
