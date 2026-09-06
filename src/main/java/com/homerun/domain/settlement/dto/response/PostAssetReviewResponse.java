package com.homerun.domain.settlement.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 사후자산심사 안내(FR-H3-01·BR-32). 실행 대출 상품으로 분기한다.
 *
 * <p>사후자산심사는 기금대출(버팀목)에만 있다. 은행 자체 대출 사용자에게 노출하면 잘못된
 * 안내라 {@code applicable=false} 로 두어 화면에서 4-3 을 숨긴다(NFR-UX-08).
 *
 * @param applicable 사후자산심사 대상인가(기금대출만 true)
 * @param summary 상태 한 줄
 * @param easyToMiss 빠뜨리기 쉬운 자산 항목(대상일 때만)
 * @param cautions 주의사항(대상일 때만)
 */
@Schema(description = "사후자산심사 안내(FR-H3-01)")
public record PostAssetReviewResponse(
        boolean applicable, String summary, List<String> easyToMiss, List<String> cautions) {

    public PostAssetReviewResponse {
        easyToMiss = List.copyOf(easyToMiss);
        cautions = List.copyOf(cautions);
    }
}
