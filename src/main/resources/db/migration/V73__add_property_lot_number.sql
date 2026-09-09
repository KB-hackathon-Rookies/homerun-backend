-- 건축물대장 조회 파라미터를 매물에 남긴다.
--
-- 주소 검색이 법정동코드·본번·부번·산 여부를 함께 주는데 지금까지 법정동코드만 저장했다.
-- jibun 은 "서울특별시 강남구 역삼동 648-23 여삼빌딩" 같은 표시용 문자열이라 조회에 쓸 수 없어,
-- 등록 이후에는 대장을 다시 볼 방법이 없었다.
--
-- 기존 행은 채우지 않는다. jibun 에서 뽑아내는 것은 추정이라, 주소 검색이 준 원본과 섞이면
-- 나중에 엉뚱한 대장을 조회했을 때 원인을 가릴 수 없다.
ALTER TABLE property
    ADD COLUMN main_lot_number VARCHAR(4),
    ADD COLUMN sub_lot_number  VARCHAR(4),
    ADD COLUMN mountain        BOOLEAN;

COMMENT ON COLUMN property.main_lot_number IS '지번 본번. 주소 검색 결과 그대로';
COMMENT ON COLUMN property.sub_lot_number IS '지번 부번. 없으면 0';
COMMENT ON COLUMN property.mountain IS '산 소재지 여부. 같은 번지라도 산과 대지는 다른 땅이다';
