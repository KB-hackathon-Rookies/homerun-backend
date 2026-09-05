package com.homerun.domain.diagnosis.dto.response;

import com.homerun.domain.diagnosis.type.DiagnosisVerdict;
import com.homerun.domain.diagnosis.type.DiagnosisWarning;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DiagnosisResponse(
        Long diagnosisId,
        Long planId,
        DiagnosisCostResponse initialCost,
        DiagnosisPolicyComparisonResponse policyComparison,
        long monthlyIncome,
        long monthlyHousingCost,
        long monthlyLivingExpense,
        long monthlyDebtPayment,
        long monthlyDisposable,
        int monthsToMove,
        long savableAmount,
        long expectedFund,
        long shortfall,
        LocalDate possibleDate,
        DiagnosisVerdict verdict,
        List<DiagnosisWarning> warnings,
        String engineVersion,
        Instant calculatedAt) {}
