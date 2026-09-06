package com.homerun.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.contract.dto.response.RenewalMethodsResponse;
import com.homerun.domain.contract.type.RenewalMethod;
import com.homerun.domain.fact.model.Fact;
import com.homerun.domain.fact.service.FactRegistry;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.LeaseType;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** FR-H9-02. 3종 비교와, 법정 수치(청구권·통보시기)를 팩트에서 읽는지를 본다. */
class RenewalMethodsServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;

    private final PlanRepository plans = mock(PlanRepository.class);
    private final FactRegistry facts = mock(FactRegistry.class);
    private final RenewalMethodsService service = new RenewalMethodsService(plans, facts);

    private Fact textFact(String code, String text) {
        return new Fact(code, code, null, null, text, "url", false);
    }

    private void given() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, null)));
        // 팩트에 화면과 다른, 구분되는 값을 넣어 코드가 실제로 팩트를 읽는지 확인한다.
        when(facts.require("FCT-111")).thenReturn(textFact("FCT-111", "종료 6~2개월 전(팩트값)"));
        when(facts.require("FCT-113")).thenReturn(textFact("FCT-113", "1회 · 5% 상한(팩트값)"));
    }

    private String detailOf(RenewalMethodsResponse r, RenewalMethod method) {
        return r.methods().stream()
                .filter(m -> m.method() == method)
                .map(RenewalMethodsResponse.Method::detail)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void should_compareThreeMethods() {
        given();
        RenewalMethodsResponse r = service.forPlan(MEMBER_ID, PLAN_ID);
        assertThat(r.methods())
                .extracting(RenewalMethodsResponse.Method::method)
                .containsExactly(RenewalMethod.CLAIM, RenewalMethod.IMPLIED, RenewalMethod.AGREED);
    }

    @Test
    void should_readClaimDetailAndNoticeWindowFromFacts() {
        given();
        RenewalMethodsResponse r = service.forPlan(MEMBER_ID, PLAN_ID);
        // 청구권 상세와 통보시기가 팩트 원문에서 온다 -- 하드코딩이면 이 값이 아니다.
        assertThat(detailOf(r, RenewalMethod.CLAIM)).isEqualTo("1회 · 5% 상한(팩트값)");
        assertThat(r.noticeWindow()).isEqualTo("종료 6~2개월 전(팩트값)");
    }

    @Test
    void should_noteClaimRightIsSingleUse() {
        given();
        assertThat(service.forPlan(MEMBER_ID, PLAN_ID).reuseNote()).contains("1회");
    }
}
