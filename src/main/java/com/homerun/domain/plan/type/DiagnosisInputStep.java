package com.homerun.domain.plan.type;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public enum DiagnosisInputStep {
    HOUSEHOLDER(Set.of(PlanInputUnknownField.HOUSEHOLDER_STATUS)),
    HOMELESS(Set.of(PlanInputUnknownField.IS_HOMELESS, PlanInputUnknownField.HOUSEHOLD_HOMELESS)),
    MARITAL_STATUS(Set.of(PlanInputUnknownField.MARITAL_STATUS)),
    EMPLOYMENT_TYPE(Set.of(PlanInputUnknownField.EMPLOYMENT_TYPE)),
    COMPANY_SIZE(Set.of(PlanInputUnknownField.COMPANY_SIZE)),
    EMPLOYMENT_PERIOD(Set.of(PlanInputUnknownField.EMPLOYMENT_MONTHS)),
    FINANCIAL(Set.of(
            PlanInputUnknownField.MONTHLY_INCOME,
            PlanInputUnknownField.NET_ASSETS,
            PlanInputUnknownField.AVAILABLE_CASH,
            PlanInputUnknownField.EXISTING_JEONSE_LOAN,
            PlanInputUnknownField.PROHIBITED_LOAN_CONFIRMED,
            PlanInputUnknownField.INCOME_SOURCE,
            PlanInputUnknownField.ASSET_SOURCE,
            PlanInputUnknownField.FINANCIAL_DATA_CONFIRMED)),
    HOPE_DEPOSIT(Set.of(PlanInputUnknownField.HOPE_DEPOSIT)),
    REGION(Set.of(PlanInputUnknownField.REGION_ID)),
    REVIEW(Set.of());

    private final Set<PlanInputUnknownField> fields;

    DiagnosisInputStep(Set<PlanInputUnknownField> fields) {
        this.fields = fields;
    }

    public Set<PlanInputUnknownField> fields() {
        return fields;
    }

    public static Optional<DiagnosisInputStep> find(String code) {
        return Arrays.stream(values()).filter(step -> step.name().equals(code)).findFirst();
    }

    public static boolean isSalaried(EmploymentType employmentType) {
        return employmentType == EmploymentType.FULL_TIME
                || employmentType == EmploymentType.CONTRACT
                || employmentType == EmploymentType.INTERN
                || employmentType == EmploymentType.DAILY_WORKER;
    }

    public DiagnosisInputStep next(EmploymentType employmentType) {
        return switch (this) {
            case HOUSEHOLDER -> HOMELESS;
            case HOMELESS -> MARITAL_STATUS;
            case MARITAL_STATUS -> EMPLOYMENT_TYPE;
            case EMPLOYMENT_TYPE -> isSalaried(employmentType) ? COMPANY_SIZE : FINANCIAL;
            case COMPANY_SIZE -> EMPLOYMENT_PERIOD;
            case EMPLOYMENT_PERIOD -> FINANCIAL;
            case FINANCIAL -> HOPE_DEPOSIT;
            case HOPE_DEPOSIT -> REGION;
            case REGION -> REVIEW;
            case REVIEW -> null;
        };
    }

    public static List<DiagnosisInputStep> path(EmploymentType employmentType) {
        if (employmentType == null || isSalaried(employmentType)) {
            return List.of(values());
        }
        return List.of(HOUSEHOLDER, HOMELESS, MARITAL_STATUS, EMPLOYMENT_TYPE, FINANCIAL, HOPE_DEPOSIT, REGION, REVIEW);
    }
}
