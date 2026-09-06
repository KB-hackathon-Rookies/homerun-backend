package com.homerun.domain.contract.service;

import com.homerun.domain.contract.dto.response.LienRepaymentResponse;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 질권 상환 자금 흐름을 계산·안내한다(FR-H10-02). 판정이 아니라 산술과 정해진 안내다.
 *
 * <p>전세대출에는 질권이 설정돼 있어 보증금은 임대인이 은행에 직접 송금한다. 은행 몫은 대출
 * 잔액이고 내 몫은 나머지다. 착오로 전액을 받으면 즉시 은행에 반환해야 한다.
 */
@Component
class LienRepaymentAdvisor {

    public LienRepaymentResponse guide(long deposit, long loanBalance) {
        // 은행 몫은 대출 잔액이지만 보증금을 넘을 수 없다.
        long toBank = Math.min(loanBalance, deposit);
        long toMe = deposit - toBank;
        return new LienRepaymentResponse(
                deposit,
                toBank,
                toMe,
                List.of(
                        "전세대출에는 질권이 설정돼 있어 보증금은 임대인이 은행에 직접 송금해요.",
                        "은행에 대출 잔액이 상환되고, 남는 돈만 내 계좌로 들어와요.",
                        "착오로 보증금 전액을 받으면 즉시 은행에 반환하세요 -- 안 그러면 상환 처리가 안 돼요.",
                        "이사 전에 은행에 상환 계좌와 정확한 금액을 확인하세요."));
    }
}
