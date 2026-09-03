package com.homerun.domain.contract.dto;

import com.homerun.domain.contract.type.ChecklistStatus;
import com.homerun.domain.contract.type.ContractStep;
import com.homerun.domain.contract.type.StepStatus;
import com.homerun.domain.plan.type.LeaseType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/** 계약 실행(PRP-02) 요청·응답. */
public final class ContractDtos {

    private ContractDtos() {}

    /**
     * 계약 정보 저장(PRP-02-04).
     *
     * <p>화면이 폼 전체를 보내므로 통째로 덮어쓴다. 날짜를 지우려면 null 로 보내면 된다.
     *
     * @param balanceDate 잔금 예정일
     * @param balancePaidAt 잔금을 실제로 지급한 날
     * @param bankConsultedAt 은행 사전상담을 받은 날(PRP-02-01)
     */
    @Schema(description = "계약 정보 저장")
    public record SaveRequest(
            Long propertyId,
            @NotNull LeaseType leaseType,
            @Min(value = 0, message = "보증금은 0원 이상이어야 한다") long deposit,
            @Min(value = 0, message = "월세는 0원 이상이어야 한다") long monthlyRent,
            @Min(value = 0, message = "관리비는 0원 이상이어야 한다") long maintenanceFee,
            Long downPayment,
            LocalDate contractDate,
            LocalDate balanceDate,
            LocalDate moveInDate,
            LocalDate confirmedDateAt,
            LocalDate moveInReportAt,
            LocalDate bankConsultedAt,
            LocalDate loanAppliedAt,
            LocalDate balancePaidAt,
            boolean electronic) {}

    /**
     * 계약 진행 단계 하나(PRP-02-04).
     *
     * @param doneAt 끝난 날. 아직이면 null
     */
    @Schema(description = "계약 진행 단계")
    public record StepView(ContractStep step, String label, StepStatus status, LocalDate doneAt) {}

    /**
     * 계약 진행상태(PRP-02-04).
     *
     * @param currentStep 지금 할 차례. 전부 끝났으면 null
     */
    @Schema(description = "계약 진행상태")
    public record ContractProgress(ContractStep currentStep, List<StepView> steps) {

        public ContractProgress {
            steps = List.copyOf(steps);
        }
    }

    /**
     * 은행 사전상담 안내(PRP-02-01).
     *
     * @param recommendedStartDate 상담을 시작하라고 권하는 날(FCT-104)
     * @param applyDeadline 대출 신청 마감(FCT-103). 넘기면 복구가 안 된다
     */
    @Schema(description = "은행 사전상담 안내")
    public record PreConsultGuide(
            boolean consulted,
            LocalDate recommendedStartDate,
            LocalDate applyDeadline,
            String summary,
            String action,
            List<String> factCodes) {

        public PreConsultGuide {
            factCodes = List.copyOf(factCodes);
        }
    }

    /**
     * 계약 전 체크리스트 항목 하나(PRP-02-02).
     *
     * @param action 아직 못 한 일. 끝났으면 null
     */
    @Schema(description = "계약 전 체크리스트 항목")
    public record ChecklistItem(String code, String label, ChecklistStatus status, String summary, String action) {}

    /**
     * 권장 특약 하나(PRP-02-03).
     *
     * <p>법에 정해진 필수 조항이 아니다. 임대인이 거절할 수 있고, 그것만으로 계약이 무효가
     * 되지도 않는다. "권장"으로만 표기한다(FCT-116 주석).
     *
     * @param applicable 이 계약에 해당하는가. 대출을 안 쓰면 대출 관련 특약은 빠진다
     */
    @Schema(description = "권장 특약")
    public record SpecialTermAdvice(
            String termCode, String label, String text, String reason, String factCode, boolean applicable) {}

    /**
     * 잔금·전입 안내(PRP-02-05, PRP-02-06).
     *
     * @param protectionEffectiveAt 대항력이 생기는 날(FCT-108). 전입신고 다음날이다
     * @param gapDays 잔금일과 대항력 발생일 사이에 비는 날 수. 이 사이 근저당이 잡히면 밀린다
     * @param taxCreditNote 전입신고가 월세 세액공제에 미치는 영향(FCT-050)
     */
    @Schema(description = "잔금·전입 안내")
    public record SettlementGuide(
            LocalDate balanceDate,
            LocalDate moveInReportAt,
            LocalDate confirmedDateAt,
            boolean sameDay,
            LocalDate protectionEffectiveAt,
            int gapDays,
            String summary,
            String action,
            String taxCreditNote,
            List<String> factCodes) {

        public SettlementGuide {
            factCodes = List.copyOf(factCodes);
        }
    }

    /**
     * 계약 실행 안내 전체(PRP-02).
     *
     * @param riskCheckRequired 매물 검증이 필요한 계약인가(PRP-02-07)
     */
    @Schema(description = "계약 실행 안내")
    public record ContractGuide(
            Long contractId,
            LeaseType leaseType,
            boolean riskCheckRequired,
            ContractProgress progress,
            PreConsultGuide preConsult,
            List<ChecklistItem> checklist,
            List<SpecialTermAdvice> specialTerms,
            SettlementGuide settlement) {

        public ContractGuide {
            checklist = List.copyOf(checklist);
            specialTerms = List.copyOf(specialTerms);
        }
    }
}
