package com.homerun.domain.contract.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 질권 상환 자금 흐름(FR-H10-02). 보증금 반환 시 은행 몫과 내 몫을 나눠 보여준다.
 *
 * @param deposit 보증금(원)
 * @param toBank 은행에 상환되는 금액(원) = min(대출 잔액, 보증금)
 * @param toMe 내가 받는 금액(원) = 보증금 − 은행 몫
 * @param notes 유의사항
 */
@Schema(description = "질권 상환 자금 흐름(FR-H10-02)")
public record LienRepaymentResponse(long deposit, long toBank, long toMe, List<String> notes) {

    public LienRepaymentResponse {
        notes = List.copyOf(notes);
    }
}
