package com.homerun.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.domain.property.type.CollateralMethod;
import com.homerun.domain.settlement.dto.response.ReturnGuaranteeGuideResponse;
import org.junit.jupiter.api.Test;

/** FR-H1-01·BR-32. 핵심은 HUG 담보에 반환보증 가입을 다시 노출하지 않는 것이다. */
class ReturnGuaranteeAdvisorTest {

    private final ReturnGuaranteeAdvisor advisor = new ReturnGuaranteeAdvisor();

    @Test
    void should_hideForHug_becauseIncluded() {
        ReturnGuaranteeGuideResponse r = advisor.guide(CollateralMethod.HUG_SAFE_JEONSE);
        assertThat(r.needed()).isFalse();
        assertThat(r.summary()).contains("이미 포함");
        assertThat(r.documents()).isEmpty();
    }

    @Test
    void should_showForHf_withTimingDocumentsAndReuse() {
        ReturnGuaranteeGuideResponse r = advisor.guide(CollateralMethod.HF);
        assertThat(r.needed()).isTrue();
        assertThat(r.timing()).isNotBlank();
        assertThat(r.documents()).contains("전입세대확인서");
        assertThat(r.reuseNote()).contains("3개월");
    }

    @Test
    void should_showForSgiAndClaimTransfer() {
        assertThat(advisor.guide(CollateralMethod.SGI).needed()).isTrue();
        assertThat(advisor.guide(CollateralMethod.CLAIM_TRANSFER).needed()).isTrue();
        assertThat(advisor.guide(CollateralMethod.OTHER).needed()).isTrue();
    }

    @Test
    void should_askToConfirm_whenUnknownOrNull() {
        assertThat(advisor.guide(CollateralMethod.UNKNOWN).needed()).isFalse();
        assertThat(advisor.guide(null).needed()).isFalse();
        assertThat(advisor.guide(null).summary()).contains("담보를 확인");
    }
}
