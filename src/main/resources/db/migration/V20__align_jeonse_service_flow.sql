-- 새 전세대출 여정의 진단 입력과 1·2·3루 할 일을 기존 계획 모델에 맞춘다.

ALTER TABLE plan_input
    ADD COLUMN household_homeless BOOLEAN,
    ADD COLUMN birth_date DATE,
    ADD COLUMN military_months INT,
    ADD COLUMN monthly_income BIGINT,
    ADD COLUMN net_assets BIGINT,
    ADD COLUMN available_cash BIGINT,
    ADD COLUMN has_existing_jeonse_loan BOOLEAN,
    ADD COLUMN income_source VARCHAR(20),
    ADD COLUMN asset_source VARCHAR(20),
    ADD COLUMN financial_data_confirmed BOOLEAN;

ALTER TABLE plan_input
    ADD CONSTRAINT ck_plan_input_military_months
        CHECK (military_months IS NULL OR military_months BETWEEN 0 AND 60),
    ADD CONSTRAINT ck_plan_input_monthly_income
        CHECK (monthly_income IS NULL OR monthly_income >= 0),
    ADD CONSTRAINT ck_plan_input_net_assets
        CHECK (net_assets IS NULL OR net_assets >= 0),
    ADD CONSTRAINT ck_plan_input_available_cash
        CHECK (available_cash IS NULL OR available_cash >= 0),
    ADD CONSTRAINT ck_plan_input_income_source
        CHECK (income_source IS NULL OR income_source IN ('OPEN_BANKING', 'MANUAL')),
    ADD CONSTRAINT ck_plan_input_asset_source
        CHECK (asset_source IS NULL OR asset_source IN ('OPEN_BANKING', 'MANUAL'));

INSERT INTO config_effective
  (fact_code, category, item, value_text, value_num, unit, apply_condition, source,
   source_url, confidence, change_cycle, related_feature, note, effective_from, updated_by)
VALUES
  ('FCT-170', '버팀목', '청년 버팀목 순자산 기준', '3억 4,500만원 이하', 345000000, '원',
   '청년전용 버팀목', '주택도시기금',
   'https://nhuf.molit.go.kr/FP/FP05/FP0502/FP05020301.jsp', 'CONFIRMED', '연1회', 'POL-01',
   '새 전세 플로우 확정 기준', '2026-09-04', 'flow-refactor'),
  ('FCT-171', '버팀목', '청년 버팀목 대출 한도', '최대 1억 5천만원', 150000000, '원',
   '만 25세 미만 단독세대주는 별도 한도 확인', '주택도시기금',
   'https://nhuf.molit.go.kr/FP/FP05/FP0502/FP05020301.jsp', 'CONFIRMED', '고시', 'POL-02',
   '은행 심사 결과가 우선', '2026-09-04', 'flow-refactor'),
  ('FCT-172', '버팀목', '청년 버팀목 예상 최저금리', '연 2.2%', 2.2, '%',
   '소득구간·우대 전 기본 안내 범위', '주택도시기금',
   'https://nhuf.molit.go.kr/FP/FP05/FP0502/FP05020301.jsp', 'CONFIRMED', '수시', 'POL-02',
   '실제 적용금리는 은행 확인', '2026-09-04', 'flow-refactor'),
  ('FCT-173', '버팀목', '청년 버팀목 예상 최고금리', '연 3.3%', 3.3, '%',
   '소득구간·우대 전 기본 안내 범위', '주택도시기금',
   'https://nhuf.molit.go.kr/FP/FP05/FP0502/FP05020301.jsp', 'CONFIRMED', '수시', 'POL-02',
   '실제 적용금리는 은행 확인', '2026-09-04', 'flow-refactor'),
  ('FCT-174', '버팀목', '청년 버팀목 기본 연령 상한', '만 34세 이하', 34, '세',
   '병역 이행자는 복무기간만큼 연장, 최대 만 39세', '주택도시기금',
   'https://nhuf.molit.go.kr/FP/FP05/FP0502/FP05020301.jsp', 'CONFIRMED', '고시', 'POL-01',
   '일 단위 판정은 만 35세 생일 전까지', '2026-09-04', 'flow-refactor'),
  ('FCT-175', '버팀목', '청년 버팀목 임차보증금 상한', '3억원 이하', 300000000, '원',
   '청년전용 버팀목 대상 주택', '주택도시기금',
   'https://nhuf.molit.go.kr/FP/FP05/FP0502/FP05020301.jsp', 'CONFIRMED', '고시', 'POL-01',
   NULL, '2026-09-04', 'flow-refactor');

UPDATE plan_step SET step_name = '대출 진단·매물 안전 확인 완료' WHERE step_code = 'FIRST_DIAGNOSIS';
UPDATE plan_step SET step_name = '대출 상품·보증 확정 완료' WHERE step_code = 'SECOND_POLICY_SELECTION';
UPDATE plan_step SET step_name = '계약·대출 실행 완료' WHERE step_code = 'THIRD_EXECUTION';

-- 유일 sequence 제약과 충돌하지 않도록 기존 값을 임시 구간으로 옮긴 후 재배치한다.
UPDATE step_task SET sequence = sequence + 100;

UPDATE step_task t SET task_name = '금융정보 확인', sequence = 1
  WHERE t.task_code = 'INPUT_INCOME_ASSET';
UPDATE step_task t SET task_name = '대출 진단 정보 입력', sequence = 2
  WHERE t.task_code = 'INPUT_HOUSING_CONDITION';
UPDATE step_task t SET task_name = '전세대출 예상 스펙 확인', sequence = 3
  WHERE t.task_code = 'REVIEW_DIAGNOSIS';

UPDATE step_task t
   SET plan_step_id = target.id, task_name = '가능 대출 목록 확인', sequence = 4
  FROM plan_step source
  JOIN plan_step target ON target.plan_id = source.plan_id AND target.step_code = 'FIRST_DIAGNOSIS'
 WHERE t.plan_step_id = source.id AND t.task_code = 'LOAN_LIMIT_CHECK';
UPDATE step_task t
   SET plan_step_id = target.id, task_name = '매물 후보 찾기', sequence = 5
  FROM plan_step source
  JOIN plan_step target ON target.plan_id = source.plan_id AND target.step_code = 'FIRST_DIAGNOSIS'
 WHERE t.plan_step_id = source.id AND t.task_code = 'PROPERTY_SEARCH';
UPDATE step_task t
   SET plan_step_id = target.id, task_name = '건축물대장 확인', sequence = 6
  FROM plan_step source
  JOIN plan_step target ON target.plan_id = source.plan_id AND target.step_code = 'FIRST_DIAGNOSIS'
 WHERE t.plan_step_id = source.id AND t.task_code = 'BUILDING_REGISTER_CHECK';
UPDATE step_task t
   SET plan_step_id = target.id, task_name = '전월세 실거래가 확인', sequence = 7
  FROM plan_step source
  JOIN plan_step target ON target.plan_id = source.plan_id AND target.step_code = 'FIRST_DIAGNOSIS'
 WHERE t.plan_step_id = source.id AND t.task_code = 'ACTUAL_PRICE_CHECK';
UPDATE step_task t
   SET plan_step_id = target.id, task_name = '등기부등본 확인', sequence = 8
  FROM plan_step source
  JOIN plan_step target ON target.plan_id = source.plan_id AND target.step_code = 'FIRST_DIAGNOSIS'
 WHERE t.plan_step_id = source.id AND t.task_code = 'REGISTER_CHECK';

UPDATE step_task SET task_name = '은행 사전상담', sequence = 1 WHERE task_code = 'BANK_CONSULTATION';
UPDATE step_task SET task_name = '월세 지원 정책 확인', sequence = 4 WHERE task_code = 'MONTHLY_SUPPORT_CHECK';
UPDATE step_task SET task_name = '대출 신청 서류 준비', sequence = 5 WHERE task_code = 'DOCUMENT_CHECK';

UPDATE step_task SET task_name = '계약서 확인', sequence = 3 WHERE task_code = 'CONTRACT_CHECK';
UPDATE step_task SET task_name = '대출 불가 반환 특약 확인', sequence = 4 WHERE task_code = 'SPECIAL_CLAUSE_CHECK';
UPDATE step_task SET task_name = '계약금 지급·영수증 보관', sequence = 5 WHERE task_code = 'DEPOSIT_PAYMENT';
UPDATE step_task SET task_name = '계약 후 확정일자 받기', sequence = 6 WHERE task_code = 'FIXED_DATE';
UPDATE step_task SET task_name = '잔금 지급', sequence = 8 WHERE task_code = 'BALANCE_PAYMENT';
UPDATE step_task SET task_name = '전입신고', sequence = 9 WHERE task_code = 'MOVE_IN_REPORT';
UPDATE step_task SET task_name = '반환보증·보증료 지원 확인', sequence = 1 WHERE task_code = 'GUARANTEE_CHECK';
UPDATE step_task SET sequence = 2 WHERE task_code = 'REGISTER_FIXED_EXPENSE';
UPDATE step_task SET sequence = 3 WHERE task_code = 'FIRST_MONTH_CHECKIN';

INSERT INTO step_task (plan_step_id, task_code, task_name, sequence, status, is_irreversible)
SELECT ps.id, 'SELECT_LOAN_PRODUCT', '대출 상품 확정', 2, 'TODO', false
  FROM plan_step ps JOIN plan p ON p.id = ps.plan_id
 WHERE ps.step_code = 'SECOND_POLICY_SELECTION' AND p.lease_type IN ('JEONSE', 'BANJEONSE')
ON CONFLICT (plan_step_id, task_code) DO NOTHING;
INSERT INTO step_task (plan_step_id, task_code, task_name, sequence, status, is_irreversible)
SELECT ps.id, 'SELECT_GUARANTEE', '보증 방식 확인', 3, 'TODO', false
  FROM plan_step ps JOIN plan p ON p.id = ps.plan_id
 WHERE ps.step_code = 'SECOND_POLICY_SELECTION' AND p.lease_type IN ('JEONSE', 'BANJEONSE')
ON CONFLICT (plan_step_id, task_code) DO NOTHING;
INSERT INTO step_task (plan_step_id, task_code, task_name, sequence, status, is_irreversible)
SELECT ps.id, 'RECORD_BANK_CONSULTATION', '은행 상담 결과 입력', 6, 'TODO', false
  FROM plan_step ps JOIN plan p ON p.id = ps.plan_id
 WHERE ps.step_code = 'SECOND_POLICY_SELECTION' AND p.lease_type IN ('JEONSE', 'BANJEONSE')
ON CONFLICT (plan_step_id, task_code) DO NOTHING;
INSERT INTO step_task (plan_step_id, task_code, task_name, sequence, status, is_irreversible)
SELECT ps.id, 'PROPERTY_VISIT', '부동산 방문', 1, 'TODO', false
  FROM plan_step ps WHERE ps.step_code = 'THIRD_EXECUTION'
ON CONFLICT (plan_step_id, task_code) DO NOTHING;
INSERT INTO step_task (plan_step_id, task_code, task_name, sequence, status, is_irreversible)
SELECT ps.id, 'SELECT_FINAL_PROPERTY', '최종 매물 결정', 2, 'TODO', false
  FROM plan_step ps WHERE ps.step_code = 'THIRD_EXECUTION'
ON CONFLICT (plan_step_id, task_code) DO NOTHING;
INSERT INTO step_task (plan_step_id, task_code, task_name, sequence, status, is_irreversible)
SELECT ps.id, 'APPLY_LOAN', '전세대출 신청', 7, 'TODO', true
  FROM plan_step ps JOIN plan p ON p.id = ps.plan_id
 WHERE ps.step_code = 'THIRD_EXECUTION' AND p.lease_type IN ('JEONSE', 'BANJEONSE')
ON CONFLICT (plan_step_id, task_code) DO NOTHING;
