package com.homerun.global.external.building;

import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * DATA_GO_KR_SERVICE_KEY 가 없을 때 쓰는 목 건축물대장.
 *
 * <p>주용도를 공동주택·아파트로 둔다. 이 문자열로 주택유형(HouseType)과 다가구·근생 여부가 갈리므로
 * 값을 바꾸면 매물 판정 결과도 같이 바뀐다.
 */
@Component
@Primary
@ConditionalOnExpression("'${external-api.building-register.service-key:}'.isBlank()")
public class MockBuildingRegisterClient extends BuildingRegisterClient {

    public MockBuildingRegisterClient(BuildingRegisterProperties properties, ObjectMapper objectMapper) {
        super(properties, objectMapper);
    }

    @Override
    public BuildingRegisterResponse findTitles(BuildingLotQuery query) {
        return new BuildingRegisterResponse(
                "00",
                "MOCK NORMAL SERVICE",
                1,
                List.of(Map.of(
                        "bldNm", "홈런아파트",
                        "mainPurpsCdNm", "공동주택",
                        "etcPurps", "아파트",
                        "platPlc", "서울특별시 강남구 역삼동 " + query.mainLotNumber() + "-" + query.subLotNumber(),
                        "totArea", "12500.5",
                        "useAprDay", "20150320",
                        "grndFlrCnt", "15")));
    }

    @Override
    public BuildingRegisterResponse findHousingPrices(BuildingLotQuery query) {
        return new BuildingRegisterResponse(
                "00",
                "MOCK NORMAL SERVICE",
                1,
                List.of(Map.of(
                        "bldNm", "홈런아파트",
                        "hsprc", "320000000",
                        "stdDay", "2026-01-01")));
    }
}
