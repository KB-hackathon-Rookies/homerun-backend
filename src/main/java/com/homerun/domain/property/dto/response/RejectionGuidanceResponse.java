package com.homerun.domain.property.dto.response;

import com.homerun.domain.property.type.RejectionCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 거절 사유 한 분류의 대안 안내(BR-24, FR-P8-01).
 *
 * @param category 거절 사유 분류
 * @param summary 무엇이 문제고 어떻게 접근해야 하는지 한 줄
 * @param alternatives 자동 제시하는 대안 목록
 * @param bankChangeUseless 다른 은행에 가도 소용없는가(사람·집 자체 문제)
 */
@Schema(description = "거절 사유별 대안 안내(BR-24)")
public record RejectionGuidanceResponse(
        RejectionCategory category, String summary, List<String> alternatives, boolean bankChangeUseless) {

    public RejectionGuidanceResponse {
        alternatives = List.copyOf(alternatives);
    }
}
