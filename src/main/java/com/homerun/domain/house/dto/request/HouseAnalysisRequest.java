package com.homerun.domain.house.dto.request;

import com.homerun.global.external.realestate.HousingType;
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

        @Schema(description = "주택 유형. 생략하면 건축물대장에서 판별합니다.", nullable = true)
        HousingType housingType,

        @Schema(description = "실거래 계약연월", example = "202608") @NotBlank @Pattern(regexp = "\\d{6}")
        String dealYearMonth) {}
