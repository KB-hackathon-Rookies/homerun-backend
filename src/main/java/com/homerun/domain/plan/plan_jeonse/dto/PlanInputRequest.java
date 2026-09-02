package com.homerun.plan.dto;

import com.homerun.domain.plan.plan_J.PlanInputUnknownField;
import com.homerun.plan.domain.enums.CompanySize;
import com.homerun.plan.domain.enums.EmploymentType;
import com.homerun.plan.domain.enums.HouseType;
import com.homerun.plan.domain.enums.HouseholderStatus;
import com.homerun.plan.domain.enums.MaritalStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PlanInputRequest {

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
}