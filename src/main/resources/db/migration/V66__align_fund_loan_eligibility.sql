-- 청년·일반 버팀목 공식 원문(2026-09-07 확인)에 맞춘 사전 판정 정합성 보강.
-- 기존 전세대출 여부 하나만 false라고 해서 공식 중복대출 금지 범위를 모두 통과한 것으로
-- 판단하지 않으며, 주택유형 이름을 3종으로 제한하지 않고 주거용 여부로 확인한다.

UPDATE config_effective
SET confidence = 'RETIRED',
    effective_to = DATE '2026-09-06',
    note = CASE fact_code
        WHEN 'FCT-004' THEN '충돌값 사용 중단. 2026년 공식 기준 FCT-170으로 대체'
        WHEN 'FCT-009' THEN '계약일 경과 규칙이 빠진 충돌값 사용 중단. 신규 계약은 FCT-171로 대체'
    END,
    updated_by = 'policy-alignment',
    updated_at = now()
WHERE fact_code IN ('FCT-004', 'FCT-009');

UPDATE config_effective
SET confidence = 'CONFIRMED',
    note = CASE fact_code
        WHEN 'FCT-170' THEN '2026년 기준 공식 원문 재확인'
        WHEN 'FCT-171' THEN '2025-06-27 이후 계약 체결 건 1.5억원. 이전 계약은 2억원이므로 계약일 확인 필요'
    END,
    updated_by = 'policy-alignment',
    updated_at = now()
WHERE fact_code IN ('FCT-170', 'FCT-171');

INSERT INTO config_effective
  (fact_code, category, item, value_text, value_num, unit, apply_condition, source, source_url,
   confidence, change_cycle, related_feature, note, effective_from, updated_by)
VALUES
  ('FCT-257', '버팀목', '세대원 전원 무주택', '세대주를 포함한 세대원 전원이 무주택이어야 함',
   NULL, NULL, '청년·일반 버팀목', '주택도시기금',
   'https://nhuf.molit.go.kr/FP/FP05/FP0502/FP05020301.jsp', 'CONFIRMED', '고시', 'POL-01',
   '사용자 사전 입력이며 최종 확인은 기관 심사가 우선', DATE '2026-09-07', 'policy-alignment'),
  ('FCT-258', '버팀목', '중복대출 금지',
   '성년 세대원 전원의 기금대출과 차주·배우자의 전세자금·주택담보대출 이용 여부 확인 필요',
   NULL, NULL, '청년·일반 버팀목', '주택도시기금',
   'https://nhuf.molit.go.kr/FP/FP05/FP0502/FP05020301.jsp', 'CONFIRMED', '고시', 'POL-01',
   'has_existing_jeonse_loan=false만으로 전체 범위를 통과 처리하지 않음', DATE '2026-09-07', 'policy-alignment'),
  ('FCT-259', '버팀목', '대상 주택', '전용면적 기준을 충족하는 주택과 주거용 오피스텔',
   NULL, NULL, '청년·일반 버팀목', '주택도시기금',
   'https://nhuf.molit.go.kr/FP/FP05/FP0502/FP05020301.jsp', 'CONFIRMED', '고시', 'POL-03',
   '주택 종류 이름 3종으로 제한하지 않고 비주거용 여부를 확인', DATE '2026-09-07', 'policy-alignment');

-- 잘못된 3종 화이트리스트 초안은 운영 판정에 들어가지 않도록 명시적으로 폐기한다.
UPDATE policy_rule
SET status = 'RETIRED'
WHERE version IN (4, 10)
  AND status = 'DRAFT'
  AND policy_id IN (SELECT id FROM policy WHERE code IN
      ('JEONSE-YOUTH-BEOTIMMOK', 'JEONSE-GENERAL-BEOTIMMOK', 'JEONSE-SEOUL-INTEREST-SUPPORT'));

-- 현재 ACTIVE 규칙을 복사하되 공식 범위를 기존 입력으로 확정할 수 없는 중복대출은
-- prohibited_loan_check로 바꾸고, 주거용 여부를 별도 조건으로 추가한다.
INSERT INTO policy_rule (policy_id, version, rule_json, status, effective_from, drafted_by)
SELECT pr.policy_id,
       11,
       jsonb_set(
           pr.rule_json,
           '{conditions}',
           (SELECT jsonb_agg(
                       CASE
                           WHEN condition->>'code' = 'HOUSEHOLD_HOMELESS'
                               THEN condition || '{"fact_code":"FCT-257"}'::jsonb
                           WHEN condition->>'code' = 'NO_DUPLICATE_LOAN'
                               THEN condition || '{"op":"prohibited_loan_check","fact_code":"FCT-258"}'::jsonb
                           ELSE condition
                       END
                       ORDER BY ordinality)
            FROM jsonb_array_elements(pr.rule_json->'conditions') WITH ORDINALITY AS t(condition, ordinality))
           || '[{"code":"RESIDENTIAL_USE","field":"is_non_residential","op":"eq","value":false,"fact_code":"FCT-259"}]'::jsonb),
       'ACTIVE',
       DATE '2026-09-07',
       'policy-alignment'
FROM policy_rule pr
WHERE pr.version = 3
  AND pr.status = 'ACTIVE'
  AND pr.policy_id IN (SELECT id FROM policy WHERE code IN
      ('JEONSE-YOUTH-BEOTIMMOK', 'JEONSE-GENERAL-BEOTIMMOK'));

UPDATE policy_rule
SET status = 'RETIRED'
WHERE version = 3
  AND status = 'ACTIVE'
  AND policy_id IN (SELECT id FROM policy WHERE code IN
      ('JEONSE-YOUTH-BEOTIMMOK', 'JEONSE-GENERAL-BEOTIMMOK'));
