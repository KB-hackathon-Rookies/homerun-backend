package com.homerun.domain.policy.dto.response;

import com.homerun.domain.policy.model.CollateralType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 담보 하나의 대출 한도(BR-19).
 *
 * @param limit 이 담보로 받을 수 있는 최대 대출액(원). 계산에 필요한 값을 모르면 null
 * @param note 어떤 상한이 적용됐는지 등 근거 한 줄
 */
@Schema(description = "담보별 대출 한도")
public record CollateralLoanLimitResponse(CollateralType collateral, Long limit, boolean provisional, String note) {}
