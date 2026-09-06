package com.homerun.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.domain.contract.dto.response.MoveOutScheduleResponse;
import com.homerun.domain.dashboard.type.DeadlineType;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** FR-H10-01. 전입신고 14일 법정 기한 계산과, 법정/권장 구분을 본다. */
class MoveOutScheduleAdvisorTest {

    private final MoveOutScheduleAdvisor advisor = new MoveOutScheduleAdvisor();

    private MoveOutScheduleResponse.Item find(MoveOutScheduleResponse r, String phaseContains) {
        return r.items().stream()
                .filter(i -> i.phase().contains(phaseContains))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void should_computeMoveInReportDueAsMoveOutPlus14() {
        MoveOutScheduleResponse r = advisor.build(LocalDate.of(2026, 10, 1));
        var moveIn = find(r, "14일");
        assertThat(moveIn.dueDate()).isEqualTo(LocalDate.of(2026, 10, 15)); // 10-01 + 14일
        assertThat(moveIn.deadlineType()).isEqualTo(DeadlineType.LEGAL);
    }

    @Test
    void should_leaveDueDateNull_whenMoveOutUnknown() {
        // 이사일을 모르면 날짜를 지어내지 않는다. 문구는 여전히 유효하다.
        MoveOutScheduleResponse r = advisor.build(null);
        assertThat(find(r, "14일").dueDate()).isNull();
        assertThat(find(r, "14일").deadlineType()).isEqualTo(DeadlineType.LEGAL);
    }

    @Test
    void should_markTerminationNoticeLegal_andPriorNoticeRecommended() {
        // 종료 통보는 법정, 사전 통지는 권장 -- 권장을 법정처럼 다루지 않는다.
        MoveOutScheduleResponse r = advisor.build(LocalDate.of(2026, 10, 1));
        assertThat(find(r, "종료").deadlineType()).isEqualTo(DeadlineType.LEGAL);
        assertThat(find(r, "1개월 전").deadlineType()).isEqualTo(DeadlineType.RECOMMENDED);
    }

    @Test
    void should_leaveMoveDayAsEventWithoutDeadlineType() {
        MoveOutScheduleResponse r = advisor.build(LocalDate.of(2026, 10, 1));
        assertThat(find(r, "이사 당일").deadlineType()).isNull();
    }
}
