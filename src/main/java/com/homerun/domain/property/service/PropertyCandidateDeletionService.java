package com.homerun.domain.property.service;

import com.homerun.domain.contract.repository.LeaseContractRepository;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.policy.entity.PolicyVerdict;
import com.homerun.domain.policy.repository.PolicyVerdictRepository;
import com.homerun.domain.policy.repository.RejectionReasonRepository;
import com.homerun.domain.policy.repository.VerdictBasisRepository;
import com.homerun.domain.property.entity.Property;
import com.homerun.domain.property.repository.BankConsultationRepository;
import com.homerun.domain.property.repository.PropertyCheckRepository;
import com.homerun.domain.property.repository.PropertyDecisionRepository;
import com.homerun.domain.property.repository.PropertyPolicyVerdictRepository;
import com.homerun.domain.property.repository.PropertyRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 비교 후보에서 매물을 제거한다. 최종 선택·계약 이력은 실수로 지우지 않는다. */
@Service
public class PropertyCandidateDeletionService {

    private final PlanRepository plans;
    private final PropertyRepository properties;
    private final PropertyDecisionRepository decisions;
    private final LeaseContractRepository contracts;
    private final PropertyCheckRepository checks;
    private final PropertyPolicyVerdictRepository propertyVerdicts;
    private final BankConsultationRepository consultations;
    private final PolicyVerdictRepository policyVerdicts;
    private final VerdictBasisRepository verdictBases;
    private final RejectionReasonRepository rejectionReasons;

    public PropertyCandidateDeletionService(
            PlanRepository plans,
            PropertyRepository properties,
            PropertyDecisionRepository decisions,
            LeaseContractRepository contracts,
            PropertyCheckRepository checks,
            PropertyPolicyVerdictRepository propertyVerdicts,
            BankConsultationRepository consultations,
            PolicyVerdictRepository policyVerdicts,
            VerdictBasisRepository verdictBases,
            RejectionReasonRepository rejectionReasons) {
        this.plans = plans;
        this.properties = properties;
        this.decisions = decisions;
        this.contracts = contracts;
        this.checks = checks;
        this.propertyVerdicts = propertyVerdicts;
        this.consultations = consultations;
        this.policyVerdicts = policyVerdicts;
        this.verdictBases = verdictBases;
        this.rejectionReasons = rejectionReasons;
    }

    @Transactional
    public void delete(Long memberId, Long planId, Long propertyId) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        Property property = properties
                .findByIdAndPlanId(propertyId, planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROPERTY_NOT_IN_PLAN));

        boolean decided = decisions
                .findByPlanId(planId)
                .map(decision -> decision.getPropertyId().equals(propertyId))
                .orElse(false);
        boolean contracted = contracts
                .findByPlanId(planId)
                .map(contract -> propertyId.equals(contract.getPropertyId()))
                .orElse(false);
        if (decided || contracted) {
            throw new BusinessException(ErrorCode.PROPERTY_DELETE_LOCKED);
        }

        List<PolicyVerdict> oldVerdicts = policyVerdicts.findAllByPropertyId(propertyId);
        for (PolicyVerdict verdict : oldVerdicts) {
            verdictBases.deleteByVerdictId(verdict.getId());
            rejectionReasons.deleteByVerdictId(verdict.getId());
        }
        policyVerdicts.deleteAll(oldVerdicts);
        propertyVerdicts.deleteByPropertyId(propertyId);
        checks.deleteByPropertyId(propertyId);
        consultations.deleteByPlanIdAndPropertyId(planId, propertyId);
        properties.delete(property);
    }
}
