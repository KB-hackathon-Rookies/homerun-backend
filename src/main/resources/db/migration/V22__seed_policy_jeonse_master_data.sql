-- #85 (POL-01/POL-02) 전세대출 정책 마스터데이터.
--
-- guarantee_agency/policy/policy_rule 은 V1 부터 있었지만 시드가 없었다. 방식 B(설계 원칙대로
-- policy_rule 의 버전 있는 JSON 조건식으로 판정)를 따르기로 했으므로 여기서 데이터부터 놓는다.
--
-- rule_json 을 해석할 룰엔진은 아직 없다(#80 으로 merge 된 JeonseLoanDiagnosisService 는
-- FactRegistry + Java if 로 판정하고 이 테이블들을 전혀 읽지 않는다). 그래서 아래 policy_rule 은
-- 전부 status = 'DRAFT' 다 — 검수도, 엔진 연동도 안 됐으니 ACTIVE 로 두면 안 된다(ACTIVE 만
-- 판정에 쓴다는 게 스키마 규칙이다). rule_json 의 조건 표현 방식은 엔진을 실제로 만드는 다음
-- 이슈에서 확정될 수 있다 — 지금은 "어떤 정책에 어떤 팩트가 걸리는지"를 데이터로 남기는 것이 목적.
--
-- 청년전용 버팀목 조건은 JeonseLoanDiagnosisService(youth())가 이미 참조하는 팩트 코드
-- (FCT-003, FCT-008, FCT-170~175) 를 그대로 쓴다. FCT-004/FCT-009 는 여전히 CONFLICT 라서
-- 안 쓴다 — 청년 버팀목 한정으로는 FCT-170/171(REVIEW)이 사실상 대체값이다.

-- 1. 보증기관 (FCT-056~062)
INSERT INTO guarantee_agency (code, name, agency_type, limit_basis, fee_rate_min, fee_rate_max) VALUES
  ('HF', '한국주택금융공사', 'PUBLIC', '증빙소득 약 3.5배 이내', 0.040, 0.180),
  ('HUG', '주택도시보증공사', 'PUBLIC', '보증금 기준', 0.097, 0.211),
  ('SGI', '서울보증보험', 'PRIVATE', '자체 기준, 3사 중 가장 큼', 0.229, 0.260);

-- 2. 정책·상품 마스터
INSERT INTO policy (code, name, category, tier, operator, guarantee_agency_id, source, status, detail_url) VALUES
  ('JEONSE-YOUTH-BEOTIMMOK', '청년전용 버팀목전세자금대출', '기금대출', 'L1', '주택도시기금',
    NULL, 'MANUAL', 'ACTIVE', 'https://nhuf.molit.go.kr/FP/FP05/FP0502/FP05020301.jsp'),
  ('JEONSE-GENERAL-BEOTIMMOK', '버팀목전세자금대출(일반)', '기금대출', 'L1', '주택도시기금',
    NULL, 'MANUAL', 'ACTIVE', 'https://nhuf.molit.go.kr/FP/FP05/FP0502/FP05020101.jsp'),
  ('JEONSE-SEOUL-INTEREST-SUPPORT', '서울시 청년 임차보증금 이자지원', '지원금', 'L1', '서울특별시',
    NULL, 'MANUAL', 'ACTIVE', NULL),
  ('RETURN-GUARANTEE-HUG', '주택도시보증공사 전세보증금반환보증', '보증', 'L2', '주택도시보증공사',
    (SELECT id FROM guarantee_agency WHERE code = 'HUG'), 'MANUAL', 'ACTIVE', 'https://www.khug.or.kr'),
  ('RETURN-GUARANTEE-HF', '한국주택금융공사 전세지킴보증', '보증', 'L2', '한국주택금융공사',
    (SELECT id FROM guarantee_agency WHERE code = 'HF'), 'MANUAL', 'ACTIVE', NULL),
  ('RETURN-GUARANTEE-SGI', '서울보증보험 전세금보장신용보험', '보증', 'L2', '서울보증보험',
    (SELECT id FROM guarantee_agency WHERE code = 'SGI'), 'MANUAL', 'ACTIVE', NULL);

-- 중기청 대출(FCT-135) — 2025-01 신규 취급 중단. 대체 상품으로 청년전용 버팀목을 이어준다.
INSERT INTO policy (code, name, category, tier, operator, guarantee_agency_id, source, status, discontinued_at, replaced_by_id) VALUES
  ('JEONSE-JUNGGICHEONG-DISCONTINUED', '중소기업 취업청년 전월세보증금대출', '기금대출', 'L1', '주택도시기금',
    NULL, 'MANUAL', 'DISCONTINUED', '2025-01-01', (SELECT id FROM policy WHERE code = 'JEONSE-YOUTH-BEOTIMMOK'));

-- 3. 조건식 초안 (전부 DRAFT, version 1). rule_json 은 "field(plan_input 컬럼) op fact_code" 형태의
--    최소 표현이고 엔진 스펙이 아니다 — 엔진 이슈에서 스키마가 바뀔 수 있다.
INSERT INTO policy_rule (policy_id, version, rule_json, status, effective_from, drafted_by) VALUES
  ((SELECT id FROM policy WHERE code = 'JEONSE-YOUTH-BEOTIMMOK'), 1,
   '{
      "operator": "AND",
      "conditions": [
        {"code": "HOUSEHOLD_HOMELESS", "field": "household_homeless", "op": "eq", "value": true},
        {"code": "HOUSEHOLDER_STATUS", "field": "householder_status", "op": "ne", "value": "NOT_HOUSEHOLDER"},
        {"code": "NO_DUPLICATE_LOAN", "field": "has_existing_jeonse_loan", "op": "eq", "value": false},
        {"code": "AGE_UPPER_BOUND", "field": "birth_date", "adjust_field": "military_months", "fact_code": "FCT-174", "op": "age_within_years_adjusted"},
        {"code": "INCOME_CAP", "field": "monthly_income", "fact_code": "FCT-003", "op": "annual_lte"},
        {"code": "NET_ASSET_CAP", "field": "net_assets", "fact_code": "FCT-170", "op": "lte"},
        {"code": "DEPOSIT_CAP", "field": "hope_deposit", "fact_code": "FCT-175", "op": "lte"}
      ],
      "amount": {"ratio_fact_code": "FCT-008", "cap_fact_code": "FCT-171"},
      "rate": {"min_fact_code": "FCT-172", "max_fact_code": "FCT-173"}
    }'::jsonb,
   'DRAFT', '2026-09-04', 'seed-#85'),

  ((SELECT id FROM policy WHERE code = 'JEONSE-GENERAL-BEOTIMMOK'), 1,
   '{
      "operator": "AND",
      "conditions": [
        {"code": "HOUSEHOLD_HOMELESS", "field": "household_homeless", "op": "eq", "value": true},
        {"code": "HOUSEHOLDER_STATUS", "field": "householder_status", "op": "ne", "value": "NOT_HOUSEHOLDER"},
        {"code": "NO_DUPLICATE_LOAN", "field": "has_existing_jeonse_loan", "op": "eq", "value": false}
      ],
      "note": "가구별 소득·자산·보증한도는 은행 사전심사 확인 사항 — 조건식으로 못 박지 않는다"
    }'::jsonb,
   'DRAFT', '2026-01-01', 'seed-#85'),

  ((SELECT id FROM policy WHERE code = 'JEONSE-SEOUL-INTEREST-SUPPORT'), 1,
   '{
      "operator": "AND",
      "conditions": [
        {"code": "INCOME_CAP", "field": "monthly_income", "fact_code": "FCT-033", "op": "annual_lte_by_group"},
        {"code": "HOUSING_REQUIREMENT", "fact_code": "FCT-034", "op": "external_check"}
      ]
    }'::jsonb,
   'DRAFT', '2026-01-01', 'seed-#85'),

  ((SELECT id FROM policy WHERE code = 'RETURN-GUARANTEE-HUG'), 1,
   '{
      "operator": "AND",
      "conditions": [
        {"code": "PRICE_RATIO_126", "entity": "property", "field": "official_price", "fact_code": "FCT-054", "op": "deposit_lte_price_times_fact"},
        {"code": "LIMIT", "fact_code": "FCT-056", "op": "reference_only"}
      ],
      "note": "1순위 추천. 대출보증+반환보증 결합형"
    }'::jsonb,
   'DRAFT', '2026-01-01', 'seed-#85'),

  ((SELECT id FROM policy WHERE code = 'RETURN-GUARANTEE-HF'), 1,
   '{
      "operator": "AND",
      "conditions": [
        {"code": "REQUIRES_HF_JEONSE_LOAN", "op": "eq", "value": true},
        {"code": "LIMIT", "fact_code": "FCT-057", "op": "reference_only"}
      ],
      "note": "HF 전세대출 이용자만 가입 가능. 은행 상환보증이지 반환보증은 아니라는 점 화면 안내 필요"
    }'::jsonb,
   'DRAFT', '2026-01-01', 'seed-#85'),

  ((SELECT id FROM policy WHERE code = 'RETURN-GUARANTEE-SGI'), 1,
   '{
      "operator": "AND",
      "conditions": [
        {"code": "LIMIT", "fact_code": "FCT-058", "op": "reference_only"}
      ],
      "note": "2순위 추천. 아파트 외 물건도 가입 가능"
    }'::jsonb,
   'DRAFT', '2026-01-01', 'seed-#85');
