package com.homerun.domain.contract.service;

import com.homerun.domain.contract.dto.response.MoveOutScheduleResponse;
import com.homerun.domain.contract.dto.response.MoveOutScheduleResponse.Item;
import com.homerun.domain.dashboard.type.DeadlineType;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 퇴거 일정을 안내한다(FR-H10-01). 판정이 아니라 정해진 순서 안내다.
 *
 * <p>법정 기한(종료 통보 창구·전입신고 14일)과 권장(사전 통지)을 구분한다. 전입신고 기한만
 * 이사일에서 계산한다 -- 나머지는 상대 시점이라 날짜를 지어내지 않는다.
 */
@Component
class MoveOutScheduleAdvisor {

    private static final int MOVE_IN_REPORT_DAYS = 14;

    public MoveOutScheduleResponse build(LocalDate moveOutDate) {
        LocalDate moveInReportDue = moveOutDate == null ? null : moveOutDate.plusDays(MOVE_IN_REPORT_DAYS);
        return new MoveOutScheduleResponse(List.of(
                new Item("계약 종료 6~2개월 전", "임대인에게 갱신/퇴거 의사를 문자·내용증명으로 통보한다.", DeadlineType.LEGAL, null),
                new Item("이사 약 1개월 전", "새 집 계약과 은행 상환 예정을 임대인·은행에 통지한다.", DeadlineType.RECOMMENDED, null),
                new Item("이사 당일", "보증금을 반환받아 대출을 상환한다. 임대인이 은행에 직접 송금한다.", null, moveOutDate),
                new Item("이사 후 14일 이내", "새 집에 전입신고한다(옛 집은 자동 전출).", DeadlineType.LEGAL, moveInReportDue)));
    }
}
