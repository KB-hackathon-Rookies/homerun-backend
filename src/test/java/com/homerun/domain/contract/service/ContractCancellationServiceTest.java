package com.homerun.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.contract.dto.request.ContractCancellationRequest;
import com.homerun.domain.contract.dto.response.ContractCancellationResponse;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 소유권 검증 후 계약 해제 안내 위임 통과와 접근·미존재 예외를 본다. 판단은 ContractCancellationAdvisor 몫. */
class ContractCancellationServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;

    private final PlanRepository plans = mock(PlanRepository.class);
    private final ContractCancellationAdvisor advisor = mock(ContractCancellationAdvisor.class);
    private final ContractCancellationService service = new ContractCancellationService(plans, advisor);
    private final ContractCancellationRequest request = mock(ContractCancellationRequest.class);

    @Test
    void should_delegateToAdvisor_whenOwned() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, null)));
        ContractCancellationResponse expected = mock(ContractCancellationResponse.class);
        when(advisor.guide(any(), anyBoolean(), any(), any())).thenReturn(expected);

        assertThat(service.guide(MEMBER_ID, PLAN_ID, request)).isSameAs(expected);
    }

    @Test
    void should_throwPlanNotFound_whenMissing() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.guide(MEMBER_ID, PLAN_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PLAN_NOT_FOUND);
    }

    @Test
    void should_throwAccessDenied_whenNotOwner() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(Plan.create(999L, LeaseType.JEONSE, null)));
        assertThatThrownBy(() -> service.guide(MEMBER_ID, PLAN_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PLAN_ACCESS_DENIED);
    }
}
