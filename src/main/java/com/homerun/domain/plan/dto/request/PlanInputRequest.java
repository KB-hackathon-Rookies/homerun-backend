package com.homerun.domain.plan.dto.request;

import com.homerun.domain.plan.type.*;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
public class PlanInputRequest {

    // ---주거 비용
    // 희망 보증금
    private Long hopeDeposit;
    // 기존 보증금 금액
    // StartSituation == MOVING_FROM_RENTAL
    private Long currentDeposit;
    // 희망 월세
    private Long monthlyRent;
    // 관리비
    private Long maintenanceFee;
    // 감당 가능한 월 부담 상환
    private Long maxMonthlyBurden;

    // ---주거 조건
    // 지역 참조
    private Long regionId;
    // 전용 면적
    private BigDecimal areaM2;
    // 주택 유형
    private HouseType houseType;

    // ---가구 조건
    // 무주택 여부
    private Boolean isHomeless;
    // 세대주 여부
    private HouseholderStatus householderStatus;
    // 혼인 상태
    private MaritalStatus maritalStatus;

    // ---직업 조건
    // 고용형태
    private EmploymentType employmentType;
    // 재직 개월
    private Integer employmentMonths;
    // 기업 규모
    private CompanySize companySize;

    // 사용자가 "모름"으로 선택한 항목
    private List<PlanInputUnknownField> unknownFields;
}
