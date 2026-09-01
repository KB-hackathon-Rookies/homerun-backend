package com.homerun.house;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

        assertThat(result.resolvedHousingType()).isEqualTo(HousingType.APARTMENT);
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

    private RealEstateTransactionResponse response(List<Map<String, String>> items) {
        return new RealEstateTransactionResponse(
                HousingType.APARTMENT, "11680", "202608", 1, 1000, items.size(), "000", "OK", items);
    }
}
