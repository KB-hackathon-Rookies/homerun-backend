-- 1루 완료 당시 정책 판정과 진단 시나리오를 그대로 복원한다.
ALTER TABLE first_base_submission
    ADD COLUMN result_snapshot JSONB;
