package com.homerun.domain.openbanking.dto.response;

import java.math.BigDecimal;

/**
 * 총 금융자산을 이루는 계좌 한 건의 잔액.
 *
 * <p>{@code totalAccountBalance} 는 합계 하나뿐이라 "어느 계좌가 얼마"를 알 수 없다. 자산확인
 * 화면이 합을 계좌별로 풀어 보여줄 수 있게 잔액 조회에 성공한 계좌를 그대로 실어 준다(실패한
 * 계좌는 합에도 안 들어가므로 여기에도 없다).
 */
public record AccountBalanceBreakdownResponse(
        String bankName,
        String accountNumberMasked,
        String productName,
        String accountType,
        BigDecimal balanceAmount,
        BigDecimal availableAmount) {}
