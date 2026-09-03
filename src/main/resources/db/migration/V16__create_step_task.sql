-- COM-03 / COM-04-06.
--
-- plan_step 은 PlanGate 5개(온보딩·진단·정책선택·실행·정착)로, 계획의 큰 관문이다.
-- 사용자가 실제로 "하는" 일은 그보다 잘다. 은행 상담, 전입신고, 확정일자 같은 것들이
-- 여기 들어간다. 대시보드의 할 일 목록(prioritizedTasks)은 이 테이블을 읽는다.
--
-- is_irreversible 을 plan_step 이 아니라 여기에도 두는 이유: 되돌릴 수 없는 것은
-- "3루 전체"가 아니라 전입신고·확정일자 같은 개별 행동이다. 우선순위 경고를 정확히
-- 띄우려면 마감(deadline)과 같은 입도에 있어야 한다.

CREATE TABLE step_task (
    id              BIGSERIAL    PRIMARY KEY,
    plan_step_id    BIGINT       NOT NULL REFERENCES plan_step (id) ON DELETE CASCADE,
    task_code       VARCHAR(50)  NOT NULL,
    task_name       VARCHAR(200) NOT NULL,
    sequence        INT          NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'TODO',
    is_irreversible BOOLEAN      NOT NULL DEFAULT false,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0
);

-- RECALC_REQUIRED 는 plan_step 과 같은 뜻이다(COM-03-07). 선행 입력이 바뀌면
-- 영향받는 할 일을 다시 하도록 되돌린다.
ALTER TABLE step_task
    ADD CONSTRAINT ck_step_task_status
        CHECK (status IN ('TODO', 'DOING', 'DONE', 'SKIPPED', 'RECALC_REQUIRED'));

ALTER TABLE step_task
    ADD CONSTRAINT ck_step_task_sequence CHECK (sequence > 0);

CREATE UNIQUE INDEX uq_step_task_step_sequence ON step_task (plan_step_id, sequence);
CREATE UNIQUE INDEX uq_step_task_step_code ON step_task (plan_step_id, task_code);
CREATE INDEX ix_step_task_step ON step_task (plan_step_id, status);

-- 마감은 관문이 아니라 할 일에 붙는다. "잔금일+3개월 대출신청 마감"은 대출신청
-- 이라는 행동의 것이지 "3루" 전체의 것이 아니다. step_id 는 단계 전체에 걸리는
-- 마감을 위해 남겨 두지만 대시보드는 task_id 로만 조회한다.
ALTER TABLE deadline
    ADD COLUMN task_id BIGINT REFERENCES step_task (id) ON DELETE CASCADE;

CREATE INDEX ix_deadline_task ON deadline (plan_id, task_id);

COMMENT ON TABLE step_task IS '계획 관문(plan_step) 안에서 사용자가 실제로 수행하는 할 일';
COMMENT ON COLUMN step_task.is_irreversible IS '되돌릴 수 없는 행동인지. 전입신고·확정일자 등';
COMMENT ON COLUMN deadline.task_id IS '이 마감이 걸린 할 일. 대시보드 우선순위의 기준';
