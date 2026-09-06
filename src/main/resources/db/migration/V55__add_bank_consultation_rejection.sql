-- 은행 상담 거절 사유 분류(#213). BR-24·DR-11·FR-P8-01·02.
--
-- 거절은 매물 단위(bank_consultation)로 저장한다. 어디서(rejection_stage) 무엇 때문에
-- (rejection_category) 막혔는지를 남겨야 대안을 자동 제시하고 같은 사유 반복을 감지할 수 있다.
-- 거절이 아닌 상담(POSSIBLE 등)에는 채우지 않으므로 전부 nullable 이다.
--
-- application 도메인의 reject_stage 와 다른 축이다 -- 저쪽은 신청 단위, 이쪽은 상담 단위다.

ALTER TABLE bank_consultation
    ADD COLUMN rejection_stage    VARCHAR(20),
    ADD COLUMN rejection_category VARCHAR(20),
    ADD COLUMN rejection_note     TEXT;

ALTER TABLE bank_consultation
    ADD CONSTRAINT ck_bank_consultation_rejection_stage CHECK (
        rejection_stage IS NULL OR rejection_stage IN ('BANK','GUARANTEE','NOT_TOLD')),
    ADD CONSTRAINT ck_bank_consultation_rejection_category CHECK (
        rejection_category IS NULL OR rejection_category IN (
            'SUBJECT_ISSUE','PROPERTY_ISSUE','GUARANTEE_ISSUE','DOCUMENT_ISSUE','LANDLORD_ISSUE'));
