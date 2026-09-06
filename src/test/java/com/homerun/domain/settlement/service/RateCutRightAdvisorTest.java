package com.homerun.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.domain.property.type.ConsultedLoanProduct;
import com.homerun.domain.settlement.dto.response.RateCutRightResponse;
import org.junit.jupiter.api.Test;

/** FR-H6-01·BR-32. 핵심은 버팀목(기금) 사용자에게 금리인하요구권을 노출하지 않는 것이다. */
class RateCutRightAdvisorTest {

    private final RateCutRightAdvisor advisor = new RateCutRightAdvisor();

    @Test
    void should_hideForYouthBeotimmok() {
        RateCutRightResponse r = advisor.guide(ConsultedLoanProduct.YOUTH_BEOTIMMOK);
        assertThat(r.applicable()).isFalse();
        assertThat(r.applyReasons()).isEmpty();
        assertThat(r.alternativeNote()).contains("우대금리");
    }

    @Test
    void should_hideForGeneralBeotimmok() {
        assertThat(advisor.guide(ConsultedLoanProduct.GENERAL_BEOTIMMOK).applicable())
                .isFalse();
    }

    @Test
    void should_showForBankLoan_withReasonsAndChannels() {
        RateCutRightResponse r = advisor.guide(ConsultedLoanProduct.BANK_LOAN);
        assertThat(r.applicable()).isTrue();
        assertThat(r.applyReasons()).hasSize(4);
        assertThat(r.applyChannels()).containsExactly("영업점 방문", "인터넷뱅킹", "은행 앱");
        assertThat(r.alternativeNote()).isNull();
    }

    @Test
    void should_askToConfirmProduct_whenUnknownOrNull() {
        assertThat(advisor.guide(ConsultedLoanProduct.UNKNOWN).applicable()).isFalse();
        assertThat(advisor.guide(null).applicable()).isFalse();
        assertThat(advisor.guide(ConsultedLoanProduct.UNKNOWN).applyReasons()).isEmpty();
    }
}
