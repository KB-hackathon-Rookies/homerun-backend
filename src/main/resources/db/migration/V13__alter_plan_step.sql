-- sequence → step_group 변경
ALTER TABLE plan_step
    RENAME COLUMN sequence TO step_group;

-- 불필요한 의존성 제거
ALTER TABLE plan_step
    DROP COLUMN depends_on;

-- 생성 시간 추가
ALTER TABLE plan_step
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- updated_at은 이미 존재하므로 추가하지 않음

-- step_group은 1~4만 허용
ALTER TABLE plan_step
    ADD CONSTRAINT chk_plan_step_group
        CHECK (step_group BETWEEN 1 AND 4);

-- status 허용값 제한
ALTER TABLE plan_step
    ADD CONSTRAINT chk_plan_step_status
        CHECK (status IN (
                          'LOCKED',
                          'READY',
                          'DOING',
                          'DONE',
                          'SKIPPED'
            ));

-- 하나의 plan에는 동일한 step_group이 하나만 존재
CREATE UNIQUE INDEX uq_plan_step_plan_group
    ON plan_step(plan_id, step_group);

-- 사용자가 마지막으로 진행한 위치
ALTER TABLE plan
    ADD COLUMN last_step_code VARCHAR(50),
    ADD COLUMN last_task_code VARCHAR(50);