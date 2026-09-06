package com.homerun.domain.settlement.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 반환보증 가입 안내(FR-H1-01·BR-32). 실행 대출의 담보로 분기한다.
 *
 * <p>HUG 안심전세는 대출보증에 반환보증이 포함돼 있어 따로 가입할 필요가 없다 -- 그때는
 * {@code needed=false} 로 두어 화면에서 4-1 을 숨긴다(오안내 방지, NFR-UX-08).
 *
 * @param needed 반환보증을 따로 가입해야 하는가(HUG 는 false)
 * @param summary 상태 한 줄
 * @param timing 가입 시기(가입 필요할 때만)
 * @param channels 가입 경로(가입 필요할 때만)
 * @param documents 필요 서류(가입 필요할 때만)
 * @param reuseNote 3루 서류를 재사용할 수 있는지(BR-26). 가입 불필요면 null
 */
@Schema(description = "반환보증 가입 안내(FR-H1-01)")
public record ReturnGuaranteeGuideResponse(
        boolean needed,
        String summary,
        String timing,
        List<String> channels,
        List<String> documents,
        String reuseNote) {

    public ReturnGuaranteeGuideResponse {
        channels = List.copyOf(channels);
        documents = List.copyOf(documents);
    }
}
