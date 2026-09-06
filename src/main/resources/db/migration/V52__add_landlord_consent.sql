-- FR-P1-07. 매물 등록 시 임대인 전세대출 협조 여부를 저장한다(#205).
--
-- 전세대출은 임대인이 질권설정·채권양도 통지에 협조해야 진행된다. 계약을 다 한 뒤 거부당하면
-- 계약금을 날리므로 매물 단계에서 미리 확인한다. landlord_tax_unpaid(체납)와는 다른 개념이다.
--
-- REFUSED 여도 자동으로 RED 가 아니다(BR-10) -- 신호등에 넣지 않고 경고만 한다. 거부는 설득으로
-- 뒤집히기도 하고, 그 매물을 포기할지는 사용자가 정한다.

ALTER TABLE property ADD COLUMN landlord_consent VARCHAR(20);

ALTER TABLE property ADD CONSTRAINT ck_property_landlord_consent
    CHECK (landlord_consent IS NULL OR landlord_consent IN ('CONFIRMED', 'NOT_ASKED', 'REFUSED'));

COMMENT ON COLUMN property.landlord_consent IS
    '임대인 전세대출 협조 여부(FR-P1-07). CONFIRMED/NOT_ASKED/REFUSED. REFUSED 여도 RED 아님.';
