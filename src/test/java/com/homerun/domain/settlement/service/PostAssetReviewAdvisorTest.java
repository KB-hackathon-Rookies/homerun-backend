package com.homerun.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.domain.property.type.ConsultedLoanProduct;
import com.homerun.domain.settlement.dto.response.PostAssetReviewResponse;
import org.junit.jupiter.api.Test;

/** FR-H3-01·BR-32. 금리인하와 대칭 -- 기금대출에만 노출한다. */
class PostAssetReviewAdvisorTest {

    private final PostAssetReviewAdvisor advisor = new PostAssetReviewAdvisor();

    @Test
    void should_showForYouthBeotimmok() {
        PostAssetReviewResponse r = advisor.guide(ConsultedLoanProduct.YOUTH_BEOTIMMOK);
        assertThat(r.applicable()).isTrue();
        assertThat(r.easyToMiss()).contains("청약통장 잔액");
        assertThat(r.cautions()).anyMatch(c -> c.contains("가산금리"));
    }

    @Test
    void should_showForGeneralBeotimmok() {
        assertThat(advisor.guide(ConsultedLoanProduct.GENERAL_BEOTIMMOK).applicable())
                .isTrue();
    }

    @Test
    void should_hideForBankLoan() {
        PostAssetReviewResponse r = advisor.guide(ConsultedLoanProduct.BANK_LOAN);
        assertThat(r.applicable()).isFalse();
        assertThat(r.easyToMiss()).isEmpty();
    }

    @Test
    void should_askToConfirm_whenUnknownOrNull() {
        assertThat(advisor.guide(ConsultedLoanProduct.UNKNOWN).applicable()).isFalse();
        assertThat(advisor.guide(null).applicable()).isFalse();
    }
}
