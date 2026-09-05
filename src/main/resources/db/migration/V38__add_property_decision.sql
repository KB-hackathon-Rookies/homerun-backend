ALTER TABLE property
    ADD COLUMN is_leasehold_registered BOOLEAN,
    ADD COLUMN has_seizure_or_disposition_restriction BOOLEAN,
    ADD COLUMN is_auction_in_progress BOOLEAN,
    ADD COLUMN senior_debt_registered_at DATE;

CREATE TABLE bank_consultation (
    id                  BIGSERIAL PRIMARY KEY,
    plan_id             BIGINT NOT NULL REFERENCES plan (id) ON DELETE CASCADE,
    property_id         BIGINT NOT NULL REFERENCES property (id) ON DELETE CASCADE,
    bank_name           VARCHAR(100) NOT NULL,
    branch_name         VARCHAR(100),
    policy_id           BIGINT REFERENCES policy (id),
    guarantee_agency_id BIGINT REFERENCES guarantee_agency (id),
    collateral_method   VARCHAR(30) NOT NULL,
    approved_limit      BIGINT,
    quoted_rate         NUMERIC(6,3),
    consulted_at        DATE NOT NULL,
    memo                TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_bank_consultation_limit CHECK (approved_limit IS NULL OR approved_limit >= 0),
    CONSTRAINT ck_bank_consultation_rate CHECK (quoted_rate IS NULL OR quoted_rate >= 0),
    CONSTRAINT ck_bank_consultation_method CHECK (
        collateral_method IN ('HUG_SAFE_JEONSE','HF','SGI','CLAIM_TRANSFER','OTHER'))
);

CREATE INDEX ix_bank_consultation_property
    ON bank_consultation (plan_id, property_id, consulted_at DESC, id DESC);

CREATE TABLE property_decision (
    id              BIGSERIAL PRIMARY KEY,
    plan_id         BIGINT NOT NULL UNIQUE REFERENCES plan (id) ON DELETE CASCADE,
    property_id     BIGINT NOT NULL REFERENCES property (id),
    consultation_id BIGINT NOT NULL REFERENCES bank_consultation (id),
    decided_at      TIMESTAMPTZ NOT NULL
);
