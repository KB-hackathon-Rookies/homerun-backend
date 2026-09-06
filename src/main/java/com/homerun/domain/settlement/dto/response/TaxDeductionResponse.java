package com.homerun.domain.settlement.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 주택임차차입금 원리금상환액 소득공제 예상(BR-29, FR-H8-01).
 *
 * <p>연상환액을 계산할 수 없으면(원리금균등인데 상환액 미입력) {@code annualRepayment} 가 null 이고
 * 공제·환급도 null 이다 — 모르는 것을 0으로 채워 공제 없음으로 단정하지 않는다.
 *
 * @param annualRepayment 공제 대상 연 상환액(원). 만기일시상환은 연 이자
 * @param deductionAmount 소득공제액(원) = min(min(연상환액, 상한) × 공제율, 한도)
 * @param estimatedRefund 예상 환급액(원) = 공제액 × 간이세율. 실제는 과세표준별
 * @param simplifiedRate 환급 계산에 쓴 간이세율이 가정값(REVIEW)인가
 */
@Schema(description = "주택임차차입금 소득공제 예상(BR-29)")
public record TaxDeductionResponse(
        Long annualRepayment, Long deductionAmount, Long estimatedRefund, boolean simplifiedRate) {}
