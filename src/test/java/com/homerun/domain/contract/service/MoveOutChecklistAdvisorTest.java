package com.homerun.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.domain.contract.dto.response.MoveOutChecklistResponse;
import org.junit.jupiter.api.Test;

/** FR-H10-04. 아파트/오피스텔은 장기수선충당금, 반환보증 가입자는 해지 항목이 해당된다. */
class MoveOutChecklistAdvisorTest {

    private final MoveOutChecklistAdvisor advisor = new MoveOutChecklistAdvisor();

    private boolean applicable(MoveOutChecklistResponse r, String code) {
        return r.items().stream()
                .filter(i -> i.code().equals(code))
                .map(MoveOutChecklistResponse.Item::applicable)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void should_markLongTermReserve_onlyForAptOrOfficetel() {
        assertThat(applicable(advisor.guide(true, false), "LONG_TERM_REPAIR_RESERVE"))
                .isTrue();
        assertThat(applicable(advisor.guide(false, false), "LONG_TERM_REPAIR_RESERVE"))
                .isFalse();
    }

    @Test
    void should_markReturnGuaranteeCancel_onlyWhenInsured() {
        assertThat(applicable(advisor.guide(false, true), "RETURN_GUARANTEE_CANCEL"))
                .isTrue();
        assertThat(applicable(advisor.guide(false, false), "RETURN_GUARANTEE_CANCEL"))
                .isFalse();
    }

    @Test
    void should_alwaysIncludeCommonItems() {
        MoveOutChecklistResponse r = advisor.guide(false, false);
        assertThat(applicable(r, "MAINTENANCE_FEE_SETTLE")).isTrue();
        assertThat(applicable(r, "YEAR_END_TAX_DOCS")).isTrue();
        assertThat(applicable(r, "AUTOPAY_CANCEL")).isTrue();
    }

    @Test
    void should_listAllFiveItemsRegardlessOfApplicability() {
        // 해당 없어도 목록에서 빼지 않는다 -- 왜 해당 없는지 화면에서 보이게.
        assertThat(advisor.guide(false, false).items()).hasSize(5);
    }
}
