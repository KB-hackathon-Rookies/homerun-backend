package com.homerun.domain.contract.dto.response;

import com.homerun.domain.dashboard.type.DeadlineType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 퇴거 일정 안내(FR-H10-01). 법정 기한(LEGAL)과 권장(RECOMMENDED)을 구분한다 -- 권장을 법정처럼
 * 다루면 사용자가 진짜 마감을 못 알아본다.
 *
 * @param items 단계별 일정
 */
@Schema(description = "퇴거 일정 안내(FR-H10-01)")
public record MoveOutScheduleResponse(List<Item> items) {

    public MoveOutScheduleResponse {
        items = List.copyOf(items);
    }

    /**
     * @param phase 시점
     * @param action 할 일
     * @param deadlineType 기한 종류. 일정이 아닌 이벤트면 null
     * @param dueDate 계산된 마감일. 이사일을 모르면 null
     */
    public record Item(String phase, String action, DeadlineType deadlineType, LocalDate dueDate) {}
}
