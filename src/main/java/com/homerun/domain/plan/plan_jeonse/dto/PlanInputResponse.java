package com.homerun.domain.plan.plan_J.dto;

import com.homerun.domain.plan.enums.PlanInputUnknownField;
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

    private Long hopeDeposit;
    private Long currentDeposit;
    private Long monthlyRent;
    private Long maintenanceFee;
    private Long maxMonthlyBurden;

    private Long regionId;
    private BigDecimal areaM2;
    private String houseType;

    private Boolean isHomeless;
    private String householderStatus;
    private String maritalStatus;

    private String employmentType;
    private Integer employmentMonths;
    private String companySize;

    private List<PlanInputUnknownField> unknownFields;

    private OffsetDateTime createdAt;
}

