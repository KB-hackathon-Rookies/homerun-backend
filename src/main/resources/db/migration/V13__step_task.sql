CREATE TABLE step_task (
                           id              BIGSERIAL    PRIMARY KEY,
                           plan_step_id    BIGINT       NOT NULL REFERENCES plan_step (id) ON DELETE CASCADE,

                           task_code       VARCHAR(50)  NOT NULL,
                           task_name       VARCHAR(200) NOT NULL,
                           sequence        INT          NOT NULL,

                           status          VARCHAR(20)  NOT NULL DEFAULT 'TODO',

                           completed_at    TIMESTAMPTZ,

                           created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                           updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

                           CONSTRAINT chk_step_task_sequence
                               CHECK (sequence > 0),

                           CONSTRAINT chk_step_task_status
                               CHECK (status IN (
                                                 'TODO',
                                                 'DOING',
                                                 'DONE',
                                                 'SKIPPED'
                                   ))
);

-- 하나의 스텝 안에서는 동일한 순서를 가질 수 없음
CREATE UNIQUE INDEX uq_step_task_sequence
    ON step_task (plan_step_id, sequence);

-- 조회 성능을 위한 인덱스
CREATE INDEX idx_step_task_plan_step_id
    ON step_task (plan_step_id);