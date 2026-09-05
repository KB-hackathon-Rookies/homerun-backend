-- 1루 최종 제출을 plan_input revision 단위로 멱등하게 처리한다.
CREATE TABLE first_base_submission (
    id             BIGSERIAL   PRIMARY KEY,
    plan_id        BIGINT      NOT NULL REFERENCES plan (id) ON DELETE CASCADE,
    input_revision INT         NOT NULL,
    diagnosis_id   BIGINT      NOT NULL REFERENCES diagnosis (id) ON DELETE CASCADE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_first_base_submission_revision UNIQUE (plan_id, input_revision),
    CONSTRAINT ck_first_base_submission_revision CHECK (input_revision > 0)
);

CREATE INDEX ix_first_base_submission_plan
    ON first_base_submission (plan_id, created_at DESC);
