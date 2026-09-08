package com.homerun.domain.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.policy.dto.request.CostSimulationRequest;
import com.homerun.domain.policy.dto.response.AncillaryCostResponse;
import com.homerun.domain.policy.dto.response.CostSimulationResponse;
import com.homerun.domain.policy.dto.response.TotalCostResponse;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 소유·보증금 검증 4분기와 계산 위임 통과를 본다. 계산 자체는 AncillaryCostCalculator 몫이라 mock 한다. */
class CostSimulationServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;

    private final PlanRepository plans = mock(PlanRepository.class);
    private final PlanInputRepository inputs = mock(PlanInputRepository.class);
    private final AncillaryCostCalculator calculator = mock(AncillaryCostCalculator.class);
    private final CostSimulationService service = new CostSimulationService(plans, inputs, calculator);

    private final CostSimulationRequest request =
            new CostSimulationRequest(144_000_000L, new BigDecimal("2.2"), null, Boolean.TRUE, 500_000L);

    private Plan ownedPlan(Long owner) {
        return Plan.create(owner, LeaseType.JEONSE, null);
    }

    private PlanInput inputWithDeposit(Long deposit) {
        PlanInput input = mock(PlanInput.class);
        when(input.getHopeDeposit()).thenReturn(deposit);
        return input;
    }

    @Test
    void should_returnDepositAndDelegatedCosts_whenOwnedWithDeposit() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(ownedPlan(MEMBER_ID)));
        PlanInput input = inputWithDeposit(200_000_000L);
        when(inputs.findByPlanId(PLAN_ID)).thenReturn(Optional.of(input));
        AncillaryCostResponse ancillary = mock(AncillaryCostResponse.class);
        TotalCostResponse total = mock(TotalCostResponse.class);
        when(calculator.ancillaryCost(anyLong(), anyLong(), any(), anyBoolean(), any()))
                .thenReturn(ancillary);
        when(calculator.totalCostYear1(anyLong(), any(), any(), anyBoolean())).thenReturn(total);

        CostSimulationResponse r = service.simulate(MEMBER_ID, PLAN_ID, request);

        assertThat(r.deposit()).isEqualTo(200_000_000L);
        assertThat(r.loanAmount()).isEqualTo(144_000_000L);
        assertThat(r.ancillary()).isSameAs(ancillary);
        assertThat(r.totalCostYear1()).isSameAs(total);
    }

    @Test
    void should_throwPlanNotFound_whenPlanMissing() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.simulate(MEMBER_ID, PLAN_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PLAN_NOT_FOUND);
    }

    @Test
    void should_throwAccessDenied_whenNotOwner() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(ownedPlan(999L)));
        assertThatThrownBy(() -> service.simulate(MEMBER_ID, PLAN_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PLAN_ACCESS_DENIED);
    }

    @Test
    void should_throwInputRequired_whenPlanInputMissing() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(ownedPlan(MEMBER_ID)));
        when(inputs.findByPlanId(PLAN_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.simulate(MEMBER_ID, PLAN_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PLAN_INPUT_NOT_FOUND);
    }

    @Test
    void should_throwDiagnosisInputRequired_whenDepositUnknown() {
        // 희망 보증금을 모르면 부대비용을 계산할 수 없다 -- 0 으로 채우지 않고 막는다.
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(ownedPlan(MEMBER_ID)));
        PlanInput input = inputWithDeposit(null);
        when(inputs.findByPlanId(PLAN_ID)).thenReturn(Optional.of(input));
        assertThatThrownBy(() -> service.simulate(MEMBER_ID, PLAN_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DIAGNOSIS_INPUT_REQUIRED);
    }
}
