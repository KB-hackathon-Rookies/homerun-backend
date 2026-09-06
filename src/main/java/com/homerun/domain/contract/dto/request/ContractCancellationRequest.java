package com.homerun.domain.contract.dto.request;

import com.homerun.domain.contract.type.PaymentStage;
import com.homerun.domain.property.type.RejectionCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 계약 해제 대응 입력(FR-P8).
 *
 * @param paymentStage 계약 진행 단계(해제 가능성을 가른다)
 * @param hasSpecialTerm 대출 미승인 시 계약 무효 특약이 있는가
 * @param rejectionCategory 거절 사유 분류(특약 적용 여부 판단). 모르면 null
 * @param daysToBalance 잔금일까지 남은 일수(특약 없을 때 대안 분기). 모르면 null
 */
@Schema(description = "계약 해제 대응 입력(FR-P8)")
public record ContractCancellationRequest(
        @NotNull PaymentStage paymentStage,
        boolean hasSpecialTerm,
        RejectionCategory rejectionCategory,
        Integer daysToBalance) {}
