package com.homerun.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.contract.dto.response.LoanTransferGuideResponse;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.global.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** FR-H10-03. 정적 안내지만 소유권 확인과 두 방법·문의 안내는 지킨다. */
class LoanTransferGuideServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;

    private final PlanRepository plans = mock(PlanRepository.class);
    private final LoanTransferGuideService service = new LoanTransferGuideService(plans);

    @Test
    void should_giveTwoMethodsAndInquiryNote() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, null)));

        LoanTransferGuideResponse r = service.forPlan(MEMBER_ID, PLAN_ID);

        assertThat(r.methods())
                .extracting(LoanTransferGuideResponse.Method::name)
                .containsExactly("상환 후 신규", "임차목적물 변경");
        assertThat(r.note()).contains("은행마다");
    }

    @Test
    void should_throw_whenNotOwner() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, null)));
        assertThatThrownBy(() -> service.forPlan(999L, PLAN_ID)).isInstanceOf(BusinessException.class);
    }
}
