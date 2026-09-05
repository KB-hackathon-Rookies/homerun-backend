-- 청년미래적금을 범위에서 뺀다(2026-09-06 결정, #175).
--
-- V31 이 검수를 마쳤다고 보고 ACTIVE 로 올렸던 규칙을 DRAFT 로 되돌린다. 판정 API 와 서비스는
-- 이 마이그레이션과 함께 지운다.
--
-- DISCONTINUED 로 두지 않는다. 중기청(JEONSE-JUNGGICHEONG-DISCONTINUED, FCT-135)에 그 상태를
-- 쓴 선례가 있지만 그건 실제로 신규 취급이 중단된 상품이다. 청년미래적금은 우리가 범위에서
-- 빼는 것뿐이라 폐지로 표시하면 "이 상품은 이제 없다"는 거짓 안내가 된다. ADM-07 의 폐지 상태는
-- 실제로 폐지된 상품에만 쓴다.
--
-- policy 행과 팩트(FCT-179~183 매칭 조건, FCT-083·084 중도해지 손실)는 지우지 않는다.
-- 레지스트리는 판정에 쓰지 않아도 참조 자료로 값이 있고, 되살릴 때는 V31 같은 승격
-- 마이그레이션 하나만 더하면 된다.
--
-- 이 강등으로 재도전 큐(ALT-01-04)가 훑을 대상이 없어진다 -- ACTIVE 정책 중 나이 하한이 아직
-- 안 찬 것이 이 정책 하나뿐이었다. 기능은 그대로 두고, 통합테스트는 V36 이 심은 분리지급
-- 규칙(FCT-209 하한 19세)으로 옮겨 검증한다.

UPDATE policy_rule
SET status = 'DRAFT'
WHERE policy_id = (SELECT id FROM policy WHERE code = 'YOUTH-FUTURE-SAVINGS')
  AND status = 'ACTIVE';
