-- 주택유형 어휘 통일(#193). V47(#190)이 심은 HOUSE_TYPE 화이트리스트가 실거래 API 어휘
-- (ROW_HOUSE)로 돼 있다. 매물 저장이 이제 도메인 어휘(VILLA)를 쓰므로 화이트리스트도 맞춘다.
--
-- V47 규칙은 DRAFT 라 아직 판정에 안 돈다. 머지된 파일을 고치는 대신(체크섬) 새 DRAFT
-- 를 심어 version 4 를 대체한다. 승격은 검수 후 별도 마이그레이션에서 한다.
--
-- version 은 10 으로 크게 띄운다. JeonsePolicyVerdictIntegrationTest 가 테스트 트랜잭션
-- 안에서 version 5~9 를 직접 심어 검증하는데, 시드가 그 대역을 쓰면 충돌한다. 실 시드는
-- 버전 4 까지뿐이라 10 은 한참 위다.
--
-- version 4 의 rule_json 을 복사해 HOUSE_TYPE 조건의 value 만 도메인 어휘로 바꾼다. 나머지
-- 조건은 그대로 둔다 — 다시 적으면 어긋난다.

INSERT INTO policy_rule (policy_id, version, rule_json, status, effective_from, drafted_by)
SELECT pr.policy_id, 10,
       jsonb_set(
           pr.rule_json,
           '{conditions}',
           (SELECT jsonb_agg(
                       CASE WHEN condition->>'code' = 'HOUSE_TYPE'
                            THEN jsonb_set(condition, '{value}', '["APARTMENT","OFFICETEL","VILLA"]'::jsonb)
                            ELSE condition END
                       ORDER BY ordinality)
            FROM jsonb_array_elements(pr.rule_json->'conditions') WITH ORDINALITY AS t(condition, ordinality))),
       'DRAFT', DATE '2026-09-06', 'seed-#193'
FROM policy_rule pr
WHERE pr.version = 4
  AND pr.status = 'DRAFT'
  AND pr.policy_id IN (SELECT id FROM policy WHERE code IN
      ('JEONSE-YOUTH-BEOTIMMOK', 'JEONSE-GENERAL-BEOTIMMOK', 'JEONSE-SEOUL-INTEREST-SUPPORT'))
  AND pr.rule_json->'conditions' @> '[{"code": "HOUSE_TYPE"}]';
