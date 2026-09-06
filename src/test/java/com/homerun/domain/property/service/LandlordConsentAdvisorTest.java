package com.homerun.domain.property.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.domain.property.dto.response.LandlordConsentGuideResponse;
import com.homerun.domain.property.type.LandlordConsent;
import org.junit.jupiter.api.Test;

/** FR-P1-08 상태별 안내. 스크립트는 NOT_ASKED/REFUSED 에만, CONFIRMED 는 경고도 스크립트도 없다. */
class LandlordConsentAdvisorTest {

    private final LandlordConsentAdvisor advisor = new LandlordConsentAdvisor();

    @Test
    void should_giveNoWarningOrScript_when_confirmed() {
        LandlordConsentGuideResponse guide = advisor.guide(LandlordConsent.CONFIRMED);

        assertThat(guide.bankConsultationWarning()).isNull();
        assertThat(guide.scripts()).isEmpty();
        assertThat(guide.suggestOtherProperty()).isFalse();
    }

    @Test
    void should_warnAndGiveBrokerScript_when_notAsked() {
        LandlordConsentGuideResponse guide = advisor.guide(LandlordConsent.NOT_ASKED);

        assertThat(guide.bankConsultationWarning()).isNotBlank();
        assertThat(guide.scripts()).isNotEmpty();
        // 아직 안 물어본 상태는 다른 매물을 권하지 않는다 — 거부와 구분한다.
        assertThat(guide.suggestOtherProperty()).isFalse();
    }

    @Test
    void should_givePersuasionScriptsForFourMisunderstandings_when_refused() {
        LandlordConsentGuideResponse guide = advisor.guide(LandlordConsent.REFUSED);

        // 개인정보·집에 걸림·복잡함·서류 4가지 오해 각각에 대응하는 스크립트.
        assertThat(guide.scripts()).hasSize(4);
        assertThat(guide.suggestOtherProperty()).isTrue();
    }

    @Test
    void should_treatNullAsNotAsked() {
        // 아직 저장 전이면 통과가 아니라 아직 안 물어본 상태로 안내한다.
        assertThat(advisor.guide(null).consent()).isEqualTo(LandlordConsent.NOT_ASKED);
    }
}
