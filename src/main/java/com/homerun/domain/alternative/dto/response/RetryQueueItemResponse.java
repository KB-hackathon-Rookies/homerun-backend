package com.homerun.domain.alternative.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "재도전 큐 항목 하나(ALT-01-04). 나이 하한을 채우면 다시 도전할 수 있는 정책이다")
public record RetryQueueItemResponse(
        String policyCode,
        String policyName,

        @Schema(description = "지금 걸려 있는 조건의 라벨(기준 fact의 item)")
        String conditionLabel,

        @Schema(description = "이 날짜부터 나이 조건을 충족한다") LocalDate eligibleFrom,
        @Schema(description = "eligibleFrom까지 남은 일수") long daysRemaining,
        String sourceUrl) {}
