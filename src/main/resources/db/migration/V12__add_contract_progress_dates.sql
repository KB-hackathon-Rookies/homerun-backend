-- PRP-02-01, PRP-02-04 계약 진행상태 관리.
--
-- lease_contract 의 balance_date · move_in_date 는 "예정일"이다. 예정일이 채워졌다고
-- 잔금을 냈다고 볼 수는 없다. 실제로 끝난 날을 따로 둔다.
--
-- 대출 신청일도 마찬가지다. application 테이블은 정책 신청이라 계약 진행과 시점이
-- 다르고, 계약 화면이 신청 도메인을 읽어야 할 이유도 없다.

ALTER TABLE lease_contract
    ADD COLUMN bank_consulted_at DATE,
    ADD COLUMN loan_applied_at DATE,
    ADD COLUMN balance_paid_at DATE;

CREATE UNIQUE INDEX uq_lease_contract_plan ON lease_contract (plan_id);

COMMENT ON COLUMN lease_contract.balance_date IS '잔금 예정일';
COMMENT ON COLUMN lease_contract.bank_consulted_at IS '은행 사전상담을 받은 날';
COMMENT ON COLUMN lease_contract.loan_applied_at IS '대출을 실제로 신청한 날';
COMMENT ON COLUMN lease_contract.balance_paid_at IS '잔금을 실제로 지급한 날';
