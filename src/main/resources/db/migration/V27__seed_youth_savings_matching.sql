-- #116 (POL-02-02). 청년미래적금 연령·소득 조건 판정.
--
-- FCT-085(연령, "만 19~34세" 텍스트)와 FCT-086(개인소득, 급여소득자/종합소득자/소상공인 세
-- 분기 텍스트)을 계산 가능한 원자값으로 분해한다(V9 패턴). confidence 는 출처(CONFIRMED)를
-- 그대로 물려받는다.
--
-- FCT-183(소상공인 매출 기준)은 분해는 하되 엔진에서 못 쓴다 — 사업 매출 데이터를 이 서비스가
-- 안 걷는다. FCT-087(가구소득, 중위소득 200%)도 마찬가지로 조건에는 넣지만(external_check,
-- 항상 NEED_INFO) 판정에 실제로 쓰지 못한다 — 팩트 자체 note에 "가구중위소득 판정 없으면
-- 항상 NEED_INFO"라고 이미 적혀 있다.

INSERT INTO config_effective
  (fact_code, category, item, value_text, value_num, unit, apply_condition, source, confidence, change_cycle, related_feature, note, effective_from, updated_by)
VALUES
  ('FCT-179', '적금', '청년미래적금 연령 하한', '만 19세 이상', 19.0, '세', NULL, 'KB Think', 'CONFIRMED', '연1회', 'POL-02', 'FCT-085 분해', '2026-01-01', 'fact-registry'),
  ('FCT-180', '적금', '청년미래적금 연령 상한', '만 34세 이하', 34.0, '세', '병역 이행기간 제외', 'KB Think', 'CONFIRMED', '연1회', 'POL-02', 'FCT-085 분해', '2026-01-01', 'fact-registry'),
  ('FCT-181', '적금', '청년미래적금 급여소득자 소득기준', '연 7,500만원 이하', 75000000.0, '원', '급여소득자(근로소득)', 'KB Think', 'CONFIRMED', '연1회', 'POL-02', 'FCT-086 분해', '2026-01-01', 'fact-registry'),
  ('FCT-182', '적금', '청년미래적금 종합소득자 소득기준', '종합소득금액 6,300만원 이하', 63000000.0, '원', '종합소득 신고자', 'KB Think', 'CONFIRMED', '연1회', 'POL-02', 'FCT-086 분해', '2026-01-01', 'fact-registry'),
  ('FCT-183', '적금', '청년미래적금 소상공인 매출기준', '매출 3억원 이하', 300000000.0, '원', '소상공인', 'KB Think', 'CONFIRMED', '연1회', 'POL-02', 'FCT-086 분해. 사업 매출 데이터를 안 걷어서 엔진 미반영', '2026-01-01', 'fact-registry');

INSERT INTO policy (code, name, category, tier, operator, guarantee_agency_id, source, status, detail_url) VALUES
  ('YOUTH-FUTURE-SAVINGS', '청년미래적금', '적금', NULL, NULL,
    NULL, 'MANUAL', 'ACTIVE', NULL);

INSERT INTO policy_rule (policy_id, version, rule_json, status, effective_from, drafted_by) VALUES
  ((SELECT id FROM policy WHERE code = 'YOUTH-FUTURE-SAVINGS'), 1,
   '{
      "operator": "AND",
      "conditions": [
        {"code": "AGE_RANGE", "field": "birth_date", "adjust_field": "military_months",
         "fact_code": "FCT-179", "alt_fact_code": "FCT-180", "op": "age_range_adjusted"},
        {"code": "INCOME_CAP_BY_EMPLOYMENT", "field": "monthly_income",
         "fact_code": "FCT-181", "alt_fact_code": "FCT-182", "op": "annual_lte_by_employment_type"},
        {"code": "HOUSEHOLD_INCOME_RATIO", "fact_code": "FCT-087", "op": "external_check"},
        {"code": "EXCLUSION_CHECK", "fact_code": "FCT-088", "op": "external_check"}
      ],
      "note": "#116: 가구소득·최근 3년 금융소득종합과세 이력은 걷는 입력이 없어 항상 NEED_INFO. 이 정책은 그래서 PASS가 사실상 안 나온다 — 의도된 설계다"
    }'::jsonb,
   'DRAFT', '2026-09-04', 'seed-#116');
