ALTER TABLE bank_consultation
    ADD COLUMN result_status VARCHAR(30) NOT NULL DEFAULT 'POSSIBLE',
    ADD COLUMN loan_product VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN';

ALTER TABLE bank_consultation DROP CONSTRAINT ck_bank_consultation_method;

ALTER TABLE bank_consultation
    ADD CONSTRAINT ck_bank_consultation_method CHECK (
        collateral_method IN ('HUG_SAFE_JEONSE','HF','SGI','CLAIM_TRANSFER','OTHER','UNKNOWN')),
    ADD CONSTRAINT ck_bank_consultation_result_status CHECK (
        result_status IN ('POSSIBLE','DIFFICULT','DOCUMENT_REVIEW_REQUIRED','NOT_HEARD')),
    ADD CONSTRAINT ck_bank_consultation_loan_product CHECK (
        loan_product IN ('YOUTH_BEOTIMMOK','GENERAL_BEOTIMMOK','BANK_LOAN','UNKNOWN'));
