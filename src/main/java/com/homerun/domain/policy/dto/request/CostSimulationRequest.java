package com.homerun.domain.policy.dto.request;

import com.homerun.domain.policy.model.CollateralType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * 부대비용·총비용 시뮬레이션 입력(BR-08a·BR-21). 보증금은 계획에서 읽고, 상품 카드에서 정해지는
 * 대출금·금리·담보는 요청으로 받는다.
 *
 * @param loanAmount 대출금(원)
 * @param annualRatePercent 예상 연 금리(퍼센트, 2.2% → 2.2)
 * @param collateral 담보 방식. 생략하면 보증료율 중간값으로 추정한다
 * @param apartment 아파트 여부. SGI 보증료율이 아파트/그 외로 갈린다
 * @param movingCost 이사비. 생략하면 기본값(FCT-237)
 */
@Schema(description = "부대비용·총비용 시뮬레이션 입력(BR-08a·BR-21)")
public record CostSimulationRequest(
        @NotNull @PositiveOrZero Long loanAmount,
        @NotNull @PositiveOrZero BigDecimal annualRatePercent,
        CollateralType collateral,
        Boolean apartment,
        @PositiveOrZero Long movingCost) {}
