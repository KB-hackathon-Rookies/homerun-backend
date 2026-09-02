package com.homerun.domain.rent.controller;

import com.homerun.domain.rent.dto.request.EffectiveRentRequest;
import com.homerun.domain.rent.dto.request.RentSupportResolveRequest;
import com.homerun.domain.rent.dto.request.TaxCreditRequest;
import com.homerun.domain.rent.dto.response.EffectiveRentResult;
import com.homerun.domain.rent.dto.response.RentSupportResolveResponse;
import com.homerun.domain.rent.dto.response.TaxCreditResult;
import com.homerun.domain.rent.service.EffectiveRentCalculator;
import com.homerun.domain.rent.service.RentSupportResolver;
import com.homerun.domain.rent.service.RentTaxCreditCalculator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

    public RentPolicyController(
            RentSupportResolver supportResolver,
            RentTaxCreditCalculator taxCreditCalculator,
            EffectiveRentCalculator effectiveRentCalculator) {
        this.supportResolver = supportResolver;
        this.taxCreditCalculator = taxCreditCalculator;
        this.effectiveRentCalculator = effectiveRentCalculator;
    }

    @PostMapping("/supports")
    @Operation(
            summary = "지원금 상호배타 판정",
            description = "동시에 받을 수 없는 조합을 걸러내고 총액이 가장 큰 조합을 추천합니다. 지원금은 단순히 더하면 실제로는 받지 못할 금액이 섞입니다.")
    public RentSupportResolveResponse resolveSupports(@Valid @RequestBody RentSupportResolveRequest request) {
        return new RentSupportResolveResponse(supportResolver.resolve(request.eligibleSupports()));
    }

    @PostMapping("/tax-credit")
    @Operation(
            summary = "월세 세액공제 계산",
            description = "지원금을 먼저 차감한 뒤 남은 본인 부담분에만 공제율을 적용합니다. 대상이 아니어도 200 으로 사유와 함께 응답합니다.")
    public TaxCreditResult calculateTaxCredit(@Valid @RequestBody TaxCreditRequest request) {
        return taxCreditCalculator.calculate(request);
    }

    @PostMapping("/effective-cost")
    @Operation(summary = "실질 월 주거비 계산", description = "지원금과 세액공제를 반영한 실질 월세를 계산합니다. 전세와 월세를 같은 기준으로 비교할 때 씁니다.")
    public EffectiveRentResult calculateEffectiveCost(@Valid @RequestBody EffectiveRentRequest request) {
        return effectiveRentCalculator.calculate(request);
    }
}
