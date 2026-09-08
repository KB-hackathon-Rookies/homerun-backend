package com.homerun.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.settlement.dto.request.MonthlyMetricsRequest;
import com.homerun.domain.settlement.dto.response.MonthlyMetricsResponse;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 소유권 검증 후 계산 위임 통과와 접근 예외를 본다. 계산은 MonthlyMetricsCalculator 몫이라 mock 한다. */
class MonthlyMetricsServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;

    private final PlanRepository plans = mock(PlanRepository.class);
    private final MonthlyMetricsCalculator calculator = mock(MonthlyMetricsCalculator.class);
    private final MonthlyMetricsService service = new MonthlyMetricsService(plans, calculator);
    private final MonthlyMetricsRequest request =
            new MonthlyMetricsRequest(144_000_000L, new BigDecimal("2.2"), 100_000L, 3_000_000L, 1_500_000L);

    @Test
    void should_delegateToCalculator_whenOwned() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, null)));
        MonthlyMetricsResponse expected = mock(MonthlyMetricsResponse.class);
        when(calculator.calculate(anyLong(), any(), anyLong(), anyLong(), anyLong()))
                .thenReturn(expected);

        assertThat(service.forPlan(MEMBER_ID, PLAN_ID, request)).isSameAs(expected);
    }

    @Test
    void should_throwPlanNotFound_whenMissing() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.forPlan(MEMBER_ID, PLAN_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PLAN_NOT_FOUND);
    }

    @Test
    void should_throwAccessDenied_whenNotOwner() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(Plan.create(999L, LeaseType.JEONSE, null)));
        assertThatThrownBy(() -> service.forPlan(MEMBER_ID, PLAN_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PLAN_ACCESS_DENIED);
    }
}
