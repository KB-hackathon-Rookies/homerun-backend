package com.homerun.domain.settlement.dto.request;

import com.homerun.domain.property.type.CollateralMethod;
import com.homerun.domain.property.type.ConsultedLoanProduct;
import com.homerun.domain.settlement.type.RepaymentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 실행 대출 등록 입력(DR-20). 계획당 1건이라 다시 등록하면 덮어쓴다.
 *
 * @param product 대출 상품
 * @param guarantee 보증 방식(없으면 null)
 * @param principal 대출 원금(원)
 * @param rate 연 금리(퍼센트, 2.2% → 2.2)
 * @param repaymentType 상환 방식
 * @param executedAt 실행일
 * @param maturityAt 만기일
 * @param preferentialUntil 우대금리 만료일
 * @param extensionCount 연장 횟수
 */
@Schema(description = "실행 대출 등록(DR-20)")
public record LoanAccountRequest(
        @NotNull ConsultedLoanProduct product,
        CollateralMethod guarantee,
        @NotNull @PositiveOrZero Long principal,
        @NotNull @PositiveOrZero BigDecimal rate,
        @NotNull RepaymentType repaymentType,
        @NotNull LocalDate executedAt,
        LocalDate maturityAt,
        LocalDate preferentialUntil,
        @PositiveOrZero Integer extensionCount) {}
