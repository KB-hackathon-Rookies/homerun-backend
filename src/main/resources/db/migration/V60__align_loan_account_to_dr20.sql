-- 실행 대출 계좌를 DR-20 형태로 정렬(#231). loan_account 는 V1 에 이미 있다 -- product·guarantee·
-- 우대금리 만료·연장 횟수와 계획당 1건 제약·상환방식 CHECK 만 없어 그것만 더한다.
--
-- product 는 기존 행이 없어 NOT NULL 로 넣을 수 있지만, 있을 경우를 대비해 기본값을 주고 뗀다.
-- product/guarantee/repayment_type 값은 기존 enum(ConsultedLoanProduct·CollateralMethod·RepaymentType)
-- 과 맞춘다. V1 의 policy_id·bank_code·rate_cut_* 컬럼은 금리인하요구권 등에서 쓰므로 그대로 둔다.

ALTER TABLE loan_account
    ADD COLUMN product            VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN guarantee          VARCHAR(30),
    ADD COLUMN preferential_until DATE,
    ADD COLUMN extension_count    INT         NOT NULL DEFAULT 0;

ALTER TABLE loan_account ALTER COLUMN product DROP DEFAULT;

ALTER TABLE loan_account
    ADD CONSTRAINT uq_loan_account_plan UNIQUE (plan_id),
    ADD CONSTRAINT ck_loan_account_extension CHECK (extension_count >= 0),
    ADD CONSTRAINT ck_loan_account_product CHECK (
        product IN ('YOUTH_BEOTIMMOK','GENERAL_BEOTIMMOK','BANK_LOAN','UNKNOWN')),
    ADD CONSTRAINT ck_loan_account_guarantee CHECK (
        guarantee IS NULL OR guarantee IN ('HUG_SAFE_JEONSE','HF','SGI','CLAIM_TRANSFER','OTHER','UNKNOWN')),
    ADD CONSTRAINT ck_loan_account_repayment CHECK (
        repayment_type IN ('MATURITY_LUMP_SUM','EQUAL_INSTALLMENT','UNKNOWN'));
