package com.homerun.domain.settlement.service;

import com.homerun.domain.property.type.ConsultedLoanProduct;
import com.homerun.domain.settlement.dto.response.PostAssetReviewResponse;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 사후자산심사 안내를 상품별로 분기한다(FR-H3-01·BR-32). 금리인하요구권과 대칭이다 -- 이쪽은
 * 기금대출(버팀목)에만 노출한다. 판정이 아니라 정해진 안내라 상품만 받는다.
 */
@Component
class PostAssetReviewAdvisor {

    private static final List<String> EASY_TO_MISS = List.of("청약통장 잔액", "자동차 시가", "기존 거주지 보증금", "부모와 공동명의인 자산");
    private static final List<String> CAUTIONS =
            List.of("입주 후에 자산이 늘어난 것은 사후심사와 무관해요.", "부적격이면 가산금리가 붙고, 한 번 붙으면 되돌릴 수 없어요.");

    public PostAssetReviewResponse guide(ConsultedLoanProduct product) {
        if (product == null) {
            return needsProduct();
        }
        return switch (product) {
            case YOUTH_BEOTIMMOK, GENERAL_BEOTIMMOK ->
                new PostAssetReviewResponse(
                        true, "기금(버팀목)대출은 실행 후 사후자산심사가 있어요. 자산을 빠뜨리지 않게 확인하세요.", EASY_TO_MISS, CAUTIONS);
            case BANK_LOAN -> new PostAssetReviewResponse(false, "은행 자체 대출은 사후자산심사가 없어요.", List.of(), List.of());
            case UNKNOWN -> needsProduct();
        };
    }

    private PostAssetReviewResponse needsProduct() {
        return new PostAssetReviewResponse(false, "대출 상품을 확인해야 안내할 수 있어요.", List.of(), List.of());
    }
}
