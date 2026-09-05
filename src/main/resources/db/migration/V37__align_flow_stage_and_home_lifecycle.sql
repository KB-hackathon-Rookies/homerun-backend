-- 최종 플로우는 1루=매물 없음, 2루=매물 있음이다. 기존 계획의 매물 작업도 2루로 이동한다.
UPDATE plan_step SET step_name = '대출 진단 완료' WHERE step_code = 'FIRST_DIAGNOSIS';
UPDATE plan_step SET step_name = '매물 검증·대출 상품 확정 완료' WHERE step_code = 'SECOND_POLICY_SELECTION';

-- 같은 관문 안의 sequence 유일 제약을 피하려고 2루 작업을 임시 구간으로 옮긴다.
UPDATE step_task t
   SET sequence = t.sequence + 100
  FROM plan_step ps
 WHERE t.plan_step_id = ps.id AND ps.step_code = 'SECOND_POLICY_SELECTION';

UPDATE step_task t
   SET plan_step_id = target.id
  FROM plan_step source
  JOIN plan_step target ON target.plan_id = source.plan_id
                       AND target.step_code = 'SECOND_POLICY_SELECTION'
 WHERE t.plan_step_id = source.id
   AND source.step_code = 'FIRST_DIAGNOSIS'
   AND t.task_code IN ('PROPERTY_SEARCH', 'BUILDING_REGISTER_CHECK', 'ACTUAL_PRICE_CHECK', 'REGISTER_CHECK');

UPDATE step_task SET sequence = CASE task_code
    WHEN 'PROPERTY_SEARCH' THEN 1
    WHEN 'BUILDING_REGISTER_CHECK' THEN 2
    WHEN 'ACTUAL_PRICE_CHECK' THEN 3
    WHEN 'REGISTER_CHECK' THEN 4
    WHEN 'BANK_CONSULTATION' THEN 5
    WHEN 'SELECT_LOAN_PRODUCT' THEN 6
    WHEN 'SELECT_GUARANTEE' THEN 7
    WHEN 'MONTHLY_SUPPORT_CHECK' THEN 8
    WHEN 'DOCUMENT_CHECK' THEN 9
    WHEN 'RECORD_BANK_CONSULTATION' THEN 10
    ELSE sequence
END
WHERE task_code IN (
    'PROPERTY_SEARCH', 'BUILDING_REGISTER_CHECK', 'ACTUAL_PRICE_CHECK', 'REGISTER_CHECK',
    'BANK_CONSULTATION', 'SELECT_LOAN_PRODUCT', 'SELECT_GUARANTEE',
    'MONTHLY_SUPPORT_CHECK', 'DOCUMENT_CHECK', 'RECORD_BANK_CONSULTATION'
);

ALTER TABLE step_task
    ADD COLUMN due_at DATE,
    ADD COLUMN recurrence_type VARCHAR(20) NOT NULL DEFAULT 'ONCE';

ALTER TABLE step_task DROP CONSTRAINT ck_step_task_status;
ALTER TABLE step_task ADD CONSTRAINT ck_step_task_status
    CHECK (status IN ('TODO','DOING','DONE','SKIPPED','RECALC_REQUIRED','EXPIRED'));
ALTER TABLE step_task ADD CONSTRAINT ck_step_task_recurrence
    CHECK (recurrence_type IN ('ONCE','MONTHLY','ANNUAL','MATURITY'));

UPDATE step_task SET recurrence_type = 'MONTHLY' WHERE task_code = 'FIRST_MONTH_CHECKIN';

-- 홈은 종료가 없는 지속 관리 단계다. 과거 홈 완료로 닫힌 계획을 다시 활성화한다.
UPDATE plan SET status = 'ACTIVE' WHERE stage = 'HOME' AND status = 'DONE';
