-- 고정지출을 DR-20 형태로 정렬(#235). FR-H5-01·02. fixed_expense 는 V1 에 이미 있어 ALTER 한다.
--
-- V1 의 category 가 DR-20 의 type 역할이다(INTEREST/MGMT/OTHER). 자동이체 여부(autopay)만 없어
-- 더하고, category 에 CHECK 를 건다. due_day 는 SMALLINT 였는데 엔티티·API 에서 int 로 다루려
-- INTEGER 로 넓힌다(값 범위 1~31, 기존 값 손실 없음).

ALTER TABLE fixed_expense
    ADD COLUMN autopay BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE fixed_expense ALTER COLUMN due_day TYPE INTEGER;

ALTER TABLE fixed_expense
    ADD CONSTRAINT ck_fixed_expense_category CHECK (category IN ('INTEREST','MGMT','OTHER')),
    ADD CONSTRAINT ck_fixed_expense_amount CHECK (amount >= 0),
    ADD CONSTRAINT ck_fixed_expense_due_day CHECK (due_day IS NULL OR due_day BETWEEN 1 AND 31);
