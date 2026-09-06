package com.homerun.domain.settlement.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 홈 정착 대시보드(FR-HD-01).
 *
 * @param daysSinceIndependence 독립(실제 전입) 후 경과일 D+N. 아직 전입 전이면 null
 * @param items 정착 항목 체크 목록
 * @param progressPercent 진행률(%). 추적 가능한 항목(DONE·PENDING)만 분모로 쓴다
 */
@Schema(description = "홈 정착 대시보드(FR-HD-01)")
public record SettlementDashboardResponse(
        Long daysSinceIndependence, List<DashboardItemResponse> items, int progressPercent) {

    public SettlementDashboardResponse {
        items = List.copyOf(items);
    }
}
