ALTER TABLE plan
    ADD COLUMN last_visited_stage VARCHAR(20) NOT NULL DEFAULT 'BENCH';

ALTER TABLE plan
    ADD CONSTRAINT ck_plan_last_visited_stage
        CHECK (last_visited_stage IN ('BENCH','FIRST','SECOND','THIRD','HOME'));
