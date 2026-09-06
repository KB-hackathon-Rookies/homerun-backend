package com.homerun.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.domain.contract.dto.response.UnreturnedDepositResponse;
import org.junit.jupiter.api.Test;

/** BR-33. 최우선 전입신고 경고와, 반환보증 가입 여부로 이행청구/법적절차가 갈리는지 본다. */
class UnreturnedDepositAdvisorTest {

    private final UnreturnedDepositAdvisor advisor = new UnreturnedDepositAdvisor();

    @Test
    void should_alwaysWarnKeepingMoveInReport() {
        assertThat(advisor.guide(true).topWarning()).contains("전입신고");
        assertThat(advisor.guide(false).topWarning()).contains("전입신고");
    }

    @Test
    void should_startWithLeaseholdRegistration() {
        // 두 경우 모두 임차권등기명령이 첫 단계다.
        assertThat(advisor.guide(true).steps().get(0)).contains("임차권등기");
        assertThat(advisor.guide(false).steps().get(0)).contains("임차권등기");
    }

    @Test
    void should_endWithGuaranteeClaim_whenInsured() {
        // 가입자는 이행청구로 끝난다 -- 소송·강제집행 단계가 없다.
        UnreturnedDepositResponse r = advisor.guide(true);
        assertThat(r.steps()).anyMatch(s -> s.contains("이행"));
        assertThat(r.steps()).noneMatch(s -> s.contains("소송"));
        assertThat(r.steps()).hasSize(2);
    }

    @Test
    void should_giveLegalStepsInOrder_whenUninsured() {
        // 미가입은 내용증명 → 소송 → 강제집행 순서로 이어진다.
        UnreturnedDepositResponse r = advisor.guide(false);
        assertThat(r.steps()).anyMatch(s -> s.contains("내용증명"));
        assertThat(r.steps()).anyMatch(s -> s.contains("소송"));
        assertThat(r.steps()).anyMatch(s -> s.contains("강제집행"));
        assertThat(r.steps()).hasSize(4);
        assertThat(r.doNotSkipNote()).contains("건너뛰");
    }

    @Test
    void should_provideCounselingContacts() {
        assertThat(advisor.guide(false).counselingContacts()).anyMatch(c -> c.contains("1566-9009"));
    }
}
