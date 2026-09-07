-- 반환보증 가입 상태(#내부). FR-H1-03. 홈 4-1. 계획당 1건.
--
-- 가입 완료·보증료 납부 완료를 저장한다. 보증료 지원(4-2)은 가입·납부가 끝나야 신청 가능하다
-- (BR-31 요건). 기관·서류 등 부가 정보는 필요해지면 이후 마이그레이션에서 더한다.

CREATE TABLE return_guarantee (
    id           BIGSERIAL   PRIMARY KEY,
    plan_id      BIGINT      NOT NULL UNIQUE REFERENCES plan (id) ON DELETE CASCADE,
    enrolled     BOOLEAN     NOT NULL DEFAULT false,
    fee_paid     BOOLEAN     NOT NULL DEFAULT false,
    enrolled_at  DATE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
