package com.homerun.domain.plan.dto.response;

import com.homerun.domain.plan.type.CompanySize;
import com.homerun.domain.plan.type.EmploymentType;
import com.homerun.domain.plan.type.HouseType;
import com.homerun.domain.plan.type.HouseholderStatus;
import com.homerun.domain.plan.type.MaritalStatus;
import com.homerun.domain.plan.type.PlanInputUnknownField;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
public class PlanInputResponse {

    private Long id;
    private Long planId;

    // 주거 비용
    private Long hopeDeposit;
    private Long currentDeposit;
    private Long monthlyRent;
    private Long maintenanceFee;
    private Long maxMonthlyBurden;

    // 주거 조건
    private Long regionId;
    private BigDecimal areaM2;
    private HouseType houseType;

    // 가구 조건
    private Boolean isHomeless;
    private HouseholderStatus householderStatus;
    private MaritalStatus maritalStatus;

    // 직업 조건
    private EmploymentType employmentType;
    private Integer employmentMonths;
    private CompanySize companySize;

    // 사용자가 "모름"으로 선택한 항목
    private List<PlanInputUnknownField> unknownFields;

    private OffsetDateTime createdAt;
}