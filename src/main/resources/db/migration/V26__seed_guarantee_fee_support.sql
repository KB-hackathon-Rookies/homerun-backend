-- #104 (GTE-01-04). 전세보증금 반환보증 보증료 지원사업 자격 판정.
--
-- FCT-033(보증료 지원 소득요건, value_num 없이 "청년 5,000만 / 청년 외 6,000만 / 신혼부부
-- 7,500만" 텍스트로만 있던 값)를 계산 가능한 원자값 3개로 분해한다(V9 과 같은 패턴).
-- confidence 는 출처(FCT-033, CONFIRMED)를 그대로 물려받는다.
--
-- 신혼부부(FCT-178)는 엔진에 안 태운다 — plan_input 에 혼인 기간이 없어서 "혼인 7년 이내"를
-- 확정할 방법이 없다. 결정 근거는 FCT-176/177 세팅에만 쓰지만, FCT-033 이 가리키던 세 값을
-- 전부 팩트 레지스트리에 남겨 두는 게 "분해하면서 원문 값을 누락하지 않는다"는 V9 원칙에 맞는다.
--
-- 정책명에 지역을 안 넣었다 — FCT-033 출처가 '서울주거포털'이라 서울 한정 사업일 가능성이
-- 있지만 원문에 "서울시"가 명시돼 있지 않아 단정하지 않는다. 확인되면 이름/코드를 정정한다.

INSERT INTO config_effective
  (fact_code, category, item, value_text, value_num, unit, apply_condition, source, confidence, change_cycle, related_feature, note, effective_from, updated_by)
VALUES
  ('FCT-176', '지원금', '보증료 지원 청년 소득기준', '연 5,000만원 이하 (선착순 예산 소진 유의)', 50000000.0, '원',
    '만 19~39세', '서울주거포털', 'CONFIRMED', '연1회', 'GTE-01', 'FCT-033 분해', '2026-01-01', 'fact-registry'),
  ('FCT-177', '지원금', '보증료 지원 일반 소득기준', '연 6,000만원 이하 (선착순 예산 소진 유의)', 60000000.0, '원',
    '청년·신혼부부 외', '서울주거포털', 'CONFIRMED', '연1회', 'GTE-01', 'FCT-033 분해', '2026-01-01', 'fact-registry'),
  ('FCT-178', '지원금', '보증료 지원 신혼부부 소득기준', '연 7,500만원 이하 (부부합산, 선착순 예산 소진 유의)', 75000000.0, '원',
    '혼인 7년 이내', '서울주거포털', 'CONFIRMED', '연1회', 'GTE-01', 'FCT-033 분해. plan_input 에 혼인 기간이 없어 엔진 미반영 — 항상 NEED_INFO', '2026-01-01', 'fact-registry');

INSERT INTO policy (code, name, category, tier, operator, guarantee_agency_id, source, status, detail_url) VALUES
  ('RETURN-GUARANTEE-FEE-SUPPORT', '전세보증금 반환보증료 지원사업', '지원금', 'L2', NULL,
    NULL, 'MANUAL', 'ACTIVE', NULL);

INSERT INTO policy_rule (policy_id, version, rule_json, status, effective_from, drafted_by) VALUES
  ((SELECT id FROM policy WHERE code = 'RETURN-GUARANTEE-FEE-SUPPORT'), 1,
   '{
      "operator": "AND",
      "conditions": [
        {"code": "INCOME_CAP_FEE_SUPPORT", "field": "monthly_income", "fact_code": "FCT-176", "alt_fact_code": "FCT-177", "op": "annual_lte_by_age_group"}
      ],
      "note": "#104: 기혼(신혼부부 포함)은 혼인 기간 데이터가 없어 항상 NEED_INFO. 미혼만 나이로 청년/일반을 가른다"
    }'::jsonb,
   'DRAFT', '2026-09-04', 'seed-#104');

-- 곁다리 버그 수정: V22 의 JEONSE-SEOUL-INTEREST-SUPPORT 가 INCOME_CAP 에 FCT-033(미지원
-- op `annual_lte_by_group`)을 잘못 참조하고 있었다. 이 정책의 소득 상한은 FCT-036(연
-- 5,000만원 이하, 단일값)이 맞는 팩트라 이미 지원되는 `annual_lte` 로 정정한다.
-- V22 는 머지된 파일이라 못 건드리므로 새 버전을 추가한다.
INSERT INTO policy_rule (policy_id, version, rule_json, status, effective_from, drafted_by) VALUES
  ((SELECT id FROM policy WHERE code = 'JEONSE-SEOUL-INTEREST-SUPPORT'), 2,
   '{
      "operator": "AND",
      "conditions": [
        {"code": "INCOME_CAP", "field": "monthly_income", "fact_code": "FCT-036", "op": "annual_lte"},
        {"code": "HOUSING_REQUIREMENT", "fact_code": "FCT-034", "op": "external_check"}
      ],
      "note": "#104: INCOME_CAP 이 FCT-033(잘못된 참조, annual_lte_by_group 미지원)을 쓰던 V22 버그 수정. FCT-036 + annual_lte 로 교체"
    }'::jsonb,
   'DRAFT', '2026-09-04', 'seed-#104');
