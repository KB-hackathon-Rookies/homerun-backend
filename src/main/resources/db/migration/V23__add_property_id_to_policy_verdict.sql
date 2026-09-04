-- #89. 반환보증(HUG/HF/SGI) 판정은 plan_input 이 아니라 매물(공시가격 등) 기반이다.
-- policy_verdict 가 지금 plan_id + policy_id 로만 연결돼 있어서 "어느 매물 기준으로
-- 판정했는지"를 남길 곳이 없었다. nullable 로 추가한다 — plan_input 기반 정책(청년/일반
-- 버팀목, 서울시 이자지원)은 계속 NULL 이다.
--
-- UNIQUE 제약(uq_policy_verdict)은 그대로 (plan_id, policy_id, rule_id) 다. 넓히지 않은
-- 이유는 #85/#87 에서 이미 검증한 plan_input 기반 정책의 재판정 upsert 를 건드릴 위험이
-- 있어서다 — NULL 은 UNIQUE 제약에서 서로 다른 값으로 취급되므로, property_id 를 제약에
-- 넣으면 plan_input 기반 정책(property_id 항상 NULL)의 재판정이 덮어쓰기가 아니라 새 행
-- 추가로 바뀐다. 그 결과 매물을 여러 개 비교할 때 반환보증 판정은 가장 최근에 평가한
-- 매물 하나만 남는다 — 이력이 필요해지면 별도 이슈에서 제약을 다시 설계한다.

ALTER TABLE policy_verdict
    ADD COLUMN property_id BIGINT REFERENCES property (id);

CREATE INDEX ix_policy_verdict_property ON policy_verdict (property_id);
