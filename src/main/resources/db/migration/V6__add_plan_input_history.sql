ALTER TABLE plan_input
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN revision INT NOT NULL DEFAULT 1,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE plan_input
    ADD CONSTRAINT uq_plan_input_plan UNIQUE (plan_id);

CREATE TABLE plan_input_history (
    id            BIGSERIAL   PRIMARY KEY,
    plan_input_id BIGINT      NOT NULL REFERENCES plan_input (id) ON DELETE CASCADE,
    plan_id       BIGINT      NOT NULL REFERENCES plan (id),
    revision      INT         NOT NULL,
    snapshot      JSONB       NOT NULL,
    saved_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_plan_input_history_revision UNIQUE (plan_input_id, revision)
);

CREATE INDEX ix_plan_input_history_plan
    ON plan_input_history (plan_id, revision DESC);
