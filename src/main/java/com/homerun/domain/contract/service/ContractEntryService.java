package com.homerun.domain.contract.service;

import com.homerun.domain.contract.dto.response.ContractEntryResponse;
import com.homerun.domain.contract.entity.LeaseContract;
import com.homerun.domain.contract.repository.LeaseContractRepository;
import com.homerun.domain.contract.type.ContractCollateralMethod;
import com.homerun.domain.contract.type.LoanProductKind;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.HouseType;
import com.homerun.domain.property.entity.BankConsultation;
import com.homerun.domain.property.entity.Property;
import com.homerun.domain.property.entity.PropertyDecision;
import com.homerun.domain.property.repository.BankConsultationRepository;
import com.homerun.domain.property.repository.PropertyDecisionRepository;
import com.homerun.domain.property.repository.PropertyRepository;
import com.homerun.domain.property.type.CollateralMethod;
import com.homerun.domain.property.type.ConsultedLoanProduct;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 2루 최종 선택을 3루 계약 초안으로 넘긴다. */
@Service
public class ContractEntryService {

    private final PlanRepository plans;
    private final PropertyDecisionRepository decisions;
    private final PropertyRepository properties;
    private final BankConsultationRepository consultations;
    private final LeaseContractRepository contracts;

    public ContractEntryService(
            PlanRepository plans,
            PropertyDecisionRepository decisions,
            PropertyRepository properties,
            BankConsultationRepository consultations,
            LeaseContractRepository contracts) {
        this.plans = plans;
        this.decisions = decisions;
        this.properties = properties;
        this.consultations = consultations;
        this.contracts = contracts;
    }

    /**
     * 초안 생성은 멱등이다. 같은 2루 결정으로 다시 진입해도 계약일·잔금일 등 3루에서 입력한
     * 값은 유지하고, 2루가 책임지는 선택값만 동기화한다.
     */
    @Transactional
    public ContractEntryResponse prefill(Long memberId, Long planId) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);

        PropertyDecision decision = decisions
                .findByPlanId(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROPERTY_DECISION_NOT_FOUND));
        Property property = properties
                .findByIdAndPlanId(decision.getPropertyId(), planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROPERTY_DECISION_NOT_FOUND));
        BankConsultation consultation = consultations
                .findByIdAndPlanIdAndPropertyId(decision.getConsultationId(), planId, property.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BANK_CONSULTATION_NOT_FOUND));

        LeaseContract contract = contracts
                .findByPlanId(planId)
                .orElseGet(() -> new LeaseContract(planId, plan.getLeaseType(), deposit(property), 0));
        contract.prefillFromDecision(
                property.getId(),
                plan.getLeaseType(),
                deposit(property),
                loanProduct(consultation.getLoanProduct()),
                collateralMethod(consultation.getCollateralMethod()),
                houseType(property.getHouseType()),
                consultation.getConsultedAt());
        LeaseContract saved = contracts.save(contract);
        return ContractEntryResponse.from(
                saved,
                decision.getRevision(),
                property.getRoadAddress() == null ? property.getAddress() : property.getRoadAddress(),
                consultation.getBankName(),
                consultation.getBranchName());
    }

    private long deposit(Property property) {
        return property.getDeposit() == null ? 0 : property.getDeposit();
    }

    private LoanProductKind loanProduct(ConsultedLoanProduct product) {
        return switch (product) {
            case YOUTH_BEOTIMMOK -> LoanProductKind.FUND_YOUTH;
            case GENERAL_BEOTIMMOK -> LoanProductKind.FUND_GENERAL;
            case BANK_LOAN -> LoanProductKind.BANK;
            case UNKNOWN -> throw new BusinessException(ErrorCode.SECOND_BASE_FINAL_TERMS_INCOMPLETE);
        };
    }

    private ContractCollateralMethod collateralMethod(CollateralMethod method) {
        return switch (method) {
            case HUG_SAFE_JEONSE -> ContractCollateralMethod.HUG_SAFE_JEONSE;
            case HF -> ContractCollateralMethod.HF;
            case SGI -> ContractCollateralMethod.SGI;
            case CLAIM_TRANSFER -> ContractCollateralMethod.CLAIM_TRANSFER;
            case OTHER -> ContractCollateralMethod.OTHER;
            case UNKNOWN -> throw new BusinessException(ErrorCode.SECOND_BASE_FINAL_TERMS_INCOMPLETE);
        };
    }

    private HouseType houseType(String value) {
        if (value == null) {
            return HouseType.OTHER;
        }
        try {
            return HouseType.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return HouseType.OTHER;
        }
    }
}
