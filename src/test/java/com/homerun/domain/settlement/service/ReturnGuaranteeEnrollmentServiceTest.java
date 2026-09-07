package com.homerun.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.settlement.dto.request.ReturnGuaranteeEnrollmentRequest;
import com.homerun.domain.settlement.dto.response.ReturnGuaranteeEnrollmentResponse;
import com.homerun.domain.settlement.entity.ReturnGuaranteeEnrollment;
import com.homerun.domain.settlement.repository.ReturnGuaranteeEnrollmentRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 보증료 지원 활성 = 가입 ∧ 납부 파생, 계획당 1건 덮어쓰기, 미저장 조회 예외를 본다. */
class ReturnGuaranteeEnrollmentServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;

    private final PlanRepository plans = mock(PlanRepository.class);
    private final ReturnGuaranteeEnrollmentRepository enrollments = mock(ReturnGuaranteeEnrollmentRepository.class);
    private final ReturnGuaranteeEnrollmentService service = new ReturnGuaranteeEnrollmentService(plans, enrollments);

    private void owned() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, null)));
        when(enrollments.save(any(ReturnGuaranteeEnrollment.class))).thenAnswer(i -> i.getArgument(0));
        when(enrollments.findByPlanId(PLAN_ID)).thenReturn(Optional.empty());
    }

    private ReturnGuaranteeEnrollmentResponse save(boolean enrolled, boolean feePaid) {
        return service.save(MEMBER_ID, PLAN_ID, new ReturnGuaranteeEnrollmentRequest(enrolled, feePaid, null));
    }

    @Test
    void should_activateFeeSupport_onlyWhenEnrolledAndFeePaid() {
        owned();
        assertThat(save(true, true).feeSupportApplicable()).isTrue();
    }

    @Test
    void should_notActivateFeeSupport_whenEnrolledButFeeUnpaid() {
        owned();
        assertThat(save(true, false).feeSupportApplicable()).isFalse();
    }

    @Test
    void should_notActivateFeeSupport_whenNotEnrolled() {
        owned();
        assertThat(save(false, true).feeSupportApplicable()).isFalse();
    }

    @Test
    void should_overwriteExisting_whenSavingAgain() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, null)));
        when(enrollments.save(any(ReturnGuaranteeEnrollment.class))).thenAnswer(i -> i.getArgument(0));
        ReturnGuaranteeEnrollment existing = new ReturnGuaranteeEnrollment(PLAN_ID);
        when(enrollments.findByPlanId(PLAN_ID)).thenReturn(Optional.of(existing));

        service.save(MEMBER_ID, PLAN_ID, new ReturnGuaranteeEnrollmentRequest(true, true, null));

        assertThat(existing.isEnrolled()).isTrue();
        assertThat(existing.isFeePaid()).isTrue();
    }

    @Test
    void should_throw_whenGettingUnsaved() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, null)));
        when(enrollments.findByPlanId(PLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(MEMBER_ID, PLAN_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RETURN_GUARANTEE_NOT_FOUND));
    }
}
