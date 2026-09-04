package com.homerun.domain.contract.service;

import com.homerun.domain.contract.entity.LeaseContract;
import com.homerun.domain.dashboard.entity.Deadline;
import com.homerun.domain.dashboard.repository.DeadlineRepository;
import com.homerun.domain.dashboard.type.DeadlineType;
import com.homerun.domain.fact.service.FactRegistry;
import com.homerun.domain.plan.entity.StepTask;
import com.homerun.domain.plan.repository.StepTaskRepository;
import com.homerun.domain.plan.type.StepTaskTemplate;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 계약 날짜에서 마감을 역산한다(SEQ-01-04, SEQ-01-06).
 *
 * <p>계획을 만들 때 잡는 마감은 희망 이사일 기준의 어림값이다. 계약이 맺어져 잔금일이
 * 정해지면 그 날짜로 다시 잡아야 하는데, 지금까지는 그 갱신이 없어 옛 값이 그대로 남았다.
 *
 * <p>기한 종류를 섞지 않는다(SEQ-01-06). 전입신고·확정일자는 놓치면 대항력에 공백이
 * 생기는 {@code LEGAL} 이고, 은행 상담 착수일은 심사가 2~3주 걸린다는 경험치라
 * {@code RECOMMENDED} 다. 둘을 같은 색으로 보여 주면 사용자가 진짜 마감을 못 알아본다.
 *
 * <p>기준 날짜를 모르면 그 마감은 만들지 않는다. 지어낸 날짜로 재촉하면 사용자가 마감
 * 자체를 안 믿게 된다(NFR-01-06).
 */
@Component
class ContractDeadlineWriter {

    /** 이 클래스가 만드는 마감. 다시 계산할 때 이 코드들만 지운다. */
    private static final Set<String> MANAGED_FACTS =
            Set.of("FCT-103", "FCT-104", "FCT-105", "FCT-106", "FCT-107", "FCT-109");

    private static final String LOAN_APPLICATION_FACT = "FCT-103";
    private static final String CONSULT_FACT = "FCT-104";
    private static final String DOCUMENT_FACT = "FCT-105";
    private static final String VERIFY_FACT = "FCT-106";
    private static final String SETTLEMENT_FACT = "FCT-107";
    private static final String GUARANTEE_FACT = "FCT-109";

    private final DeadlineRepository deadlines;
    private final StepTaskRepository tasks;
    private final FactRegistry facts;

    ContractDeadlineWriter(DeadlineRepository deadlines, StepTaskRepository tasks, FactRegistry facts) {
        this.deadlines = deadlines;
        this.tasks = tasks;
        this.facts = facts;
    }

    /**
     * 계약 날짜로 마감을 다시 만든다.
     *
     * <p>이전 것을 지우고 새로 쓴다. 잔금일을 미루면 옛 마감이 남아 이미 지난 날짜로
     * 재촉하게 된다. 사람이 직접 넣은 마감은 {@code factCode} 가 없어 지워지지 않는다.
     */
    void rewrite(Long planId, LeaseContract contract) {
        Map<String, Long> taskIds = tasks.findAllByPlanId(planId).stream()
                .collect(Collectors.toMap(StepTask::getTaskCode, StepTask::getId, (first, second) -> first));

        deadlines.deleteByPlanIdAndFactCodeIn(planId, MANAGED_FACTS);
        List<Deadline> rewritten = build(planId, contract, taskIds);
        if (!rewritten.isEmpty()) {
            deadlines.saveAll(rewritten);
        }
    }

    private List<Deadline> build(Long planId, LeaseContract contract, Map<String, Long> taskIds) {
        List<Deadline> built = new ArrayList<>();
        LocalDate balanceDate = contract.getBalanceDate();
        LocalDate contractDate = contract.getContractDate();

        // 잔금일 당일에 전입신고와 확정일자를 함께 끝내야 한다. 하루만 밀려도 그 사이
        // 설정된 근저당이 임차인보다 앞선다(FCT-108).
        add(
                built,
                planId,
                taskIds,
                StepTaskTemplate.MOVE_IN_REPORT,
                DeadlineType.RECOMMENDED,
                "전입신고 권장일(잔금일 당일)",
                "BALANCE_DATE",
                balanceDate,
                0,
                SETTLEMENT_FACT);
        add(
                built,
                planId,
                taskIds,
                StepTaskTemplate.FIXED_DATE,
                DeadlineType.RECOMMENDED,
                "확정일자 권장일(잔금일 당일)",
                "BALANCE_DATE",
                balanceDate,
                0,
                SETTLEMENT_FACT);

        int consultOffset = days(CONSULT_FACT);
        LocalDate consultStart = balanceDate == null ? null : balanceDate.minusDays(consultOffset);
        add(
                built,
                planId,
                taskIds,
                StepTaskTemplate.BANK_CONSULTATION,
                DeadlineType.RECOMMENDED,
                "은행 상담 시작 권장일",
                "BALANCE_DATE",
                balanceDate,
                -consultOffset,
                CONSULT_FACT);

        // 서류는 상담일 기준이다. 발급이 1개월 유효라 너무 일찍 떼면 다시 떼야 한다.
        add(
                built,
                planId,
                taskIds,
                StepTaskTemplate.DOCUMENT_CHECK,
                DeadlineType.RECOMMENDED,
                "서류 발급 시작 권장일",
                "BANK_CONSULT_DATE",
                consultStart,
                -days(DOCUMENT_FACT),
                DOCUMENT_FACT);

        add(
                built,
                planId,
                taskIds,
                StepTaskTemplate.REGISTER_CHECK,
                DeadlineType.RECOMMENDED,
                "계약 전 검증 권장일",
                "CONTRACT_DATE",
                contractDate,
                -days(VERIFY_FACT),
                VERIFY_FACT);

        addGuaranteeDeadline(built, planId, taskIds, contract, balanceDate);
        addLoanApplicationDeadline(built, planId, taskIds, contract);

        return built;
    }

    /**
     * 대출 신청 법정 마감(FCT-103, LEGAL). 잔금일·전입일 중 빠른 날부터 3개월 — 놓치면
     * 정책자금을 영구히 못 쓴다. #67: {@code APPLY_LOAN} 할 일에 붙인다 — 은행 사전상담
     * (RECOMMENDED, D-21)에 붙이면 대시보드가 더 가까운 날짜를 골라 이 절대 마감이 가려진다.
     *
     * <p>둘 다 아직 모르면 만들지 않는다 — 지어낸 날짜로 재촉하지 않는다(NFR-01-06).
     */
    private void addLoanApplicationDeadline(
            List<Deadline> built, Long planId, Map<String, Long> taskIds, LeaseContract contract) {
        LocalDate balanceDate = contract.getBalanceDate();
        LocalDate moveIn = contract.getMoveInReportAt();
        LocalDate earliest = earliestKnown(balanceDate, moveIn);
        if (earliest == null) {
            return;
        }
        String baseEvent = balanceDate != null && balanceDate.equals(earliest) ? "BALANCE_DATE" : "MOVE_IN_REPORT_DATE";
        LocalDate legalDeadline = earliest.plusMonths(months(LOAN_APPLICATION_FACT));
        int offsetDays = (int) ChronoUnit.DAYS.between(earliest, legalDeadline);

        add(
                built,
                planId,
                taskIds,
                StepTaskTemplate.APPLY_LOAN,
                DeadlineType.LEGAL,
                "대출 신청 법정 마감(잔금일·전입일 중 빠른 날 + 3개월)",
                baseEvent,
                earliest,
                offsetDays,
                LOAN_APPLICATION_FACT);
    }

    private LocalDate earliestKnown(LocalDate a, LocalDate b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isBefore(b) ? a : b;
    }

    /**
     * 반환보증 가입 마감(FCT-109).
     *
     * <p>"전입·확정일자 완료 후 즉시"라 <b>둘 다</b> 끝나야 한다. 한쪽만 보고 계산하면 아직
     * 가입할 수 없는 사람에게 이미 지났다고 표시된다. 둘 다 끝났으면 <b>늦은 쪽</b>이 기준이다.
     *
     * <p>아직이면 잔금일로 잡되 예정 기반임을 기준 사건과 문구로 구분한다. 완료 기반과 예정
     * 기반을 같은 이름으로 내보내면 어느 쪽인지 알 수 없다.
     */
    private void addGuaranteeDeadline(
            List<Deadline> built,
            Long planId,
            Map<String, Long> taskIds,
            LeaseContract contract,
            LocalDate balanceDate) {
        LocalDate moveIn = contract.getMoveInReportAt();
        LocalDate confirmed = contract.getConfirmedDateAt();

        if (moveIn != null && confirmed != null) {
            LocalDate later = moveIn.isAfter(confirmed) ? moveIn : confirmed;
            add(
                    built,
                    planId,
                    taskIds,
                    StepTaskTemplate.GUARANTEE_CHECK,
                    DeadlineType.RECOMMENDED,
                    "반환보증 가입 권장일",
                    "PROTECTION_COMPLETED",
                    later,
                    0,
                    GUARANTEE_FACT);
            return;
        }
        add(
                built,
                planId,
                taskIds,
                StepTaskTemplate.GUARANTEE_CHECK,
                DeadlineType.RECOMMENDED,
                "반환보증 가입 권장일(잔금일 기준 예정)",
                "BALANCE_DATE",
                balanceDate,
                0,
                GUARANTEE_FACT);
    }

    /** 기준 날짜나 할 일이 없으면 만들지 않는다. */
    private void add(
            List<Deadline> built,
            Long planId,
            Map<String, Long> taskIds,
            StepTaskTemplate template,
            DeadlineType type,
            String label,
            String baseEvent,
            LocalDate baseDate,
            int offsetDays,
            String factCode) {
        Long taskId = taskIds.get(template.code());
        if (taskId == null || baseDate == null) {
            return;
        }
        built.add(Deadline.derived(planId, taskId, type, label, baseEvent, baseDate, offsetDays, factCode));
    }

    /** 며칠인지는 레지스트리에서 읽는다. 상수로 박으면 기준이 바뀔 때 아무도 못 찾는다. */
    private int days(String factCode) {
        return facts.require(factCode).requireNumber().intValueExact();
    }

    /** FCT-103 은 단위가 '개월'이다 — days() 와 다른 단위라 따로 둔다. */
    private int months(String factCode) {
        return facts.require(factCode).requireNumber().intValueExact();
    }
}
