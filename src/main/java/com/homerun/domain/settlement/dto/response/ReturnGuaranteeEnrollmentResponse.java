package com.homerun.domain.settlement.dto.response;

import com.homerun.domain.settlement.entity.ReturnGuaranteeEnrollment;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 반환보증 가입 상태(FR-H1-03).
 *
 * @param enrolled 가입 여부
 * @param feePaid 보증료 납부 여부
 * @param enrolledAt 가입일
 * @param feeSupportApplicable 보증료 지원(4-2) 신청이 가능한가 = 가입 ∧ 납부(FR-H2 순서 강제)
 */
@Schema(description = "반환보증 가입 상태(FR-H1-03)")
public record ReturnGuaranteeEnrollmentResponse(
        boolean enrolled, boolean feePaid, LocalDate enrolledAt, boolean feeSupportApplicable) {

    public static ReturnGuaranteeEnrollmentResponse from(ReturnGuaranteeEnrollment e) {
        return new ReturnGuaranteeEnrollmentResponse(
                e.isEnrolled(), e.isFeePaid(), e.getEnrolledAt(), e.isEnrolled() && e.isFeePaid());
    }
}
