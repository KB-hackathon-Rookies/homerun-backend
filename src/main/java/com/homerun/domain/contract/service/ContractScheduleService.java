package com.homerun.domain.contract.service;

import com.homerun.domain.contract.dto.response.ContractScheduleResponse;
import com.homerun.domain.contract.dto.response.ContractScheduleResponse.Milestone;
import com.homerun.domain.contract.entity.LeaseContract;
import com.homerun.domain.contract.repository.LeaseContractRepository;
import com.homerun.domain.contract.type.ApplicationMethod;
import com.homerun.domain.contract.type.ContractCollateralMethod;
import com.homerun.domain.contract.type.LoanProductKind;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.HouseType;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractScheduleService {

    private final PlanRepository plans;
    private final LeaseContractRepository contracts;

    public ContractScheduleService(PlanRepository plans, LeaseContractRepository contracts) {
        this.plans = plans;
        this.contracts = contracts;
    }

    @Transactional(readOnly = true)
    public ContractScheduleResponse get(Long memberId, Long planId) {
        plans.findById(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND))
                .verifyOwner(memberId);
        LeaseContract contract =
                contracts.findByPlanId(planId).orElseThrow(() -> new BusinessException(ErrorCode.CONTRACT_NOT_FOUND));
        return build(contract);
    }

    ContractScheduleResponse build(LeaseContract contract) {
        LocalDate balance = contract.getBalanceDate();
        List<String> warnings = new ArrayList<>();
        if (balance == null) {
            warnings.add("잔금일을 입력하면 계약·대출 일정을 역산할 수 있습니다.");
            return response(contract, List.of(), warnings);
        }

        List<Milestone> milestones = new ArrayList<>();
        LoanProductKind product = contract.getLoanProductKind();
        int preparationDays = product == LoanProductKind.BANK ? 21 : 30;
        milestones.add(milestone(
                "LOAN_PREPARATION",
                "대출 심사 준비 시작",
                balance.minusDays(preparationDays),
                product == LoanProductKind.BANK ? "은행 자체대출은 은행 상담 결과를 기준으로 준비합니다." : "기금대출 심사와 거절 시 재시도 기간을 확보합니다.",
                false));
        milestones.add(milestone(
                "DOCUMENT_ISSUE", "대출 제출 서류 일괄 발급", balance.minusDays(14), "제출처가 요구하는 발급일 제한 안에서 준비합니다.", false));

        if (contract.getApplicationMethod() == ApplicationMethod.BANK_VISIT) {
            milestones.add(milestone(
                    "BANK_VISIT_RESERVATION",
                    "은행 방문 일정 확정",
                    balance.minusDays(15),
                    "방문 신청에 필요한 지점과 담당자를 확인합니다.",
                    false));
        } else if (contract.getApplicationMethod() == ApplicationMethod.ONLINE) {
            milestones.add(milestone(
                    "ONLINE_APPLICATION_CHECK",
                    "온라인 신청 환경 점검",
                    balance.minusDays(15),
                    "인증서와 제출 파일 형식을 미리 확인합니다.",
                    false));
        }

        if (contract.getHouseType() == HouseType.MULTI_FAMILY) {
            milestones.add(milestone(
                    "TENANT_PRIORITY_CHECK",
                    "전입세대와 선순위 보증금 재확인",
                    balance.minusDays(10),
                    "다가구는 다른 세입자의 보증금이 등기부에 모두 나타나지 않습니다.",
                    true));
        }

        milestones.add(milestone(
                "REGISTRY_RECHECK", "잔금 전 등기부 재대조", balance.minusDays(3), "계약 당시 등기부와 비교해 새 권리 제한을 확인합니다.", true));
        milestones.add(milestone("SETTLEMENT", "잔금·전입신고·확정일자 처리", balance, "같은 날 처리해 대항력 공백을 최소화합니다.", true));

        if (contract.getCollateralMethod() == ContractCollateralMethod.HUG_SAFE_JEONSE) {
            milestones.add(milestone(
                    "GUARANTEE_FEE_SUPPORT",
                    "보증료 지원 신청",
                    balance.plusDays(1),
                    "안심전세는 반환보증이 포함되므로 별도 가입 단계를 생략합니다.",
                    false));
        } else if (contract.getCollateralMethod() != null
                && contract.getCollateralMethod() != ContractCollateralMethod.NONE) {
            milestones.add(milestone(
                    "RETURN_GUARANTEE_JOIN",
                    "반환보증 별도 가입",
                    balance.plusDays(1),
                    "전입신고와 확정일자 처리 후 반환보증 가입을 진행합니다.",
                    false));
        }
        milestones.sort(java.util.Comparator.comparing(Milestone::dueDate).thenComparing(Milestone::code));
        return response(contract, milestones, warnings);
    }

    private Milestone milestone(String code, String label, LocalDate dueDate, String reason, boolean blocking) {
        return new Milestone(code, label, dueDate, reason, blocking);
    }

    private ContractScheduleResponse response(
            LeaseContract contract, List<Milestone> milestones, List<String> warnings) {
        return new ContractScheduleResponse(
                contract.getBalanceDate(),
                contract.getLoanProductKind(),
                contract.getCollateralMethod(),
                contract.getApplicationMethod(),
                contract.getHouseType(),
                milestones,
                warnings);
    }
}
