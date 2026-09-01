package com.homerun.global.external.realestate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/real-estate/rent-transactions")
@Tag(name = "전월세 실거래가", description = "국토교통부 주택 유형별 전월세 실거래가 API")
public class RealEstateTransactionController {

    private final RealEstateTransactionClient client;

    public RealEstateTransactionController(RealEstateTransactionClient client) {
        this.client = client;
    }

    @GetMapping
    @Operation(summary = "지역·월별 전월세 실거래가 조회", description = "법정동 코드 앞 5자리와 계약연월로 전월세 실거래가를 조회합니다.")
    public RealEstateTransactionResponse findTransactions(
            @RequestParam HousingType housingType,
            @Parameter(example = "11680")
                    @RequestParam
                    @Pattern(regexp = "\\d{5}", message = "legalDistrictCode는 5자리 숫자여야 합니다.")
                    String legalDistrictCode,
            @Parameter(example = "202608")
                    @RequestParam
                    @Pattern(regexp = "\\d{6}", message = "dealYearMonth는 YYYYMM 형식이어야 합니다.")
                    String dealYearMonth,
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int numOfRows) {
        return client.findTransactions(
                new RealEstateTransactionRequest(housingType, legalDistrictCode, dealYearMonth, pageNo, numOfRows));
    }
}
