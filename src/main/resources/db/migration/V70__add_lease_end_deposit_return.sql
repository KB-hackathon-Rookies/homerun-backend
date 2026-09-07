-- 보증금 반환 결과(#내부). DR-22·FR-HX-01. lease_end 에 미반환 대응 상태를 더한다.
--
-- deposit_returned·unreturned_action 값은 DepositReturnStatus·UnreturnedAction enum 과 짝이다.

ALTER TABLE lease_end
    ADD COLUMN deposit_returned     VARCHAR(10),
    ADD COLUMN return_amount_to_bank BIGINT,
    ADD COLUMN return_amount_to_me   BIGINT,
    ADD COLUMN unreturned_action    VARCHAR(30);

ALTER TABLE lease_end
    ADD CONSTRAINT ck_lease_end_deposit_returned CHECK (
        deposit_returned IS NULL OR deposit_returned IN ('YES','NO','PARTIAL')),
    ADD CONSTRAINT ck_lease_end_unreturned_action CHECK (
        unreturned_action IS NULL OR unreturned_action IN (
            'LEASEHOLD_REGISTRATION','GUARANTEE_CLAIM','LAWSUIT'));
