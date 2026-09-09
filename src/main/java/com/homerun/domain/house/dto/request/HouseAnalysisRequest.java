package com.homerun.domain.house.dto.request;

import com.homerun.domain.plan.type.HouseType;
import com.homerun.global.external.building.BuildingLotQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "주소 검색에서 사용자가 선택한 집과 실거래가 조회 기준")
public record HouseAnalysisRequest(
        @Schema(description = "10자리 법정동 코드", example = "1168010100") @NotBlank @Pattern(regexp = "\\d{10}")
        String legalDistrictCode,

        @Schema(description = "산 소재지 여부", example = "false") boolean mountain,

        @Schema(description = "지번 본번", example = "123") @NotBlank @Pattern(regexp = "\\d{1,4}")
        String mainLotNumber,

        @Schema(description = "지번 부번", example = "4") @Pattern(regexp = "\\d{1,4}")
        String subLotNumber,

        @Schema(description = "도로명주소", example = "서울특별시 강남구 테헤란로 123")
        String roadAddress,

        @Schema(description = "지번주소", example = "서울특별시 강남구 역삼동 123-4")
        String jibunAddress,

        @Schema(description = "주소 검색 결과의 건물명", example = "홈런아파트")
        String buildingName,

        @Schema(description = "주택 유형. 생략하면 건축물대장에서 판별합니다. 자동판별 실패(단독·다가구)면 직접 입력합니다.", nullable = true)
        HouseType houseType,

        @Schema(description = "실거래 계약연월", example = "202608") @NotBlank @Pattern(regexp = "\\d{6}")
        String dealYearMonth) {

    /**
     * 건축물대장 조회 파라미터. 주소 검색이 준 값을 그대로 옮긴다.
     *
     * <p>부번은 비워서 보낼 수 있어 여기서 {@code "0"} 으로 맞춘다. 이 정규화가 여러 곳에 흩어지면
     * 한쪽만 고쳐져 같은 집을 다른 지번으로 조회하게 된다.
     */
    public BuildingLotQuery toLotQuery() {
        return new BuildingLotQuery(legalDistrictCode, mountain, mainLotNumber, normalizedSubLotNumber());
    }

    public String normalizedSubLotNumber() {
        return subLotNumber == null || subLotNumber.isBlank() ? "0" : subLotNumber;
    }
}
