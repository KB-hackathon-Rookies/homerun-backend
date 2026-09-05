-- POL-01-03 Phase 1. 청년 주거급여 분리지급을 규칙 엔진으로 판정한다.
--
-- 이 정책이 Phase 1 인 이유는 전제조건이 전부 boolean 이기 때문이다. 소득 금액을 하나도 안
-- 걷고 "부모 가구가 주거급여를 받고 있지 않아 신청할 수 없습니다" 같은 확정적인 불가 판정을
-- 낼 수 있다.
--
--   FCT-044  부모 가구가 이미 주거급여 수급 중이어야 함   -- 원가구
--   FCT-043  부모와 주민등록상 시·군을 달리할 것          -- 원가구
--   FCT-042  만 19세~30세 미만 미혼 자녀
--
-- FCT-044 의 비고에 "소득 판정이 부모 가구 기준. 가구원 동의 필요 정책 1호" 라고 적혀 있다.
-- 이 요구사항이 왜 있는지가 팩트 하나에 이미 기록돼 있는 셈이다.

-- ---------------------------------------------------------------------------
-- 1. 원가구에 관한 입력 두 가지. 둘 다 여부만 받는다 -- 부모의 소득 금액이나 주소를 받지
--    않는다(SEC-01-01 · SEC-01-05).
-- ---------------------------------------------------------------------------

ALTER TABLE plan_input ADD COLUMN lives_apart_from_parents BOOLEAN;
ALTER TABLE plan_input ADD COLUMN parent_on_housing_benefit BOOLEAN;

COMMENT ON COLUMN plan_input.lives_apart_from_parents IS
    '부모와 주민등록상 시·군이 다른가(FCT-043). 주소가 아니라 다른지 여부만 받는다.';
COMMENT ON COLUMN plan_input.parent_on_housing_benefit IS
    '부모 가구가 이미 주거급여를 받고 있는가(FCT-044). 청년 단독 신청은 불가하다.';

-- ---------------------------------------------------------------------------
-- 2. FCT-042 의 연령 범위를 수치로 분리한다. V18(열람·발급 수수료)·V33(은행별 금리)과 같이
--    문장에 묻힌 값을 판정에 쓸 수 있게 갈라 놓는 것이다.
--
--    ★ 상한이 29 인 이유 -- 틀리기 제일 쉬운 곳이다.
--
--    age_range_adjusted 는 상한을 "이하"로 계산한다.
--        maxDate = birthDate.plusYears(maxAge + 1);
--        pass    = today.isBefore(maxDate);
--    즉 상한 팩트는 "만 N세 이하"의 N 이다(FCT-180 이 "만 34세 이하" = 34 로 같은 규약).
--
--    그런데 FCT-042 원문은 "만 19세~30세 미만"이라 상한이 배타적이다. 여기에 30 을 넣으면
--    만 30세까지 통과해 1년이 통째로 어긋난다. 배타 상한 30 = 포함 상한 29 다.
-- ---------------------------------------------------------------------------

INSERT INTO config_effective
  (fact_code, category, item, value_text, value_num, unit, apply_condition, source,
   confidence, change_cycle, related_feature, note, effective_from, updated_by)
VALUES
  ('FCT-209', '주거급여', '청년 분리지급 연령 하한', '만 19세 이상', 19.0, '세',
   '해당연도 1월 1일 기준', '복지로', 'CONFIRMED', '연1회', 'POL-01',
   'FCT-042 분해', '2026-01-01', 'fact-registry'),

  ('FCT-210', '주거급여', '청년 분리지급 연령 상한', '만 29세 이하', 29.0, '세',
   '출생월 기준', '복지로', 'CONFIRMED', '연1회', 'POL-01',
   'FCT-042 분해. 원문은 "만 30세 미만"이다 -- 판정 엔진의 상한은 "이하" 규약이라 배타 상한 30을 포함 상한 29로 옮겼다. 30을 넣으면 만 30세까지 통과해 1년이 어긋난다',
   '2026-01-01', 'fact-registry');

UPDATE config_effective
SET note = '연령 범위는 FCT-209(하한 19) · FCT-210(상한, 포함 규약이라 29)으로 분리했다'
WHERE fact_code = 'FCT-042';

-- ---------------------------------------------------------------------------
-- 3. 정책과 조건식.
--
--    규칙은 DRAFT 로 심는다. 조건식 초안까지가 여기 몫이고 최종 확정은 사람이 한다(ADM-04).
--    검수를 마치면 V31 처럼 별도 마이그레이션에서 ACTIVE 로 승격한다.
--
--    소득 조건은 external_check 로 남긴다. 분리지급의 소득 판정은 부모 가구 기준인데
--    (FCT-044) 원가구 소득을 걷는 경로가 아직 없다. 없는 값을 지어내는 대신 추가확인으로
--    두고, household_basis 로 "무엇을 확인해야 하는지"를 밝힌다(Phase 2에서 채운다).
-- ---------------------------------------------------------------------------

INSERT INTO policy (code, name, category, tier, operator, guarantee_agency_id, source, status, detail_url)
VALUES ('HOUSING-BENEFIT-YOUTH-SPLIT', '청년 주거급여 분리지급', '지원금', NULL, '국토교통부',
        NULL, 'MANUAL', 'ACTIVE', 'https://www.bokjiro.go.kr');

INSERT INTO policy_rule (policy_id, version, rule_json, status, effective_from, drafted_by)
VALUES (
  (SELECT id FROM policy WHERE code = 'HOUSING-BENEFIT-YOUTH-SPLIT'), 1,
  '{
     "operator": "AND",
     "conditions": [
       {"code": "AGE_RANGE_YOUTH_SPLIT", "field": "birth_date",
        "fact_code": "FCT-209", "alt_fact_code": "FCT-210", "op": "age_range_adjusted"},
       {"code": "UNMARRIED", "field": "marital_status", "op": "eq", "value": "SINGLE",
        "fact_code": "FCT-042"},
       {"code": "LIVES_APART_FROM_PARENTS", "field": "lives_apart_from_parents", "op": "eq", "value": true,
        "fact_code": "FCT-043", "household_basis": "ORIGIN"},
       {"code": "PARENT_ON_HOUSING_BENEFIT", "field": "parent_on_housing_benefit", "op": "eq", "value": true,
        "fact_code": "FCT-044", "household_basis": "ORIGIN"},
       {"code": "ORIGIN_HOUSEHOLD_INCOME", "fact_code": "FCT-038", "op": "external_check",
        "household_basis": "ORIGIN"}
     ],
     "note": "#160: 소득 판정은 부모 가구 기준이라(FCT-044) 원가구 소득을 걷기 전에는 항상 NEED_INFO 다. 병역 보정은 원문에 근거가 없어 넣지 않았다"
   }'::jsonb,
  'DRAFT', '2026-09-05', 'seed-#160');
