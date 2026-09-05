-- DIA-02 1루 진단 계산 결과를 재현할 수 있도록 계산 당시의 비용과 월 현금흐름 입력을 보관한다.
-- RIR은 사용자 결정으로 신규 계산에서 제외하므로 기존 nullable 컬럼만 호환용으로 남긴다.

ALTER TABLE cost_estimate
    ADD COLUMN emergency_reserve BIGINT NOT NULL DEFAULT 0;

ALTER TABLE cost_estimate
    ADD CONSTRAINT ck_cost_estimate_emergency_reserve CHECK (emergency_reserve >= 0);

ALTER TABLE diagnosis
    ADD COLUMN cost_estimate_id BIGINT REFERENCES cost_estimate (id),
    ADD COLUMN monthly_income BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN monthly_living_expense BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN monthly_debt_payment BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN expected_loan_amount BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN expected_monthly_interest BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN required_cash_after_policy BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN possible_date_before_policy DATE,
    ADD COLUMN possible_date DATE,
    ADD COLUMN warnings JSONB NOT NULL DEFAULT '[]';

ALTER TABLE diagnosis
    ADD CONSTRAINT ck_diagnosis_monthly_income CHECK (monthly_income >= 0),
    ADD CONSTRAINT ck_diagnosis_monthly_living_expense CHECK (monthly_living_expense >= 0),
    ADD CONSTRAINT ck_diagnosis_monthly_debt_payment CHECK (monthly_debt_payment >= 0),
    ADD CONSTRAINT ck_diagnosis_expected_loan_amount CHECK (expected_loan_amount >= 0),
    ADD CONSTRAINT ck_diagnosis_expected_monthly_interest CHECK (expected_monthly_interest >= 0),
    ADD CONSTRAINT ck_diagnosis_required_cash_after_policy CHECK (required_cash_after_policy >= 0);

CREATE INDEX ix_cost_estimate_plan ON cost_estimate (plan_id, created_at DESC);
