package com.homerun.domain.settlement.service;

import com.homerun.domain.property.type.ConsultedLoanProduct;
import com.homerun.domain.settlement.dto.response.RateCutRightResponse;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 금리인하요구권 안내를 상품별로 분기한다(FR-H6-01·BR-32). 판정이 아니라 정해진 안내라 상품만 받는다.
 *
 * <p>핵심은 버팀목(기금) 사용자에게 금리인하요구권을 노출하지 않는 것이다 — 기금대출은 대상이
 * 아니라 노출하면 잘못된 안내가 된다.
 */
@Component
class RateCutRightAdvisor {

    private static final List<String> APPLY_REASONS = List.of("취업(무직 → 재직)", "소득 증가", "신용점수 상승", "부채 감소");
    private static final List<String> APPLY_CHANNELS = List.of("영업점 방문", "인터넷뱅킹", "은행 앱");

    public RateCutRightResponse guide(ConsultedLoanProduct product) {
        if (product == null) {
            return needsProduct();
        }
        return switch (product) {
            case YOUTH_BEOTIMMOK, GENERAL_BEOTIMMOK ->
                new RateCutRightResponse(
                        false,
                        "버팀목(기금)대출은 금리인하요구권 대상이 아니에요.",
                        List.of(),
                        List.of(),
                        "대신 연장 시점에 우대금리를 추가로 채울 수 있는지 확인하세요(상한 0.5%p).");
            case BANK_LOAN ->
                new RateCutRightResponse(true, "은행 자체 대출은 금리인하요구권을 신청할 수 있어요.", APPLY_REASONS, APPLY_CHANNELS, null);
            case UNKNOWN -> needsProduct();
        };
    }

    private RateCutRightResponse needsProduct() {
        return new RateCutRightResponse(
                false, "대출 상품을 확인해야 안내할 수 있어요.", List.of(), List.of(), "실행한 대출 상품을 등록하면 대상 여부를 알려드려요.");
    }
}
