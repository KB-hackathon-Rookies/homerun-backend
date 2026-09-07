package com.homerun.domain.settlement.service;

import com.homerun.domain.contract.entity.LeaseContract;
import com.homerun.domain.contract.repository.LeaseContractRepository;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.settlement.dto.response.DashboardItemResponse;
import com.homerun.domain.settlement.dto.response.SettlementDashboardResponse;
import com.homerun.domain.settlement.entity.LoanAccount;
import com.homerun.domain.settlement.repository.LoanAccountRepository;
import com.homerun.domain.settlement.repository.ReturnGuaranteeEnrollmentRepository;
import com.homerun.domain.settlement.type.DashboardItemStatus;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈 정착 대시보드(FR-HD-01). 확인 가능한 항목만 DONE/PENDING 으로, 완료 여부를 저장하지 않는
 * 항목은 NOT_TRACKED 로 정직하게 표시한다 -- 근거 없이 미완료로 단정하지 않는다.
 *
 * <p>진행률은 추적 가능한 항목(DONE·PENDING)만 분모로 쓴다. NOT_TRACKED 는 진행률을 왜곡하지
 * 않게 뺀다.
 */
@Service
public class SettlementDashboardService {

    private final PlanRepository plans;
    private final LeaseContractRepository contracts;
    private final LoanAccountRepository loans;
    private final ReturnGuaranteeStatusResolver returnGuaranteeResolver;
    private final ReturnGuaranteeEnrollmentRepository enrollments;
    private final Clock clock;

    public SettlementDashboardService(
            PlanRepository plans,
            LeaseContractRepository contracts,
            LoanAccountRepository loans,
            ReturnGuaranteeStatusResolver returnGuaranteeResolver,
            ReturnGuaranteeEnrollmentRepository enrollments,
            Clock clock) {
        this.plans = plans;
        this.contracts = contracts;
        this.loans = loans;
        this.returnGuaranteeResolver = returnGuaranteeResolver;
        this.enrollments = enrollments;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SettlementDashboardResponse forPlan(Long memberId, Long planId) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);

        LeaseContract contract = contracts.findByPlanId(planId).orElse(null);
        LoanAccount loan = loans.findByPlanId(planId).orElse(null);

        List<DashboardItemResponse> items = new ArrayList<>();
        items.add(item(
                "LOAN_EXECUTED", "전세대출 실행", loan != null ? DashboardItemStatus.DONE : DashboardItemStatus.PENDING));
        items.add(item("MOVE_IN_REPORT", "전입신고", done(contract != null && contract.getMoveInReportAt() != null)));
        items.add(item("CONFIRMED_DATE", "확정일자", done(contract != null && contract.getConfirmedDateAt() != null)));
        items.add(item("RETURN_GUARANTEE", "반환보증", returnGuaranteeStatus(planId)));
        // 보증료 지원·연말정산은 완료 여부를 저장하지 않아 추적할 수 없다.
        items.add(item("GUARANTEE_FEE_SUPPORT", "보증료 지원 신청", DashboardItemStatus.NOT_TRACKED));
        items.add(item("YEAR_END_TAX", "연말정산 소득공제", DashboardItemStatus.NOT_TRACKED));

        int tracked = (int) items.stream()
                .filter(i -> i.status() != DashboardItemStatus.NOT_TRACKED)
                .count();
        int done = (int) items.stream()
                .filter(i -> i.status() == DashboardItemStatus.DONE)
                .count();
        int progress = tracked == 0 ? 0 : Math.round(done * 100f / tracked);

        return new SettlementDashboardResponse(daysSinceIndependence(contract), items, progress);
    }

    /**
     * HUG 담보이거나 반환보증에 가입했으면 완료다(resolver). 가입 기록이 있는데 아직 가입 전이면
     * 해야 할 일(PENDING), 기록도 없고 HUG 도 아니면 추적할 수 없다(NOT_TRACKED) -- 근거 없이
     * 미완료로 단정하지 않는다.
     */
    private DashboardItemStatus returnGuaranteeStatus(Long planId) {
        if (returnGuaranteeResolver.hasReturnGuarantee(planId)) {
            return DashboardItemStatus.DONE;
        }
        return enrollments.findByPlanId(planId).isPresent()
                ? DashboardItemStatus.PENDING
                : DashboardItemStatus.NOT_TRACKED;
    }

    /** 독립(실제 전입) 후 경과일. 아직 전입 전이면 null -- 예정일로 지어내지 않는다. */
    private Long daysSinceIndependence(LeaseContract contract) {
        if (contract == null || contract.getMoveInReportAt() == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(contract.getMoveInReportAt(), LocalDate.now(clock));
    }

    private DashboardItemStatus done(boolean condition) {
        return condition ? DashboardItemStatus.DONE : DashboardItemStatus.PENDING;
    }

    private DashboardItemResponse item(String code, String label, DashboardItemStatus status) {
        return new DashboardItemResponse(code, label, status);
    }
}
