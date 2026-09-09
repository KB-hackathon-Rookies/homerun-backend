package com.homerun.domain.openbanking.dto.response;

import java.math.BigDecimal;

/**
 * 월 평균 소득을 이루는 한 달치 급여.
 *
 * <p>{@code averageMonthlyNetIncome} 는 평균 하나뿐이라 어느 달을 몇 번 잡아 나눈 값인지 보이지
 * 않는다. "최근 3개월 급여의 평균"이라는 말이 성립하도록 집계에 잡힌 달을 그대로 실어 준다.
 *
 * @param month {@code YYYY-MM}
 * @param amount 그 달에 급여로 잡힌 입금 합계
 */
public record MonthlyIncomeResponse(String month, BigDecimal amount) {}
