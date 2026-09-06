package com.homerun.domain.settlement.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 금리인하요구권 안내(FR-H6-01·BR-32). 실행 대출 상품으로 분기한다.
 *
 * <p>버팀목(기금)은 대상이 아니라 화면에서 아예 숨겨야 오안내가 없다(NFR-UX-08). 그때는
 * {@code applicable=false} 에 대체 안내를 준다.
 *
 * @param applicable 금리인하요구권 대상인가(은행 자체 대출만 true)
 * @param summary 상태 한 줄
 * @param applyReasons 신청 사유(대상일 때만)
 * @param applyChannels 신청 경로(대상일 때만)
 * @param alternativeNote 비대상일 때 대체 안내(버팀목 우대금리 등). 대상이면 null
 */
@Schema(description = "금리인하요구권 안내(FR-H6-01)")
public record RateCutRightResponse(
        boolean applicable,
        String summary,
        List<String> applyReasons,
        List<String> applyChannels,
        String alternativeNote) {

    public RateCutRightResponse {
        applyReasons = List.copyOf(applyReasons);
        applyChannels = List.copyOf(applyChannels);
    }
}
