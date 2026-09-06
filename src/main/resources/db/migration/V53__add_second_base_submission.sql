ALTER TABLE property_decision
    ADD COLUMN decision_revision INT NOT NULL DEFAULT 1,
    ADD CONSTRAINT ck_property_decision_revision CHECK (decision_revision > 0);

CREATE TABLE second_base_submission (
    id                BIGSERIAL PRIMARY KEY,
    plan_id           BIGINT NOT NULL REFERENCES plan (id) ON DELETE CASCADE,
    decision_revision INT NOT NULL,
    result_snapshot   JSONB NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_second_base_submission_revision UNIQUE (plan_id, decision_revision),
    CONSTRAINT ck_second_base_submission_revision CHECK (decision_revision > 0)
);

CREATE INDEX ix_second_base_submission_plan_created
    ON second_base_submission (plan_id, created_at DESC, id DESC);
