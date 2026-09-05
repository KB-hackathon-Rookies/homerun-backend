-- 요구사항 명세서 DR-08 이 매물에 요구하는 필드 중 여섯 개가 빠져 있다(#179).
--
-- 가장 급한 것은 전용면적이다. BR-09 의 상품별 1차 필터가 "주택유형·전용면적·보증금·지역"을
-- 보는데, 면적 컬럼이 없어 85㎡ 판정을 아예 할 수 없었다. 매물×상품 판정(DR-10)이 이 컬럼을
-- 기다리고 있다.
--
-- 출처(source) 컬럼을 함께 두는 이유는 FR-P1-09·FR-P1-10 때문이다. 건축물대장 자동판별이
-- 실패하면(단독·다가구 등 400) 사용자가 직접 입력하고, 실거래 매칭이 없으면 면적도 직접
-- 입력한다. 자동으로 얻은 값과 사람이 적은 값을 구분하지 않으면 어느 쪽을 믿을지 알 수 없다.

ALTER TABLE property ADD COLUMN jibun              VARCHAR(50);
ALTER TABLE property ADD COLUMN detail_address     VARCHAR(100);
ALTER TABLE property ADD COLUMN exclusive_area     NUMERIC(8,2);
ALTER TABLE property ADD COLUMN area_source        VARCHAR(10);
ALTER TABLE property ADD COLUMN house_type_source  VARCHAR(10);
ALTER TABLE property ADD COLUMN price_matched      BOOLEAN;

ALTER TABLE property ADD CONSTRAINT ck_property_area_source
    CHECK (area_source IS NULL OR area_source IN ('AUTO', 'MANUAL'));
ALTER TABLE property ADD CONSTRAINT ck_property_house_type_source
    CHECK (house_type_source IS NULL OR house_type_source IN ('AUTO', 'MANUAL'));

COMMENT ON COLUMN property.exclusive_area IS
    '전용면적(㎡). BR-09 의 85㎡ 판정 입력. 실거래 excluUseAr 또는 사용자 직접 입력.';
COMMENT ON COLUMN property.area_source IS
    'AUTO=실거래 매칭에서 가져옴, MANUAL=사용자가 직접 입력. 값이 없으면 아직 확보 못 한 것.';
COMMENT ON COLUMN property.house_type_source IS
    'AUTO=건축물대장 자동판별, MANUAL=자동판별 실패 후 직접 입력(FR-P1-09).';
COMMENT ON COLUMN property.price_matched IS
    '실거래 목록에서 이 집과 일치하는 거래를 찾았는가(rents.matchedCount > 0).';
COMMENT ON COLUMN property.detail_address IS
    '동·호수. 집합건물은 여기까지 정확해야 등기부가 맞다(FR-P2-02).';
