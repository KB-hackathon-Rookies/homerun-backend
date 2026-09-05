-- 요구사항 명세서 DR-10. 매물 하나 × 상품 하나의 판정을 담는다(#181).
--
-- policy_verdict 로는 안 되는 이유가 분명하다. 그 테이블은 UNIQUE (plan_id, policy_id, rule_id)
-- 라서 같은 계획에서 매물을 여러 개 판정하면 마지막 하나만 남는다. V23 이 property_id 를
-- 더하면서 이 한계를 주석에 적어 뒀다 -- "매물을 여러 개 비교할 때 가장 최근에 평가한 매물
-- 하나만 남는다. 이력이 필요해지면 별도 이슈에서 제약을 다시 설계한다."
--
-- 명세서는 매물을 최대 5개까지 나란히 비교하라고 한다(FR-P1-02·FR-P1-03). 그래서 제약을
-- 넓히는 대신 매물 축을 가진 자리를 따로 만든다. policy_verdict 는 계획 단위 판정(1루 사람
-- 조건)으로 그대로 두고, 여기는 매물 단위 판정(2루 집 조건)만 담는다.
--
-- fail_codes 는 JSONB 배열이다(plan_input.unknown_fields 와 같은 방식). BR-12 가 "탈락해도 전체 조건을 모두 평가해 코드를 배열로 수집한다"고
-- 했다 -- 첫 번째 실패에서 멈추면 사용자가 무엇을 몇 개 고쳐야 하는지 알 수 없다.

CREATE TABLE property_policy_verdict (
    id           BIGSERIAL   PRIMARY KEY,
    property_id  BIGINT      NOT NULL REFERENCES property (id),
    policy_id    BIGINT      NOT NULL REFERENCES policy (id),
    rule_id      BIGINT      REFERENCES policy_rule (id),
    status       VARCHAR(20) NOT NULL,
    fail_codes   JSONB       NOT NULL DEFAULT '[]',
    fail_step    SMALLINT,
    evaluated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 같은 매물·상품을 다시 판정하면 덮어쓴다. 이력이 아니라 지금 상태를 담는 자리다.
ALTER TABLE property_policy_verdict
    ADD CONSTRAINT uq_property_policy_verdict UNIQUE (property_id, policy_id);

-- policy_verdict.verdict 와 같은 3단계를 쓴다. 판정 축이 같아야 화면에서 섞어 쓸 수 있다.
ALTER TABLE property_policy_verdict
    ADD CONSTRAINT ck_property_policy_verdict_status
    CHECK (status IN ('PASS', 'NEED_INFO', 'FAIL'));

-- 명세서 2-1 의 STEP 1~4. 어느 단계에서 걸렸는지 알아야 "○○단계에서 △△ 조건 미충족"을
-- 한 줄로 보여줄 수 있다(FR-P1-04).
ALTER TABLE property_policy_verdict
    ADD CONSTRAINT ck_property_policy_verdict_fail_step
    CHECK (fail_step IS NULL OR fail_step BETWEEN 1 AND 4);

CREATE INDEX ix_property_policy_verdict_property ON property_policy_verdict (property_id);

COMMENT ON TABLE property_policy_verdict IS
    '매물 x 상품 판정(DR-10). 계획 단위 판정은 policy_verdict, 매물 단위 판정은 여기.';
COMMENT ON COLUMN property_policy_verdict.fail_codes IS
    '미충족 조건 코드 전부. 첫 실패에서 멈추지 않는다(BR-12).';
