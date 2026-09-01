package com.homerun.global.external.address;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "도로명주소 검색 결과")
public record AddressSearchResult(
        @Schema(description = "전체 도로명주소", example = "서울특별시 강남구 테헤란로 123")
        String roadAddress,

        @Schema(description = "지번주소", example = "서울특별시 강남구 역삼동 123-4")
        String jibunAddress,

        @Schema(description = "우편번호", example = "06133") String zipCode,

        @Schema(description = "10자리 법정동 코드", example = "1168010100")
        String legalDistrictCode,

        @Schema(description = "도로명 코드") String roadNameCode,
        @Schema(description = "건물관리번호") String buildingManagementNumber,
        @Schema(description = "건물명", example = "홈런아파트") String buildingName,
        @Schema(description = "공동주택 여부") boolean apartmentBuilding,
        @Schema(description = "산 소재지 여부") boolean mountain,
        @Schema(description = "지번 본번", example = "123") String mainLotNumber,
        @Schema(description = "지번 부번", example = "4") String subLotNumber) {}
