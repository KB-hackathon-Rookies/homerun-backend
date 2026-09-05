CREATE TABLE plan_input_step (
    id           BIGSERIAL PRIMARY KEY,
    plan_id      BIGINT NOT NULL REFERENCES plan (id) ON DELETE CASCADE,
    step_code    VARCHAR(40) NOT NULL,
    status       VARCHAR(20) NOT NULL,
    completed_at TIMESTAMPTZ,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    version      BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_plan_input_step UNIQUE (plan_id, step_code),
    CONSTRAINT ck_plan_input_step_code CHECK (step_code IN (
        'HOUSEHOLDER','HOMELESS','MARITAL_STATUS','EMPLOYMENT_TYPE',
        'COMPANY_SIZE','EMPLOYMENT_PERIOD','FINANCIAL','HOPE_DEPOSIT','REGION','REVIEW')),
    CONSTRAINT ck_plan_input_step_status CHECK (status IN ('COMPLETED','SKIPPED'))
);

CREATE INDEX ix_plan_input_step_plan ON plan_input_step (plan_id, updated_at DESC);
