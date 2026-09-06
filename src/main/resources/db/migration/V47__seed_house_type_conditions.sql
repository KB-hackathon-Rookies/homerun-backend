-- BR-09 주택유형 조건(#190). 명세서 4.3 표가 상품마다 대상 주택유형을 정의한다.
--
--   청년/일반 버팀목: 아파트 · 오피스텔(주거용) · 연립다세대
--   서울시 이자지원:  주택 · 주거용 오피스텔 · 노인복지주택 (다중주택 제외)
--
-- 유형 목록은 조건 리터럴(value 배열)에 둔다. 금액·비율이 아닌 조건 표현이라 config_effective
-- 대상이 아니고, 대응하는 팩트도 레지스트리에 없다.
--
-- 기존 ACTIVE 규칙(version 3)의 rule_json 을 복사해 조건 하나를 붙인다. 조건식을 처음부터
-- 다시 적으면 v3 과 어긋난 채로 검수될 수 있다. DRAFT 로 심는다 -- 조건식 최종 확정은 사람
-- 몫이고(ADM-04), 특히 이 화이트리스트는 검수자가 원문과 대조해야 한다: 단독·다가구가 버팀목
-- 원문상 정말 제외인지(O-13 계열), 명세서 표가 자동판별 가능한 3종만 적은 것은 아닌지.
--
-- 서울시의 "주택·노인복지주택·다중주택 제외"는 현재 house_type 값 체계(자동판별 3종)에서는
-- 전부 "주택"에 해당해 버팀목과 같은 목록이 된다. 수동 유형 입력(FR-P1-09)이 생겨 값이
-- 늘어나면 목록을 갈라야 한다.

INSERT INTO policy_rule (policy_id, version, rule_json, status, effective_from, drafted_by)
SELECT pr.policy_id, 4,
       jsonb_set(
           pr.rule_json,
           '{conditions}',
           (pr.rule_json->'conditions') ||
           '[{"code": "HOUSE_TYPE", "field": "house_type", "op": "in",
              "value": ["APARTMENT", "OFFICETEL", "ROW_HOUSE"]}]'::jsonb),
       'DRAFT', DATE '2026-09-06', 'seed-#190'
FROM policy_rule pr
WHERE pr.version = 3
  AND pr.status = 'ACTIVE'
  AND pr.policy_id IN (SELECT id FROM policy WHERE code IN
      ('JEONSE-YOUTH-BEOTIMMOK', 'JEONSE-GENERAL-BEOTIMMOK', 'JEONSE-SEOUL-INTEREST-SUPPORT'));
