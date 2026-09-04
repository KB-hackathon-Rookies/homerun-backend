-- #100. 기금대출(청년/일반 버팀목) 승인은 사람 조건뿐 아니라 집 조건도 통과해야 한다
-- (강태훈 공유 플로우 정의). CLAUDE.md 가 "대출도 거절된다"고 명시한 두 조건만 추가한다 —
-- 위반건축물, 다가구. 소유자 불일치·신탁등기는 "위험할 수 있다" 수준이라 하드 블록으로
-- 단정하지 않고 이번엔 뺐다.
--
-- V22(#85, 이미 머지됨)의 version 1 행은 안 건드리고 새 버전을 추가한다. 둘 다 여전히
-- DRAFT다 — 검수 전이라 판정에 안 쓰인다는 원칙은 그대로다.

INSERT INTO policy_rule (policy_id, version, rule_json, status, effective_from, drafted_by) VALUES
  ((SELECT id FROM policy WHERE code = 'JEONSE-YOUTH-BEOTIMMOK'), 2,
   '{
      "operator": "AND",
      "conditions": [
        {"code": "HOUSEHOLD_HOMELESS", "field": "household_homeless", "op": "eq", "value": true},
        {"code": "HOUSEHOLDER_STATUS", "field": "householder_status", "op": "ne", "value": "NOT_HOUSEHOLDER"},
        {"code": "NO_DUPLICATE_LOAN", "field": "has_existing_jeonse_loan", "op": "eq", "value": false},
        {"code": "AGE_UPPER_BOUND", "field": "birth_date", "adjust_field": "military_months", "fact_code": "FCT-174", "op": "age_within_years_adjusted"},
        {"code": "INCOME_CAP", "field": "monthly_income", "fact_code": "FCT-003", "op": "annual_lte"},
        {"code": "NET_ASSET_CAP", "field": "net_assets", "fact_code": "FCT-170", "op": "lte"},
        {"code": "DEPOSIT_CAP", "field": "hope_deposit", "fact_code": "FCT-175", "op": "lte"},
        {"code": "NOT_VIOLATION_BUILDING", "field": "is_violation_building", "op": "eq", "value": false},
        {"code": "NOT_MULTI_HOUSEHOLD", "field": "is_multi_household", "op": "eq", "value": false}
      ],
      "amount": {"ratio_fact_code": "FCT-008", "cap_fact_code": "FCT-171"},
      "rate": {"min_fact_code": "FCT-172", "max_fact_code": "FCT-173"},
      "note": "#100: 집 조건(위반건축물·다가구) 추가. 매물 없이 판정하면 이 둘은 NEED_INFO로 빠진다"
    }'::jsonb,
   'DRAFT', '2026-09-04', 'seed-#100'),

  ((SELECT id FROM policy WHERE code = 'JEONSE-GENERAL-BEOTIMMOK'), 2,
   '{
      "operator": "AND",
      "conditions": [
        {"code": "HOUSEHOLD_HOMELESS", "field": "household_homeless", "op": "eq", "value": true},
        {"code": "HOUSEHOLDER_STATUS", "field": "householder_status", "op": "ne", "value": "NOT_HOUSEHOLDER"},
        {"code": "NO_DUPLICATE_LOAN", "field": "has_existing_jeonse_loan", "op": "eq", "value": false},
        {"code": "NOT_VIOLATION_BUILDING", "field": "is_violation_building", "op": "eq", "value": false},
        {"code": "NOT_MULTI_HOUSEHOLD", "field": "is_multi_household", "op": "eq", "value": false}
      ],
      "note": "가구별 소득·자산·보증한도는 은행 사전심사 확인 사항 — 조건식으로 못 박지 않는다. #100: 집 조건 추가"
    }'::jsonb,
   'DRAFT', '2026-09-04', 'seed-#100');
