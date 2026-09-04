-- #102 (POL-03-01). 청년전용 버팀목 전용면적 상한(FCT-005, 85㎡)을 조건에 추가한다.
-- FCT-006(만 25세 미만 단독세대주는 60㎡ 예외)은 조건부 분기라 이번엔 안 넣는다.
--
-- V22(#85)/V24(#100)의 이전 버전은 안 건드리고 새 DRAFT 버전을 추가한다.

INSERT INTO policy_rule (policy_id, version, rule_json, status, effective_from, drafted_by) VALUES
  ((SELECT id FROM policy WHERE code = 'JEONSE-YOUTH-BEOTIMMOK'), 3,
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
        {"code": "NOT_MULTI_HOUSEHOLD", "field": "is_multi_household", "op": "eq", "value": false},
        {"code": "AREA_CAP", "field": "area_m2", "fact_code": "FCT-005", "op": "lte"}
      ],
      "amount": {"ratio_fact_code": "FCT-008", "cap_fact_code": "FCT-171"},
      "rate": {"min_fact_code": "FCT-172", "max_fact_code": "FCT-173"},
      "note": "#102: 전용면적 85㎡ 상한(FCT-005) 추가. 만 25세 미만 단독세대주 60㎡ 예외(FCT-006)는 아직 반영 안 함"
    }'::jsonb,
   'DRAFT', '2026-09-04', 'seed-#102');
