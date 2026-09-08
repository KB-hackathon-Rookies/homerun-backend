package com.homerun.domain.contract.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;

/** 잔금 지급과 전입신고를 실제로 마친 날짜. */
public record ContractExecutionCompletionRequest(
        @NotNull @PastOrPresent LocalDate balancePaidAt,
        @NotNull @PastOrPresent LocalDate moveInReportAt) {}
