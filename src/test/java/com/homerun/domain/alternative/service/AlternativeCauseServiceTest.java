package com.homerun.domain.alternative.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.alternative.dto.response.CausesResponse;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.policy.entity.Policy;
import com.homerun.domain.policy.entity.PolicyVerdict;
import com.homerun.domain.policy.entity.RejectionReason;
import com.homerun.domain.policy.repository.PolicyRepository;
import com.homerun.domain.policy.repository.PolicyVerdictRepository;
import com.homerun.domain.policy.repository.RejectionReasonRepository;
import com.homerun.domain.policy.type.PolicyVerdictResult;
import com.homerun.domain.policy.type.RejectionReasonCategory;
import com.homerun.global.exception.BusinessException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 새 판정을 하지 않는다 — 이미 저장된 FAIL policy_verdict/rejection_reason 을 모아 보여주기만
 * 하는지를 검증한다. 판정 자체(카테고리 분류 포함)는 JeonsePolicyVerdictServiceTest 가 본다. */
class AlternativeCauseServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;
    private static final Long POLICY_ID = 100L;
    private static final Long ALT_POLICY_ID = 200L;
    private static final Long VERDICT_ID = 1000L;

    private final PlanRepository plans = mock(PlanRepository.class);
    private final PolicyVerdictRepository verdicts = mock(PolicyVerdictRepository.class);
    private final RejectionReasonRepository reasons = mock(RejectionReasonRepository.class);
    private final PolicyRepository policies = mock(PolicyRepository.class);
    private final AlternativeCauseService service = new AlternativeCauseService(plans, verdicts, reasons, policies);

    @BeforeEach
    void setUp() {
        when(plans.findById(PLAN_ID))
                .thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, LocalDate.of(2026, 12, 1))));
    }

    @Test
    void should_returnCauseWithAlternative_when_failedVerdictHasRejectionReason() {
        PolicyVerdict verdict = failedVerdict();
        when(verdicts.findAllByPlanId(PLAN_ID)).thenReturn(List.of(verdict));
        Policy policy = mockPolicy(POLICY_ID, "JEONSE-YOUTH-BEOTIMMOK", "청년전용 버팀목");
        Policy alternative = mockPolicy(ALT_POLICY_ID, "JEONSE-GENERAL-BEOTIMMOK", "일반 버팀목");
        when(policies.findAllById(List.of(POLICY_ID))).thenReturn(List.of(policy));
        when(policies.findById(ALT_POLICY_ID)).thenReturn(Optional.of(alternative));
        when(reasons.findByVerdictId(VERDICT_ID))
                .thenReturn(List.of(RejectionReason.create(VERDICT_ID, "AGE_UPPER_BOUND", "만 34세 이하", ALT_POLICY_ID)));

        CausesResponse response = service.causes(MEMBER_ID, PLAN_ID);

        assertThat(response.causes()).hasSize(1);
        var cause = response.causes().get(0);
        assertThat(cause.policyCode()).isEqualTo("JEONSE-YOUTH-BEOTIMMOK");
        assertThat(cause.reasonCode()).isEqualTo("AGE_UPPER_BOUND");
        assertThat(cause.category()).isEqualTo(RejectionReasonCategory.USER);
        assertThat(cause.alternativePolicyCode()).isEqualTo("JEONSE-GENERAL-BEOTIMMOK");
    }

    @Test
    void should_returnNullAlternative_when_rejectionReasonHasNoAlternative() {
        PolicyVerdict verdict = failedVerdict();
        when(verdicts.findAllByPlanId(PLAN_ID)).thenReturn(List.of(verdict));
        Policy policy = mockPolicy(POLICY_ID, "JEONSE-YOUTH-BEOTIMMOK", "청년전용 버팀목");
        when(policies.findAllById(List.of(POLICY_ID))).thenReturn(List.of(policy));
        when(reasons.findByVerdictId(VERDICT_ID))
                .thenReturn(List.of(RejectionReason.create(VERDICT_ID, "NOT_VIOLATION_BUILDING", "위반건축물 아님", null)));

        CausesResponse response = service.causes(MEMBER_ID, PLAN_ID);

        var cause = response.causes().get(0);
        assertThat(cause.alternativePolicyCode()).isNull();
        assertThat(cause.category()).isEqualTo(RejectionReasonCategory.HOUSE);
    }

    @Test
    void should_returnEmpty_when_noFailedVerdicts() {
        PolicyVerdict passVerdict = mock(PolicyVerdict.class);
        when(passVerdict.getVerdict()).thenReturn(PolicyVerdictResult.PASS);
        when(verdicts.findAllByPlanId(PLAN_ID)).thenReturn(List.of(passVerdict));

        CausesResponse response = service.causes(MEMBER_ID, PLAN_ID);

        assertThat(response.causes()).isEmpty();
    }

    @Test
    void should_throw_when_requesterIsNotPlanOwner() {
        assertThatThrownBy(() -> service.causes(999L, PLAN_ID)).isInstanceOf(BusinessException.class);
    }

    private PolicyVerdict failedVerdict() {
        PolicyVerdict verdict = mock(PolicyVerdict.class);
        when(verdict.getId()).thenReturn(VERDICT_ID);
        when(verdict.getPolicyId()).thenReturn(POLICY_ID);
        when(verdict.getVerdict()).thenReturn(PolicyVerdictResult.FAIL);
        return verdict;
    }

    private Policy mockPolicy(Long id, String code, String name) {
        Policy policy = mock(Policy.class);
        when(policy.getId()).thenReturn(id);
        when(policy.getCode()).thenReturn(code);
        when(policy.getName()).thenReturn(name);
        return policy;
    }
}
