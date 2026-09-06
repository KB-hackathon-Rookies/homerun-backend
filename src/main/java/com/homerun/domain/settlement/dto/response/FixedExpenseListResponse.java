package com.homerun.domain.settlement.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 고정지출 목록(FR-H5-01).
 *
 * @param items 등록된 고정지출
 * @param monthlyTotal 월 고정지출 합계(원)
 * @param delinquencyAlertActive 연체 알림이 동작하는가. 이자(INTEREST) 항목이 없으면 false
 */
@Schema(description = "고정지출 목록(FR-H5-01)")
public record FixedExpenseListResponse(
        List<FixedExpenseResponse> items, long monthlyTotal, boolean delinquencyAlertActive) {

    public FixedExpenseListResponse {
        items = List.copyOf(items);
    }
}
