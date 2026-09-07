package com.homerun.domain.house.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.house.dto.request.HouseAnalysisRequest;
import com.homerun.domain.house.dto.response.HouseAnalysisResponse;
import com.homerun.domain.plan.type.HouseType;
import com.homerun.global.external.building.BuildingLedgerResponse;
import com.homerun.global.external.building.BuildingLedgerService;
import com.homerun.global.external.building.BuildingRegisterResponse;
import com.homerun.global.external.realestate.HousingType;
import com.homerun.global.external.realestate.RealEstateTransactionClient;
import com.homerun.global.external.realestate.RealEstateTransactionResponse;
import com.homerun.global.external.realestate.RealEstateTransactionUpstreamException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HouseAnalysisServiceTest {

    private final BuildingLedgerService buildingLedgerService = mock(BuildingLedgerService.class);
    private final RealEstateTransactionClient transactionClient = mock(RealEstateTransactionClient.class);
    private final HouseAnalysisService service = new HouseAnalysisService(buildingLedgerService, transactionClient);

    @Test
    void connectsBuildingTypeToMatchingRentTransactions() {
        BuildingRegisterResponse titles = new BuildingRegisterResponse(
                "00",
                "OK",
                1,
                List.of(Map.of(
                        "bldNm", "홈런아파트",
                        "mainPurpsCdNm", "공동주택(아파트)",
                        "etcPurps", "아파트")));
        BuildingLedgerResponse ledger =
                new BuildingLedgerResponse(titles, new BuildingRegisterResponse("00", "OK", 0, List.of()));
        when(buildingLedgerService.findLedger(any())).thenReturn(ledger);

        when(transactionClient.findAllTransactions(eq(HousingType.APARTMENT), eq("11680"), eq("202608")))
                .thenReturn(response(List.of(
                        Map.of("jibun", "123-4", "aptNm", "홈런아파트", "deposit", "50,000"),
                        Map.of("jibun", "999", "aptNm", "다른아파트", "deposit", "40,000"))));

        HouseAnalysisResponse result = service.analyze(new HouseAnalysisRequest(
                "1168010100", false, "123", "4", "서울특별시 강남구 테헤란로 123", "서울특별시 강남구 역삼동 123-4", "홈런아파트", null, "202608"));

        assertThat(result.resolvedHouseType()).isEqualTo(HouseType.APARTMENT);
        assertThat(result.rents().matchedCount()).isEqualTo(1);
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void keepsBuildingResultWhenRentApiIsUnavailable() {
        BuildingRegisterResponse titles =
                new BuildingRegisterResponse("00", "OK", 1, List.of(Map.of("mainPurpsCdNm", "공동주택(아파트)")));
        when(buildingLedgerService.findLedger(any()))
                .thenReturn(new BuildingLedgerResponse(titles, new BuildingRegisterResponse("00", "OK", 0, List.of())));
        when(transactionClient.findAllTransactions(eq(HousingType.APARTMENT), eq("11680"), eq("202608")))
                .thenThrow(new RealEstateTransactionUpstreamException("등록되지 않은 서비스키"));

        HouseAnalysisResponse result = service.analyze(new HouseAnalysisRequest(
                "1168010100", false, "123", "4", "서울특별시 강남구 테헤란로 123", "서울특별시 강남구 역삼동 123-4", "홈런아파트", null, "202608"));

        assertThat(result.rents().available()).isFalse();
        assertThat(result.buildingLedger().titles().totalCount()).isEqualTo(1);
        assertThat(result.warnings()).singleElement().asString().contains("등록되지 않은 서비스키");
    }

    @Test
    void acceptsManualHouseType_andSkipsRentQuery_forTypesWithoutTransactionApi() {
        // FR-P1-09. 사용자가 유형을 직접 주면 자동판별보다 그쪽이 이긴다.
        // 단독주택은 유형별 실거래 API 가 없어 조회를 건너뛴다.
        BuildingLedgerResponse ledger = new BuildingLedgerResponse(
                new BuildingRegisterResponse("00", "OK", 1, List.of(Map.of("mainPurpsCdNm", "단독주택"))),
                new BuildingRegisterResponse("00", "OK", 0, List.of()));
        when(buildingLedgerService.findLedger(any())).thenReturn(ledger);

        HouseAnalysisResponse result = service.analyze(new HouseAnalysisRequest(
                "1168010100",
                false,
                "123",
                "4",
                "서울특별시 관악구 신림로 1",
                "서울특별시 관악구 신림동 123-4",
                "홈런빌",
                HouseType.DETACHED,
                "202608"));

        assertThat(result.resolvedHouseType()).isEqualTo(HouseType.DETACHED);
        assertThat(result.rents().available()).isFalse();
        assertThat(result.rents().matchedCount()).isZero();
        assertThat(result.warnings()).anySatisfy(w -> assertThat(w).contains("직접 입력"));
        // 실거래 API 를 부르지 않는다 — 없는 유형별 엔드포인트를 호출하지 않는다.
        org.mockito.Mockito.verifyNoInteractions(transactionClient);
    }

    @Test
    void resolvesDetachedHouseFromLedger_withoutManualType() {
        // 대장이 "단독주택" 이라고 말해 주는데 굳이 사용자에게 다시 묻지 않는다.
        BuildingLedgerResponse ledger = new BuildingLedgerResponse(
                new BuildingRegisterResponse("00", "OK", 1, List.of(Map.of("mainPurpsCdNm", "단독주택"))),
                new BuildingRegisterResponse("00", "OK", 0, List.of()));
        when(buildingLedgerService.findLedger(any())).thenReturn(ledger);

        HouseAnalysisResponse result = service.analyze(request(null));

        assertThat(result.resolvedHouseType()).isEqualTo(HouseType.DETACHED);
        assertThat(result.rents().available()).isFalse();
        org.mockito.Mockito.verifyNoInteractions(transactionClient);
    }

    @Test
    void readsMultiFamilyFromEtcPurps_beforeDetached() {
        // 다가구는 주용도가 "단독주택" 이고 기타용도에만 "다가구주택" 이 적히는 일이 많다.
        BuildingLedgerResponse ledger = new BuildingLedgerResponse(
                new BuildingRegisterResponse(
                        "00", "OK", 1, List.of(Map.of("mainPurpsCdNm", "단독주택", "etcPurps", "다가구주택"))),
                new BuildingRegisterResponse("00", "OK", 0, List.of()));
        when(buildingLedgerService.findLedger(any())).thenReturn(ledger);

        assertThat(service.analyze(request(null)).resolvedHouseType()).isEqualTo(HouseType.MULTI_FAMILY);
    }

    @Test
    void fallsBackToOther_whenLedgerIsEmpty() {
        // 대장이 없는 필지가 흔하다. 여기서 막으면 매물을 아예 등록할 수 없다 — 유형은
        // 바로 다음 단계에서 사용자가 고르므로, 경고만 남기고 등록은 통과시킨다.
        BuildingLedgerResponse ledger = new BuildingLedgerResponse(
                new BuildingRegisterResponse("00", "OK", 0, List.of()),
                new BuildingRegisterResponse("00", "OK", 0, List.of()));
        when(buildingLedgerService.findLedger(any())).thenReturn(ledger);

        HouseAnalysisResponse result = service.analyze(request(null));

        assertThat(result.resolvedHouseType()).isEqualTo(HouseType.OTHER);
        assertThat(result.warnings()).anySatisfy(w -> assertThat(w).contains("건축물대장을 찾지 못해"));
        org.mockito.Mockito.verifyNoInteractions(transactionClient);
    }

    @Test
    void fallsBackToOther_whenPurposeIsNotResidential() {
        BuildingLedgerResponse ledger = new BuildingLedgerResponse(
                new BuildingRegisterResponse("00", "OK", 1, List.of(Map.of("mainPurpsCdNm", "제2종근린생활시설"))),
                new BuildingRegisterResponse("00", "OK", 0, List.of()));
        when(buildingLedgerService.findLedger(any())).thenReturn(ledger);

        HouseAnalysisResponse result = service.analyze(request(null));

        assertThat(result.resolvedHouseType()).isEqualTo(HouseType.OTHER);
        assertThat(result.warnings()).anySatisfy(w -> assertThat(w).contains("판별하지 못했습니다"));
    }

    private HouseAnalysisRequest request(HouseType houseType) {
        return new HouseAnalysisRequest(
                "1168010100", false, "123", "4", "서울특별시 관악구 신림로 1", "서울특별시 관악구 신림동 123-4", "홈런빌", houseType, "202608");
    }

    private RealEstateTransactionResponse response(List<Map<String, String>> items) {
        return new RealEstateTransactionResponse(
                HousingType.APARTMENT, "11680", "202608", 1, 1000, items.size(), "000", "OK", items);
    }
}
