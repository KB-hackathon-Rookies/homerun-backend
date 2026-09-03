-- 전세 계획에서 비교할 매물 후보와 최종 선택 상태를 저장한다.

ALTER TABLE property
    ADD COLUMN legal_district_code VARCHAR(10),
    ADD COLUMN building_name VARCHAR(200),
    ADD COLUMN deposit BIGINT,
    ADD COLUMN is_multi_household BOOLEAN,
    ADD COLUMN landlord_tax_unpaid BOOLEAN,
    ADD COLUMN is_selected BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN analyzed_at TIMESTAMPTZ;

ALTER TABLE property
    ADD CONSTRAINT ck_property_deposit CHECK (deposit IS NULL OR deposit >= 0);

CREATE UNIQUE INDEX uq_property_selected_per_plan
    ON property (plan_id) WHERE is_selected = true;
