package com.homerun.domain.property.service;

import com.homerun.domain.contract.repository.LeaseContractRepository;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.policy.entity.PolicyVerdict;
import com.homerun.domain.policy.repository.PolicyVerdictRepository;
import com.homerun.domain.policy.repository.RejectionReasonRepository;
import com.homerun.domain.policy.repository.VerdictBasisRepository;
import com.homerun.domain.property.dto.response.PropertyWorkflowResponse;
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

        if (isLocked(planId, propertyId)) {
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

    /**
     * 재진단 — 매물을 지우지 않고 워크플로만 첫 STEP 으로 되돌린다.
     *
     * <p>잘못 입력해 불가가 난 매물을 삭제 후 재등록(주소 재입력·재조회) 없이 고치게 한다.
     * 삭제와 같은 잠금을 쓴다 — 최종 선택·계약에 쓴 매물은 되돌리지 않는다({@link #isLocked}).
     * 저장된 판정은 지우지 않는다. 재진단 뒤 카드/상세가 {@code evaluatePolicyVerdicts} 로 다시
     * 계산하고, 등기부 재저장 시 새로 덮어쓴다.
     */
    @Transactional
    public PropertyWorkflowResponse reDiagnose(Long memberId, Long planId, Long propertyId) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        Property property = properties
                .findByIdAndPlanId(propertyId, planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROPERTY_NOT_IN_PLAN));

        if (isLocked(planId, propertyId)) {
            throw new BusinessException(ErrorCode.PROPERTY_RECHECK_LOCKED);
        }

        property.resetDiagnosis();
        return PropertyWorkflowResponse.from(property);
    }

    /** 최종 선택했거나 계약에 사용한 매물인가. 삭제·재진단이 공유하는 잠금 판정이다. */
    private boolean isLocked(Long planId, Long propertyId) {
        boolean decided = decisions
                .findByPlanId(planId)
                .map(decision -> decision.getPropertyId().equals(propertyId))
                .orElse(false);
        boolean contracted = contracts
                .findByPlanId(planId)
                .map(contract -> propertyId.equals(contract.getPropertyId()))
                .orElse(false);
        return decided || contracted;
    }
}
