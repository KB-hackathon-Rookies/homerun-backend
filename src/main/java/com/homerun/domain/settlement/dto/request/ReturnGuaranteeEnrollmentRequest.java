package com.homerun.domain.settlement.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 반환보증 가입 상태 저장(FR-H1-03). 계획당 1건이라 다시 저장하면 덮어쓴다.
 *
 * @param enrolled 반환보증에 가입했는가
 * @param feePaid 보증료를 납부했는가
 * @param enrolledAt 가입일
 */
@Schema(description = "반환보증 가입 상태(FR-H1-03)")
public record ReturnGuaranteeEnrollmentRequest(boolean enrolled, boolean feePaid, LocalDate enrolledAt) {}
