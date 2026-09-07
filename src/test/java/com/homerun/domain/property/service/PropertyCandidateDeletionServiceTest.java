package com.homerun.domain.property.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.homerun.domain.contract.repository.LeaseContractRepository;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.policy.entity.PolicyVerdict;
import com.homerun.domain.policy.repository.PolicyVerdictRepository;
import com.homerun.domain.policy.repository.RejectionReasonRepository;
import com.homerun.domain.policy.repository.VerdictBasisRepository;
import com.homerun.domain.property.entity.Property;
import com.homerun.domain.property.entity.PropertyDecision;
import com.homerun.domain.property.repository.BankConsultationRepository;
import com.homerun.domain.property.repository.PropertyCheckRepository;
import com.homerun.domain.property.repository.PropertyDecisionRepository;
import com.homerun.domain.property.repository.PropertyPolicyVerdictRepository;
import com.homerun.domain.property.repository.PropertyRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PropertyCandidateDeletionServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;
    private static final Long PROPERTY_ID = 20L;

    private final PlanRepository plans = mock(PlanRepository.class);
    private final PropertyRepository properties = mock(PropertyRepository.class);
    private final PropertyDecisionRepository decisions = mock(PropertyDecisionRepository.class);
    private final LeaseContractRepository contracts = mock(LeaseContractRepository.class);
    private final PropertyCheckRepository checks = mock(PropertyCheckRepository.class);
    private final PropertyPolicyVerdictRepository propertyVerdicts = mock(PropertyPolicyVerdictRepository.class);
    private final BankConsultationRepository consultations = mock(BankConsultationRepository.class);
    private final PolicyVerdictRepository policyVerdicts = mock(PolicyVerdictRepository.class);
    private final VerdictBasisRepository verdictBases = mock(VerdictBasisRepository.class);
    private final RejectionReasonRepository rejectionReasons = mock(RejectionReasonRepository.class);
    private final PropertyCandidateDeletionService service = new PropertyCandidateDeletionService(
            plans,
            properties,
            decisions,
            contracts,
            checks,
            propertyVerdicts,
            consultations,
            policyVerdicts,
            verdictBases,
            rejectionReasons);

    private Property property;

    @BeforeEach
    void setUp() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, null)));
        property = Property.candidate(
                PLAN_ID, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        ReflectionTestUtils.setField(property, "id", PROPERTY_ID);
        when(properties.findByIdAndPlanId(PROPERTY_ID, PLAN_ID)).thenReturn(Optional.of(property));
        when(decisions.findByPlanId(PLAN_ID)).thenReturn(Optional.empty());
        when(contracts.findByPlanId(PLAN_ID)).thenReturn(Optional.empty());
    }

    @Test
    void should_deleteCandidateAndItsDependentRecords() {
        PolicyVerdict oldVerdict = mock(PolicyVerdict.class);
        when(oldVerdict.getId()).thenReturn(30L);
        when(policyVerdicts.findAllByPropertyId(PROPERTY_ID)).thenReturn(List.of(oldVerdict));

        service.delete(MEMBER_ID, PLAN_ID, PROPERTY_ID);

        verify(verdictBases).deleteByVerdictId(30L);
        verify(rejectionReasons).deleteByVerdictId(30L);
        verify(policyVerdicts).deleteAll(List.of(oldVerdict));
        verify(propertyVerdicts).deleteByPropertyId(PROPERTY_ID);
        verify(checks).deleteByPropertyId(PROPERTY_ID);
        verify(consultations).deleteByPlanIdAndPropertyId(PLAN_ID, PROPERTY_ID);
        verify(properties).delete(property);
    }

    @Test
    void should_rejectDeletingFinalDecisionProperty() {
        PropertyDecision decision = mock(PropertyDecision.class);
        when(decision.getPropertyId()).thenReturn(PROPERTY_ID);
        when(decisions.findByPlanId(PLAN_ID)).thenReturn(Optional.of(decision));

        assertThatThrownBy(() -> service.delete(MEMBER_ID, PLAN_ID, PROPERTY_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.PROPERTY_DELETE_LOCKED));

        verify(properties, never()).delete(property);
    }
}
