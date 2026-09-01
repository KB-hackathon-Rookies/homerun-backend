package com.homerun.global.external.address;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/addresses")
@Tag(name = "주소", description = "행정안전부 도로명주소 검색 API")
public class AddressController {

    private final JusoAddressClient client;

    public AddressController(JusoAddressClient client) {
        this.client = client;
    }

    @GetMapping("/search")
    @Operation(summary = "주소 검색", description = "도로명·건물명·지번으로 표준 주소와 건물 식별 코드를 검색합니다.")
    public AddressSearchResponse search(
            @Parameter(example = "테헤란로 123") @RequestParam @Size(min = 2, max = 100) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) int currentPage,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int countPerPage) {
        return client.search(keyword, currentPage, countPerPage);
    }
}
