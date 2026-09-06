package com.homerun.domain.contract.service;

import com.homerun.domain.contract.dto.request.ThirdBaseCompleteRequest;
import com.homerun.domain.contract.dto.response.RegistryComparisonResponse;
import com.homerun.domain.contract.dto.response.ThirdBaseCompleteResponse;
import com.homerun.domain.contract.dto.response.ThirdBaseCompleteResponse.HomeHandoff;
import com.homerun.domain.contract.entity.LeaseContract;
import com.homerun.domain.contract.repository.LeaseContractRepository;
import com.homerun.domain.contract.type.ContractCollateralMethod;
import com.homerun.domain.contract.type.RegistryComparisonStatus;
import com.homerun.domain.plan.dto.request.CompletePlanStepRequest;
import com.homerun.domain.plan.dto.response.PlanProgressResponse;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.service.PlanService;
import com.homerun.domain.plan.type.PlanGate;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 3루의 실제 완료 조건을 검증하고 HOME 정착 작업으로 넘긴다. */
@Service
public class ThirdBaseCompletionService {

    private final PlanRepository plans;
    private final LeaseContractRepository contracts;
    private final RegistryComparisonService registries;
    private final PlanService planService;

    public ThirdBaseCompletionService(
            PlanRepository plans,
            LeaseContractRepository contracts,
            RegistryComparisonService registries,
            PlanService planService) {
        this.plans = plans;
        this.contracts = contracts;
        this.registries = registries;
        this.planService = planService;
    }

    @Transactional
    public ThirdBaseCompleteResponse complete(Long memberId, Long planId, ThirdBaseCompleteRequest request) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        plan.verifyRuleVersion(request.ruleVersion());

        LeaseContract contract =
                contracts.findByPlanId(planId).orElseThrow(() -> new BusinessException(ErrorCode.CONTRACT_NOT_FOUND));
        if (contract.getBalancePaidAt() == null || contract.getMoveInReportAt() == null) {
            throw new BusinessException(ErrorCode.THIRD_BASE_EXECUTION_INCOMPLETE);
        }

        RegistryComparisonResponse comparison = registries.compare(memberId, planId);
        if (comparison.status() != RegistryComparisonStatus.SAFE) {
            throw new BusinessException(ErrorCode.THIRD_BASE_REGISTRY_RECHECK_REQUIRED);
        }

        PlanProgressResponse progress = planService.completeStep(
                memberId, planId, PlanGate.THIRD_EXECUTION.code(), new CompletePlanStepRequest(request.ruleVersion()));
        return new ThirdBaseCompleteResponse(true, contract.getCollateralMethod(), homeHandoffs(contract), progress);
    }

    private List<HomeHandoff> homeHandoffs(LeaseContract contract) {
        if (contract.getCollateralMethod() == ContractCollateralMethod.HUG_SAFE_JEONSE) {
            return List.of(
                    new HomeHandoff("GUARANTEE_FEE_SUPPORT", "보증료 지원 확인", "HUG 안심전세는 반환보증이 포함되어 있어 보증료 지원 여부만 확인합니다."));
        }
        if (contract.getCollateralMethod() == ContractCollateralMethod.HF
                || contract.getCollateralMethod() == ContractCollateralMethod.SGI
                || contract.getCollateralMethod() == ContractCollateralMethod.CLAIM_TRANSFER) {
            return List.of(
                    new HomeHandoff("RETURN_GUARANTEE_JOIN", "전세보증금 반환보증 가입", "전입신고와 확정일자 처리 후 반환보증을 별도로 신청합니다."));
        }
        return List.of();
    }
}
