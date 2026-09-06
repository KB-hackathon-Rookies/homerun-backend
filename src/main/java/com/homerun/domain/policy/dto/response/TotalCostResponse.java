package com.homerun.domain.policy.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 1년차 총비용(BR-21). 금리만 비교하면 순위가 뒤집히므로 카드·비교표는 이 값으로 정렬한다.
 *
 * <p>연간 실부담 = 대출금 × 금리 + 대출금 × 보증료율 + 인지세 고객부담분(1회). 소득공제 절감액은
 * 전세대출에서 일반적으로 해당이 적고 입력이 필요해 여기서는 빼지 않는다 — 필요하면 별도로 뺀다.
 *
 * @param annualInterest 연 이자(만기일시상환 = 대출금 × 금리)
 * @param guaranteeFee 대출보증 보증료(연 1회분)
 * @param stampDuty 인지세 고객 부담분(1회)
 * @param totalYear1 1년차 합계
 * @param estimated 보증료율이 추정치(담보 미확정)인가
 */
@Schema(description = "1년차 총비용(BR-21)")
public record TotalCostResponse(
        long annualInterest, long guaranteeFee, long stampDuty, long totalYear1, boolean estimated) {}
