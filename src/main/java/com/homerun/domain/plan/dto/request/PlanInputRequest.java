package com.homerun.domain.plan.dto.request;

import com.homerun.domain.plan.type.CompanySize;
import com.homerun.domain.plan.type.EmploymentType;
import com.homerun.domain.plan.type.FinancialValueSource;
import com.homerun.domain.plan.type.HouseType;
import com.homerun.domain.plan.type.HouseholderStatus;
import com.homerun.domain.plan.type.MaritalStatus;
import com.homerun.domain.plan.type.PlanInputUnknownField;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

public record PlanInputRequest(
        @PositiveOrZero(message = "희망 보증금은 0원 이상이어야 합니다") Long hopeDeposit,
        @PositiveOrZero(message = "현재 보유자금은 0원 이상이어야 합니다") Long currentDeposit,
        @PositiveOrZero(message = "월세는 0원 이상이어야 합니다") Long monthlyRent,
        @PositiveOrZero(message = "관리비는 0원 이상이어야 합니다") Long maintenanceFee,
        @PositiveOrZero(message = "월 부담 상한은 0원 이상이어야 합니다") Long maxMonthlyBurden,
        @Positive(message = "지역 식별자는 1 이상이어야 합니다") Long regionId,

        @DecimalMin(value = "0.01", message = "주택 면적은 0보다 커야 합니다")
        BigDecimal areaM2,

        HouseType houseType,
        Boolean isHomeless,
        HouseholderStatus householderStatus,
        MaritalStatus maritalStatus,
        EmploymentType employmentType,
        @PositiveOrZero(message = "재직기간은 0개월 이상이어야 합니다") Integer employmentMonths,
        CompanySize companySize,
        Boolean householdHomeless,
        LocalDate birthDate,
        @PositiveOrZero @Max(60) Integer militaryMonths,
        @PositiveOrZero Long monthlyIncome,
        @PositiveOrZero Long netAssets,
        @PositiveOrZero Long availableCash,
        Boolean existingJeonseLoan,
        FinancialValueSource incomeSource,
        FinancialValueSource assetSource,
        Boolean financialDataConfirmed,

        @Schema(description = "부모와 주민등록상 시·군이 다른가(FCT-043). 주소가 아니라 다른지 여부만 받는다")
        Boolean livesApartFromParents,

        @Schema(description = "부모 가구가 이미 주거급여를 받고 있는가(FCT-044). 청년 단독 신청은 불가하다")
        Boolean parentOnHousingBenefit,

        @Schema(
                description = "세대원 기금대출과 배우자의 전세·주택담보대출이 없음을 확인했는가(FCT-258). "
                        + "사용자 진술이며 은행 검증이 아니다. 확인하지 않으면 판정은 추가확인으로 남는다")
        Boolean prohibitedLoanConfirmed,

        Set<PlanInputUnknownField> unknownFields) {

    /**
     * 원가구 입력(#160)·중복대출 확인 이전 자리수를 위한 편의 생성자. 뒤에 붙은 세 값은
     * 모름(null)으로 둔다 — 안 물어본 것과 아니라고 답한 것은 다르다(COM-05-04).
     */
    public PlanInputRequest(
            Long hopeDeposit,
            Long currentDeposit,
            Long monthlyRent,
            Long maintenanceFee,
            Long maxMonthlyBurden,
            Long regionId,
            BigDecimal areaM2,
            HouseType houseType,
            Boolean isHomeless,
            HouseholderStatus householderStatus,
            MaritalStatus maritalStatus,
            EmploymentType employmentType,
            Integer employmentMonths,
            CompanySize companySize,
            Boolean householdHomeless,
            LocalDate birthDate,
            Integer militaryMonths,
            Long monthlyIncome,
            Long netAssets,
            Long availableCash,
            Boolean existingJeonseLoan,
            FinancialValueSource incomeSource,
            FinancialValueSource assetSource,
            Boolean financialDataConfirmed,
            Set<PlanInputUnknownField> unknownFields) {
        this(
                hopeDeposit,
                currentDeposit,
                monthlyRent,
                maintenanceFee,
                maxMonthlyBurden,
                regionId,
                areaM2,
                houseType,
                isHomeless,
                householderStatus,
                maritalStatus,
                employmentType,
                employmentMonths,
                companySize,
                householdHomeless,
                birthDate,
                militaryMonths,
                monthlyIncome,
                netAssets,
                availableCash,
                existingJeonseLoan,
                incomeSource,
                assetSource,
                financialDataConfirmed,
                null,
                null,
                null,
                unknownFields);
    }

    public Set<PlanInputUnknownField> normalizedUnknownFields() {
        return unknownFields == null ? Set.of() : Set.copyOf(unknownFields);
    }
}
