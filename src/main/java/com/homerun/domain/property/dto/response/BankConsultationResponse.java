package com.homerun.domain.property.dto.response;

import com.homerun.domain.property.entity.BankConsultation;
import com.homerun.domain.property.type.CollateralMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record BankConsultationResponse(
        Long consultationId,
        Long propertyId,
        String bankName,
        String branchName,
        Long policyId,
        Long guaranteeAgencyId,
        CollateralMethod collateralMethod,
        Long approvedLimit,
        BigDecimal quotedRate,
        LocalDate consultedAt,
        String memo,
        Instant createdAt) {

    public static BankConsultationResponse from(BankConsultation consultation) {
        return new BankConsultationResponse(
                consultation.getId(),
                consultation.getPropertyId(),
                consultation.getBankName(),
                consultation.getBranchName(),
                consultation.getPolicyId(),
                consultation.getGuaranteeAgencyId(),
                consultation.getCollateralMethod(),
                consultation.getApprovedLimit(),
                consultation.getQuotedRate(),
                consultation.getConsultedAt(),
                consultation.getMemo(),
                consultation.getCreatedAt());
    }
}
