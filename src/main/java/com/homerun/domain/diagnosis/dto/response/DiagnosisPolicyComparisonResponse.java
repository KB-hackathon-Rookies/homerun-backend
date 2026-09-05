package com.homerun.domain.diagnosis.dto.response;

import java.time.LocalDate;

public record DiagnosisPolicyComparisonResponse(
        long requiredCashBeforePolicy,
        long requiredCashAfterPolicy,
        long reducedInitialCash,
        long expectedLoanAmount,
        long expectedMonthlyInterest,
        long monthlyHousingCostBeforePolicy,
        long monthlyHousingCostAfterPolicy,
        long monthlyDisposableBeforePolicy,
        long monthlyDisposableAfterPolicy,
        LocalDate possibleDateBeforePolicy,
        LocalDate possibleDateAfterPolicy) {}
