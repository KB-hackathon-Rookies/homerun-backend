package com.homerun.domain.rent;

import com.homerun.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 월세 트랙 2루 계산(POL-04).
 *
 * <p>자격 판정 자체는 규칙 엔진(POL-01)이 하고, 여기서는 판정 결과를 받아 금액을 계산한다.
 */
@RestController
@RequestMapping("/api/v1/policies/rent")
@Tag(name = "월세 정책 계산", description = "지원금 상호배타·세액공제·실질 주거비")
public class RentPolicyController {

    private final RentSupportResolver supportResolver;
    private final RentTaxCreditCalculator taxCreditCalculator;
    private final EffectiveRentCalculator effectiveRentCalculator;
    private final RentLoanCalculator loanCalculator;
    private final HousingBenefitEvaluator housingBenefitEvaluator;

    public RentPolicyController(
            RentSupportResolver supportResolver,
            RentTaxCreditCalculator taxCreditCalculator,
            EffectiveRentCalculator effectiveRentCalculator,
            RentLoanCalculator loanCalculator,
            HousingBenefitEvaluator housingBenefitEvaluator) {
        this.supportResolver = supportResolver;
        this.taxCreditCalculator = taxCreditCalculator;
        this.effectiveRentCalculator = effectiveRentCalculator;
        this.loanCalculator = loanCalculator;
        this.housingBenefitEvaluator = housingBenefitEvaluator;
    }

    @PostMapping("/supports")
    @Operation(
            summary = "지원금 상호배타 판정",
            description = "동시에 받을 수 없는 조합을 걸러내고 총액이 가장 큰 조합을 추천합니다. 지원금은 단순히 더하면 실제로는 받지 못할 금액이 섞입니다.")
    public ApiResponse<RentSupportResolveResponse> resolveSupports(
            @Valid @RequestBody RentSupportResolveRequest request) {
        return ApiResponse.success(new RentSupportResolveResponse(supportResolver.resolve(request.eligibleSupports())));
    }

    @PostMapping("/tax-credit")
    @Operation(
            summary = "월세 세액공제 계산",
            description = "지원금을 먼저 차감한 뒤 남은 본인 부담분에만 공제율을 적용합니다. 대상이 아니어도 200 으로 사유와 함께 응답합니다.")
    public ApiResponse<TaxCreditResult> calculateTaxCredit(@Valid @RequestBody TaxCreditRequest request) {
        return ApiResponse.success(taxCreditCalculator.calculate(request));
    }

    @PostMapping("/effective-cost")
    @Operation(summary = "실질 월 주거비 계산", description = "지원금과 세액공제를 반영한 실질 월세를 계산합니다. 전세와 월세를 같은 기준으로 비교할 때 씁니다.")
    public ApiResponse<EffectiveRentResult> calculateEffectiveCost(@Valid @RequestBody EffectiveRentRequest request) {
        return ApiResponse.success(effectiveRentCalculator.calculate(request));
    }

    @PostMapping("/loan-comparison")
    @Operation(
            summary = "월세대출 총비용 비교",
            description = "보증부월세와 주거안정을 총 이자가 적은 순으로 비교합니다. 둘 다 만기일시상환이라 월 부담은 이자뿐이고, 표면 금리만 보면 대출 구조 차이를 놓칩니다.")
    public ApiResponse<List<RentLoanQuote>> compareLoans(@Valid @RequestBody RentLoanRequest request) {
        return ApiResponse.success(loanCalculator.compare(request));
    }

    @PostMapping("/housing-benefit")
    @Operation(summary = "주거급여 판정", description = "주거급여와 청년 분리지급 대상 여부를 판정합니다. 기준이 없는 가구원 수는 불가가 아니라 추가확인으로 넘깁니다.")
    public ApiResponse<HousingBenefitResult> evaluateHousingBenefit(@Valid @RequestBody HousingBenefitRequest request) {
        return ApiResponse.success(housingBenefitEvaluator.evaluate(request));
    }
}
