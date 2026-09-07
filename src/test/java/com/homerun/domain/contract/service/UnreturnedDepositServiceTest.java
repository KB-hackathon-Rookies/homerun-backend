package com.homerun.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.settlement.service.ReturnGuaranteeStatusResolver;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** override 를 주면 그 값을 쓰고, 없으면 resolver 로 자동 판단하는 배선을 본다. */
class UnreturnedDepositServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;

    private final PlanRepository plans = mock(PlanRepository.class);
    private final ReturnGuaranteeStatusResolver resolver = mock(ReturnGuaranteeStatusResolver.class);
    private final UnreturnedDepositService service =
            new UnreturnedDepositService(plans, new UnreturnedDepositAdvisor(), resolver);

    private void owned() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, null)));
    }

    @Test
    void should_useResolver_whenOverrideNull() {
        owned();
        when(resolver.hasReturnGuarantee(PLAN_ID)).thenReturn(true);

        // 가입자는 이행청구로 끝나 소송 단계가 없다 -- resolver 가 true 를 준 결과가 반영된다.
        assertThat(service.guide(MEMBER_ID, PLAN_ID, null).steps()).noneMatch(s -> s.contains("소송"));
    }

    @Test
    void should_preferOverride_andNotCallResolver() {
        owned();

        // 직접 false 를 주면 resolver 를 부르지 않고 법적 절차(소송 포함)를 준다.
        assertThat(service.guide(MEMBER_ID, PLAN_ID, false).steps()).anyMatch(s -> s.contains("소송"));
        verify(resolver, never()).hasReturnGuarantee(PLAN_ID);
    }
}
