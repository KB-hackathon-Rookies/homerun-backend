package com.homerun.domain.rent.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

/**
 * 월세대출 견적 입력.
 *
 * @param deposit 보증금(원)
 * @param monthlyRent 월세(원)
 * @param termMonths 대출 기간(개월)
 * @param preferential 주거안정 우대형 대상인가. 사회초년생은 대체로 해당된다
 */
@Schema(description = "월세대출 견적 입력")
public record RentLoanRequest(
        @Min(value = 0, message = "보증금은 0원 이상이어야 한다") long deposit,
        @Min(value = 0, message = "월세는 0원 이상이어야 한다") long monthlyRent,
        @Min(value = 1, message = "대출 기간은 1개월 이상이어야 한다") int termMonths,
        boolean preferential) {}
