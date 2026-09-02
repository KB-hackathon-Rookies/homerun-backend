package com.homerun.domain.dashboard.dto.response;

import com.homerun.domain.dashboard.entity.Deadline;
import com.homerun.domain.dashboard.type.DashboardTaskPriorityReason;
import com.homerun.domain.dashboard.type.DeadlineType;
import com.homerun.domain.plan.entity.PlanStep;
import com.homerun.domain.plan.type.PlanStepStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Schema(description = "대시보드 할 일")
public record DashboardTaskResponse(
        @Schema(description = "단계 코드", example = "FIRST_DIAGNOSIS")
        String stepCode,

        @Schema(description = "단계 이름", example = "독립 가능성 진단 완료")
        String stepName,

        @Schema(description = "단계 상태") PlanStepStatus status,
        @Schema(description = "단계 순서", example = "2") int sequence,
        @Schema(description = "되돌리기 어려운 작업 여부") boolean irreversible,

        @Schema(description = "가장 가까운 마감 이름", nullable = true)
        String deadlineLabel,

        @Schema(description = "마감 유형", nullable = true) DeadlineType deadlineType,
        @Schema(description = "가장 가까운 마감일", nullable = true) LocalDate dueDate,

        @Schema(description = "마감일까지 남은 일수. 음수이면 기한 초과", nullable = true)
        Long daysUntilDue,

        @Schema(description = "할 일이 상단에 노출된 이유") DashboardTaskPriorityReason priorityReason) {

    public static DashboardTaskResponse from(PlanStep step, Deadline deadline, LocalDate today) {
        LocalDate dueDate = deadline == null ? null : deadline.getDueDate();
        Long daysUntilDue = dueDate == null ? null : ChronoUnit.DAYS.between(today, dueDate);
        return new DashboardTaskResponse(
                step.getStepCode(),
                step.getStepName(),
                step.getStatus(),
                step.getSequence(),
                step.isIrreversible(),
                deadline == null ? null : deadline.getLabel(),
                deadline == null ? null : deadline.getType(),
                dueDate,
                daysUntilDue,
                priorityReason(step, daysUntilDue));
    }

    private static DashboardTaskPriorityReason priorityReason(PlanStep step, Long daysUntilDue) {
        if (daysUntilDue != null && daysUntilDue < 0) {
            return DashboardTaskPriorityReason.OVERDUE;
        }
        if (daysUntilDue != null && step.isIrreversible()) {
            return DashboardTaskPriorityReason.IRREVERSIBLE_DEADLINE;
        }
        if (daysUntilDue != null) {
            return DashboardTaskPriorityReason.DEADLINE_APPROACHING;
        }
        if (step.isIrreversible()) {
            return DashboardTaskPriorityReason.IRREVERSIBLE;
        }
        return switch (step.getStatus()) {
            case RECALC_REQUIRED -> DashboardTaskPriorityReason.RECALCULATION_REQUIRED;
            case DOING -> DashboardTaskPriorityReason.IN_PROGRESS;
            default -> DashboardTaskPriorityReason.READY;
        };
    }
}
