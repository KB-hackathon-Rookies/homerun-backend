package com.homerun.domain.policy.controller;

import com.homerun.domain.policy.dto.response.BankLoanRateListResponse;
import com.homerun.domain.policy.service.BankLoanRateService;
import com.homerun.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 일반 전세대출 금리 비교(POL-03-07). 사용자별 상태가 없는 참조 데이터라 계획에 안 매단다 —
 * {@link GuaranteeAgencyController} 와 같은 취급이다. */
@RestController
@RequestMapping("/api/v1/bank-loan-rates")
@Tag(name = "일반 전세대출 금리 비교", description = "은행별 공시 평균금리 비교(POL-03-07)")
public class BankLoanRateController {

    private final BankLoanRateService service;

    public BankLoanRateController(BankLoanRateService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "은행별 공시 평균금리 비교",
            description = "정책대출이 안 될 때 쓰는 은행 자체 대출의 공시 평균금리를 낮은 순으로 비교한다."
                    + " 광고금리가 아니라 은행연합회 공시 신규취급 평균이며, 대출 승인이나 한도 안내가 아니다.")
    public ApiResponse<BankLoanRateListResponse> compare() {
        return ApiResponse.success(service.compare());
    }
}
