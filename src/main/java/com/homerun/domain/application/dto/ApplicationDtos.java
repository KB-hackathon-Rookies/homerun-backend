package com.homerun.domain.application.dto;

import com.homerun.domain.application.type.ApplicationStatus;
import com.homerun.domain.application.type.RejectStage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 신청 실행 요청·응답. */
public final class ApplicationDtos {

    private ApplicationDtos() {}

    /** 신청 건 생성(APP-01-02). */
    @Schema(description = "신청 건 생성")
    public record CreateRequest(@NotNull Long policyId, String channel) {}

    /**
     * 진행 상태·결과 기록(APP-01-05, APP-01-06, APP-01-07).
     *
     * @param status 옮길 상태
     * @param rejectStage 거절 시 어디서 막혔는지. REJECTED 일 때만 의미가 있다
     * @param rejectReasonCode 거절 사유 코드
     * @param rejectionEvidence 거절 안내문·보완 서류 등 사용자가 다시 확인할 근거
     * @param approvedAmount 승인 금액(원)
     * @param approvedRate 승인 금리(%)
     */
    @Schema(description = "신청 진행 상태·결과 기록")
    public record UpdateRequest(
            @NotNull ApplicationStatus status,
            RejectStage rejectStage,
            String rejectReasonCode,
            Long approvedAmount,
            BigDecimal approvedRate,
            String rejectionEvidence) {

        public UpdateRequest(
                ApplicationStatus status,
                RejectStage rejectStage,
                String rejectReasonCode,
                Long approvedAmount,
                BigDecimal approvedRate) {
            this(status, rejectStage, rejectReasonCode, approvedAmount, approvedRate, null);
        }
    }

    /**
     * 신청 한 건.
     *
     * @param nextAction 거절 시 사용자가 다음에 할 일. 막힌 단계마다 다르다
     */
    @Schema(description = "신청 건")
    public record ApplicationView(
            Long id,
            Long planId,
            Long policyId,
            String channel,
            ApplicationStatus status,
            Instant submittedAt,
            Instant resultAt,
            RejectStage rejectStage,
            String rejectReasonCode,
            String rejectionEvidence,
            Long approvedAmount,
            BigDecimal approvedRate,
            String nextAction) {

        public ApplicationView(
                Long id,
                Long planId,
                Long policyId,
                String channel,
                ApplicationStatus status,
                Instant submittedAt,
                Instant resultAt,
                RejectStage rejectStage,
                String rejectReasonCode,
                Long approvedAmount,
                BigDecimal approvedRate,
                String nextAction) {
            this(
                    id,
                    planId,
                    policyId,
                    channel,
                    status,
                    submittedAt,
                    resultAt,
                    rejectStage,
                    rejectReasonCode,
                    null,
                    approvedAmount,
                    approvedRate,
                    nextAction);
        }
    }

    /** 신청 목록. */
    @Schema(description = "신청 목록")
    public record ApplicationList(List<ApplicationView> applications) {

        public ApplicationList {
            applications = List.copyOf(applications);
        }
    }
}
