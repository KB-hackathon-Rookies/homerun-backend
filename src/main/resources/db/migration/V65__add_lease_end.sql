-- 갱신·퇴거 결정(#내부). DR-22 lease_end. 계약 종료 시 갱신/퇴거와 갱신 방법·통보일을 계획당
-- 1건 저장한다. 미반환 대응(deposit_returned 등)은 이후 마이그레이션에서 더한다.
--
-- renewal_method 값은 기존 enum(RenewalMethod: CLAIM/IMPLIED/AGREED)과 맞춘다.
-- decision 값은 LeaseDecision enum 과 짝이다.

CREATE TABLE lease_end (
    id               BIGSERIAL   PRIMARY KEY,
    plan_id          BIGINT      NOT NULL UNIQUE REFERENCES plan (id) ON DELETE CASCADE,
    decision         VARCHAR(20) NOT NULL,
    renewal_method   VARCHAR(20),
    claim_right_used BOOLEAN     NOT NULL DEFAULT false,
    notice_sent_at   DATE,
    decided_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_lease_end_decision CHECK (decision IN ('RENEW','LEAVE','UNDECIDED')),
    CONSTRAINT ck_lease_end_renewal_method CHECK (
        renewal_method IS NULL OR renewal_method IN ('CLAIM','IMPLIED','AGREED'))
);
