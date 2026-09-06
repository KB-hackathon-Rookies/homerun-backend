package com.homerun.domain.property.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.domain.property.dto.response.RejectionGuidanceResponse;
import com.homerun.domain.property.type.CollateralMethod;
import com.homerun.domain.property.type.RejectionCategory;
import org.junit.jupiter.api.Test;

/** BR-24 거절 사유별 대안. 핵심은 "다른 은행 무의미" 구분과 보증기관 담보 변경 경로다. */
class RejectionAdvisorTest {

    private final RejectionAdvisor advisor = new RejectionAdvisor();

    @Test
    void should_markBankChangeUseless_forSubjectIssue() {
        // 사람 문제는 은행을 바꿔도 소용없다.
        RejectionGuidanceResponse g = advisor.guide(RejectionCategory.SUBJECT_ISSUE, null);
        assertThat(g.bankChangeUseless()).isTrue();
        assertThat(g.alternatives()).isNotEmpty();
    }

    @Test
    void should_markBankChangeUseless_forPropertyIssue() {
        // 집 자체 문제도 은행을 바꿔도 소용없다.
        assertThat(advisor.guide(RejectionCategory.PROPERTY_ISSUE, null).bankChangeUseless())
                .isTrue();
    }

    @Test
    void should_notMarkBankChangeUseless_forGuaranteeIssue() {
        // 보증기관 거절은 같은 은행에서 담보를 바꾸는 게 대안이라 "은행 무의미"가 아니다.
        assertThat(advisor.guide(RejectionCategory.GUARANTEE_ISSUE, CollateralMethod.HUG_SAFE_JEONSE)
                        .bankChangeUseless())
                .isFalse();
    }

    @Test
    void should_suggestSgiOrHf_whenHugRejected() {
        RejectionGuidanceResponse g =
                advisor.guide(RejectionCategory.GUARANTEE_ISSUE, CollateralMethod.HUG_SAFE_JEONSE);
        assertThat(g.alternatives()).anyMatch(a -> a.contains("SGI") && a.contains("HF"));
    }

    @Test
    void should_suggestPolicyLoan_whenSgiRejected() {
        RejectionGuidanceResponse g = advisor.guide(RejectionCategory.GUARANTEE_ISSUE, CollateralMethod.SGI);
        assertThat(g.alternatives()).anyMatch(a -> a.contains("정책형"));
    }

    @Test
    void should_suggestSgi_whenHfRejected() {
        RejectionGuidanceResponse g = advisor.guide(RejectionCategory.GUARANTEE_ISSUE, CollateralMethod.HF);
        assertThat(g.alternatives()).anyMatch(a -> a.contains("SGI"));
    }

    @Test
    void should_askWhichGuarantee_whenCollateralUnknown() {
        RejectionGuidanceResponse g = advisor.guide(RejectionCategory.GUARANTEE_ISSUE, null);
        assertThat(g.alternatives()).isNotEmpty();
    }

    @Test
    void should_giveEveryCategoryAlternatives() {
        for (RejectionCategory category : RejectionCategory.values()) {
            assertThat(advisor.guide(category, CollateralMethod.HUG_SAFE_JEONSE).alternatives())
                    .as("%s 대안", category)
                    .isNotEmpty();
        }
    }
}
