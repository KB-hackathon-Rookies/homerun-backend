package com.homerun.domain.policy.dto.response;

import com.homerun.domain.fact.model.Fact;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** 은행 한 곳의 전세대출 공시 평균금리(POL-03-07). */
@Schema(description = "은행별 전세대출 공시 평균금리")
public record BankLoanRateResponse(
        String factCode,
        @Schema(description = "은행 이름. 팩트 item 에서 상품명을 뗀 값") String bankName,
        @Schema(description = "연 이율(%)") BigDecimal rate,
        @Schema(description = "원문 표현. 화면에 그대로 쓸 수 있다") String text,
        @Schema(description = "주간 공시라 바뀔 수 있다는 표시") boolean provisional,
        String sourceUrl) {

    private static final String ITEM_SUFFIX = " 전세대출 평균금리";

    public static BankLoanRateResponse from(Fact fact) {
        return new BankLoanRateResponse(
                fact.code(),
                bankNameOf(fact.item()),
                fact.requireNumber(),
                fact.text(),
                fact.provisional(),
                fact.sourceUrl());
    }

    /** 항목 이름이 "KB국민은행 전세대출 평균금리" 꼴이라 접미사만 뗀다. 형식이 달라지면
     * 자르지 않고 항목 이름을 그대로 쓴다 — 억지로 잘라 이상한 이름을 만들지 않는다. */
    private static String bankNameOf(String item) {
        return item != null && item.endsWith(ITEM_SUFFIX)
                ? item.substring(0, item.length() - ITEM_SUFFIX.length())
                : item;
    }
}
