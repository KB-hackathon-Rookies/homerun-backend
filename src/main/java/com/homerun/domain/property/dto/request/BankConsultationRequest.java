package com.homerun.domain.property.dto.request;

import com.homerun.domain.property.type.CollateralMethod;
import com.homerun.domain.property.type.ConsultationResultStatus;
import com.homerun.domain.property.type.ConsultedLoanProduct;
import com.homerun.domain.property.type.RejectionCategory;
import com.homerun.domain.property.type.RejectionStage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "매물별 은행 사전상담 결과")
public record BankConsultationRequest(
        @NotBlank String bankName,
        String branchName,
        Long policyId,
        Long guaranteeAgencyId,
        @NotNull ConsultationResultStatus resultStatus,
        @NotNull ConsultedLoanProduct loanProduct,
        @NotNull CollateralMethod collateralMethod,
        @PositiveOrZero Long approvedLimit,

        @DecimalMin(value = "0.0", message = "상담 금리는 0 이상이어야 합니다")
        BigDecimal quotedRate,

        @NotNull LocalDate consultedAt,
        String memo,

        @Schema(description = "거절일 때만: 어디서 막혔는가(BR-24)") RejectionStage rejectionStage,

        @Schema(description = "거절일 때만: 무엇 때문에 막혔는가(BR-24)") RejectionCategory rejectionCategory,

        @Schema(description = "거절 메모") String rejectionNote) {

    /** 거절 분류 없이 상담 결과만 저장하는 경우(POSSIBLE 등). */
    public BankConsultationRequest(
            String bankName,
            String branchName,
            Long policyId,
            Long guaranteeAgencyId,
            ConsultationResultStatus resultStatus,
            ConsultedLoanProduct loanProduct,
            CollateralMethod collateralMethod,
            Long approvedLimit,
            BigDecimal quotedRate,
            LocalDate consultedAt,
            String memo) {
        this(
                bankName,
                branchName,
                policyId,
                guaranteeAgencyId,
                resultStatus,
                loanProduct,
                collateralMethod,
                approvedLimit,
                quotedRate,
                consultedAt,
                memo,
                null,
                null,
                null);
    }
}
