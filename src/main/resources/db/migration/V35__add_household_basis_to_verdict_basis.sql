-- POL-01-03 Phase 0. 조건을 어느 가구 기준으로 봤는지를 남긴다.
--
-- 청년 정책은 소득·자산을 볼 때 "누구의 가구인가"를 두 가지로 나눠 본다. 같은 사람도 어느
-- 기준을 대느냐에 따라 결과가 뒤집힌다(FCT-044: 청년 주거급여 분리지급은 소득 판정이 부모
-- 가구 기준이다). 그런데 지금은 판정 근거 어디에도 그 기준이 안 남아, 사용자도 리뷰어도
-- 무엇을 기준으로 본 조건인지 알 수 없다.
--
-- 판정을 두 벌로 쪼개지 않는다. 실제로 갈리는 것은 소득·자산 조건뿐이고 연령·무주택·보증금
-- 상한은 가구 기준과 무관하다. 판정을 통째로 두 개 만들면 같은 조건을 두 번 평가하게 되고
-- uq_policy_verdict (plan_id, policy_id, rule_id) 에도 걸린다 -- V23 이 property_id 에서 같은
-- 벽을 만나 미뤄 둔 문제다. 그래서 판정은 정책당 하나로 두고 조건에 축을 붙인다.
--
-- 컬럼은 nullable 로 더하기만 한다. UNIQUE 제약에도 재판정 upsert 에도 영향이 없다.
-- NULL 은 두 가지 뜻으로 쓴다.
--   · 이 마이그레이션 이전에 쌓인 판정 근거 (다시 판정하면 채워진다)
--   · RULE_NOT_ACTIVE 처럼 자격 조건이 아니라 판정을 못 한 사정을 담는 항목
--     -- 이런 항목에 SELF 를 박으면 독립가구 기준으로 따져 본 것처럼 읽힌다.

ALTER TABLE verdict_basis ADD COLUMN household_basis VARCHAR(20);

ALTER TABLE verdict_basis ADD CONSTRAINT ck_verdict_basis_household
    CHECK (household_basis IS NULL OR household_basis IN ('SELF', 'ORIGIN'));

-- 청년미래적금의 가구소득 조건은 본인이 아니라 본인이 속한 가구 기준이다(FCT-087:
-- "기준중위소득 200% 이하", note 에 "가구중위소득 판정 없으면 항상 NEED_INFO").
-- 부모와 함께 잡히는 가구라 ORIGIN 이다.
--
-- 판정 결과는 이 UPDATE 로 바뀌지 않는다. 이 조건은 op 가 external_check 라 엔진이 모르는
-- op 로 보고 계속 NEED_INFO 로 떨어진다(V27 주석에 "의도된 설계"라고 적혀 있다). 달라지는
-- 것은 사용자가 보는 이유뿐이다 -- 그냥 "추가 확인"에서 "원가구 기준 소득이라 확인 필요"로.
--
-- 조건 배열의 순서를 유지하면서 코드가 맞는 항목에만 키를 더한다. 인덱스로 지목하면
-- 나중에 조건이 하나 끼어들 때 엉뚱한 조건에 붙는다.
UPDATE policy_rule pr
SET rule_json = jsonb_set(
        pr.rule_json,
        '{conditions}',
        (SELECT jsonb_agg(
                    CASE WHEN element->>'code' = 'HOUSEHOLD_INCOME_RATIO'
                         THEN element || '{"household_basis":"ORIGIN"}'::jsonb
                         ELSE element END
                    ORDER BY ordinality)
         FROM jsonb_array_elements(pr.rule_json->'conditions') WITH ORDINALITY AS t(element, ordinality)))
WHERE pr.policy_id = (SELECT id FROM policy WHERE code = 'YOUTH-FUTURE-SAVINGS')
  AND pr.rule_json->'conditions' @> '[{"code": "HOUSEHOLD_INCOME_RATIO"}]';
