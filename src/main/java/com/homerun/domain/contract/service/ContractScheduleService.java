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
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractScheduleService {

    private final PlanRepository plans;
    private final LeaseContractRepository contracts;
    private final Clock clock;

    public ContractScheduleService(PlanRepository plans, LeaseContractRepository contracts, Clock clock) {
        this.plans = plans;
        this.contracts = contracts;
        this.clock = clock;
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
            return response(contract, applicationDeadline(contract), false, List.of(), warnings);
        }

        List<Milestone> milestones = new ArrayList<>();
        if (contract.getLoanProductKind() == LoanProductKind.FUND_YOUTH) {
            milestones.add(milestone(
                    "COMPANY_DOCUMENTS",
                    "재직·소득 서류 준비",
                    balance.minusDays(30),
                    "청년 버팀목 신청에 필요한 재직·소득 서류를 먼저 확인합니다.",
                    false));
        }

        LocalDate reservation = balance.minusDays(21);
        boolean bankVisit = contract.getApplicationMethod() == ApplicationMethod.BANK_VISIT;
        milestones.add(milestone(
                "BANK_RESERVATION",
                bankVisit ? "은행 방문 예약" : "온라인 신청 환경 확인",
                bankVisit ? nextWeekday(reservation) : reservation,
                bankVisit ? "주말 예약일은 다음 영업일로 조정합니다." : "인증서와 제출 파일 형식을 확인합니다.",
                false));
        milestones.add(milestone(
                "DOCUMENT_ISSUE", "대출 제출 서류 일괄 발급", balance.minusDays(14), "제출처의 발급일 제한 안에서 한 번에 준비합니다.", false));

        if (requiresTenantHouseholdConfirmation(contract)) {
            milestones.add(milestone(
                    "TENANT_HOUSEHOLD_CONFIRMATION",
                    "전입세대 열람·확인서 발급",
                    balance.minusDays(12),
                    "HUG 보증의 단독·다가구 주택은 선순위 임차보증금을 다시 확인합니다.",
                    true));
        }

        milestones.add(milestone(
                "LOAN_GUARANTEE_APPLICATION", "대출·보증 신청", balance.minusDays(10), "선택한 은행·상품·담보 방식으로 신청합니다.", true));
        milestones.add(milestone("LOAN_REVIEW", "대출 심사 상태 확인", balance.minusDays(3), "추가 서류 요청과 거절 가능성을 확인합니다.", true));
        milestones.add(
                milestone("REGISTRY_RECHECK", "잔금 직전 등기부 재대조", balance, "송금 전에 계약 당시 등기부와 최신 등기부를 비교합니다.", true));
        milestones.add(milestone(
                "BALANCE_AND_MOVE_IN", "잔금 지급·전입신고", balance, "등기부가 안전할 때만 잔금을 지급하고 같은 날 전입신고를 진행합니다.", true));

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
        LocalDate applicationDeadline = applicationDeadline(contract);
        boolean compressed = isCompressed(balance);
        if (compressed) {
            warnings.add("잔금일까지 30일이 채 남지 않아 서류·예약·심사 일정을 압축해서 진행해야 합니다.");
        }
        return response(contract, applicationDeadline, compressed, milestones, warnings);
    }

    private Milestone milestone(String code, String label, LocalDate dueDate, String reason, boolean blocking) {
        return new Milestone(code, label, dueDate, reason, blocking);
    }

    private boolean requiresTenantHouseholdConfirmation(LeaseContract contract) {
        return contract.getCollateralMethod() == ContractCollateralMethod.HUG_SAFE_JEONSE
                && (contract.getHouseType() == HouseType.DETACHED || contract.getHouseType() == HouseType.MULTI_FAMILY);
    }

    private LocalDate nextWeekday(LocalDate date) {
        LocalDate adjusted = date;
        while (adjusted.getDayOfWeek() == DayOfWeek.SATURDAY || adjusted.getDayOfWeek() == DayOfWeek.SUNDAY) {
            adjusted = adjusted.plusDays(1);
        }
        return adjusted;
    }

    private LocalDate applicationDeadline(LeaseContract contract) {
        LocalDate balance = contract.getBalanceDate();
        LocalDate moveIn = contract.getMoveInDate();
        if (balance == null) {
            return moveIn == null ? null : moveIn.plusMonths(3);
        }
        if (moveIn == null || balance.isBefore(moveIn)) {
            return balance.plusMonths(3);
        }
        return moveIn.plusMonths(3);
    }

    private boolean isCompressed(LocalDate balance) {
        long days = ChronoUnit.DAYS.between(LocalDate.now(clock), balance);
        return days >= 0 && days < 30;
    }

    private ContractScheduleResponse response(
            LeaseContract contract,
            LocalDate applicationDeadline,
            boolean compressed,
            List<Milestone> milestones,
            List<String> warnings) {
        return new ContractScheduleResponse(
                contract.getBalanceDate(),
                applicationDeadline,
                contract.getLoanProductKind(),
                contract.getCollateralMethod(),
                contract.getApplicationMethod(),
                contract.getHouseType(),
                compressed,
                milestones,
                warnings);
    }
}
