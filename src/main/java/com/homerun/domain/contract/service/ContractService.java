package com.homerun.domain.contract.service;

import com.homerun.domain.contract.dto.ContractDtos.ContractGuide;
import com.homerun.domain.contract.dto.ContractDtos.SaveRequest;
import com.homerun.domain.contract.entity.LeaseContract;
import com.homerun.domain.contract.repository.LeaseContractRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.property.dto.request.PropertyFacts;
import com.homerun.domain.property.dto.response.PropertyVerification;
import com.homerun.domain.property.service.PropertyVerificationService;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계약 실행(PRP-02).
 *
 * <p>계약 한 건을 저장하고, 그 계약 상태에 맞는 안내를 모아 준다. 판정은 각 조언자가 하고
 * 여기서는 소유권 확인과 조립만 한다.
 */
@Service
public class ContractService {

    private final LeaseContractRepository contracts;
    private final PlanRepository plans;
    private final ContractProgressReader progress;
    private final PreConsultAdvisor preConsult;
    private final PreContractChecklist checklist;
    private final SpecialTermAdvisor specialTerms;
    private final SettlementAdvisor settlement;
    private final PropertyVerificationService verification;

    public ContractService(
            LeaseContractRepository contracts,
            PlanRepository plans,
            ContractProgressReader progress,
            PreConsultAdvisor preConsult,
            PreContractChecklist checklist,
            SpecialTermAdvisor specialTerms,
            SettlementAdvisor settlement,
            PropertyVerificationService verification) {
        this.contracts = contracts;
        this.plans = plans;
        this.progress = progress;
        this.preConsult = preConsult;
        this.checklist = checklist;
        this.specialTerms = specialTerms;
        this.settlement = settlement;
        this.verification = verification;
    }

    /** 남의 계획을 건드리지 못하게 한다(SEC-01-04). */
    private void verifyOwner(Long memberId, Long planId) {
        plans.findById(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND))
                .verifyOwner(memberId);
    }

    /** 계약 정보를 저장한다(PRP-02-04). 계획당 한 건이라 있으면 덮어쓴다. */
    @Transactional
    public ContractGuide save(Long memberId, Long planId, SaveRequest request) {
        verifyOwner(memberId, planId);

        LeaseContract contract = contracts
                .findByPlanId(planId)
                .orElseGet(
                        () -> new LeaseContract(planId, request.leaseType(), request.deposit(), request.monthlyRent()));
        contract.overwrite(
                request.propertyId(),
                request.leaseType(),
                request.deposit(),
                request.monthlyRent(),
                request.maintenanceFee(),
                request.downPayment(),
                request.contractDate(),
                request.balanceDate(),
                request.moveInDate(),
                request.confirmedDateAt(),
                request.moveInReportAt(),
                request.bankConsultedAt(),
                request.loanAppliedAt(),
                request.balancePaidAt(),
                request.electronic());

        return toGuide(contracts.save(contract));
    }

    /**
     * 계약 실행 안내를 모아 준다(PRP-02).
     *
     * <p>계약 정보가 아직 없어도 안내는 나간다. 계약서를 쓰기 전에 무엇을 확인해야 하는지가
     * 정작 제일 필요한 시점이다.
     */
    @Transactional(readOnly = true)
    public ContractGuide guide(Long memberId, Long planId) {
        verifyOwner(memberId, planId);
        return toGuide(contracts.findByPlanId(planId).orElse(null));
    }

    /**
     * 매물 검증(PRP-01, PRP-02-07).
     *
     * <p>보증금이 걸린 월세는 전세와 같은 검증을 받는다. 판정 자체는 규칙들이 보증금 기준으로
     * 하므로 임대차 유형으로 갈라내지 않는다.
     */
    @Transactional(readOnly = true)
    public PropertyVerification riskCheck(Long memberId, Long planId, PropertyFacts facts) {
        verifyOwner(memberId, planId);
        return verification.verify(facts);
    }

    private ContractGuide toGuide(LeaseContract contract) {
        return new ContractGuide(
                contract == null ? null : contract.getId(),
                contract == null ? null : contract.getLeaseType(),
                contract != null && contract.hasDeposit(),
                progress.read(contract),
                preConsult.advise(contract),
                checklist.build(contract),
                specialTerms.advise(contract),
                settlement.advise(contract));
    }
}
