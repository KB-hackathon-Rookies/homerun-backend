package com.homerun.domain.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.fact.model.Fact;
import com.homerun.domain.fact.service.FactRegistry;
import com.homerun.domain.plan.dto.request.PlanInputRequest;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.entity.PlanInputHistory;
import com.homerun.domain.plan.repository.PlanInputHistoryRepository;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.CompanySize;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.policy.dto.response.PreferentialRateChange;
import com.homerun.domain.policy.dto.response.PreferentialRateChangeResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 응답 자체는 순수 비교 로직이라 mock으로 충분 — JSONB 왕복은 통합테스트가 맡는다. */
class PreferentialRateChangeServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;

    private final PlanRepository plans = mock(PlanRepository.class);
    private final PlanInputRepository inputs = mock(PlanInputRepository.class);
    private final PlanInputHistoryRepository history = mock(PlanInputHistoryRepository.class);
    private final FactRegistry facts = mock(FactRegistry.class);
    private final PreferentialRateChangeService service =
            new PreferentialRateChangeService(plans, inputs, history, facts);

    @BeforeEach
    void setUp() {
        when(plans.findById(PLAN_ID))
                .thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, LocalDate.of(2026, 12, 1))));
    }

    @Test
    void should_detectPromotion_when_companySizeChangesToSmall() {
        when(inputs.findByPlanId(PLAN_ID)).thenReturn(Optional.of(companyInput(CompanySize.SMALL)));
        when(history.findFirstByPlanIdOrderByRevisionDesc(PLAN_ID))
                .thenReturn(Optional.of(PlanInputHistory.capture(companyInput(CompanySize.LARGE))));
        when(facts.require("FCT-019"))
                .thenReturn(new Fact("FCT-019", "중소기업 취업·창업 청년", new BigDecimal("0.3"), "%p", "연 0.3%p", "url", false));

        PreferentialRateChangeResponse response = service.detect(MEMBER_ID, PLAN_ID);

        assertThat(response.changes()).hasSize(1);
        PreferentialRateChange change = response.changes().get(0);
        assertThat(change.code()).isEqualTo("YOUNG_EMPLOYMENT_DISCOUNT");
        assertThat(change.rateBonus()).isEqualByComparingTo("0.3");
    }

    @Test
    void should_notDetectPromotion_when_alreadyEligibleBefore() {
        // 이전에도 SMALL 이었으면 "새로" 바뀐 게 아니다 — 승격이 아니라 유지다.
        when(inputs.findByPlanId(PLAN_ID)).thenReturn(Optional.of(companyInput(CompanySize.SMALL)));
        when(history.findFirstByPlanIdOrderByRevisionDesc(PLAN_ID))
                .thenReturn(Optional.of(PlanInputHistory.capture(companyInput(CompanySize.STARTUP))));

        PreferentialRateChangeResponse response = service.detect(MEMBER_ID, PLAN_ID);

        assertThat(response.changes()).isEmpty();
    }

    @Test
    void should_notDetectPromotion_when_stillLarge() {
        when(inputs.findByPlanId(PLAN_ID)).thenReturn(Optional.of(companyInput(CompanySize.LARGE)));
        when(history.findFirstByPlanIdOrderByRevisionDesc(PLAN_ID))
                .thenReturn(Optional.of(PlanInputHistory.capture(companyInput(CompanySize.LARGE))));

        PreferentialRateChangeResponse response = service.detect(MEMBER_ID, PLAN_ID);

        assertThat(response.changes()).isEmpty();
    }

    @Test
    void should_returnEmpty_when_noHistoryExists() {
        // 수정 이력이 없으면 비교 대상이 없다 — 승격이라 부를 "이전"이 없다.
        when(inputs.findByPlanId(PLAN_ID)).thenReturn(Optional.of(companyInput(CompanySize.SMALL)));
        when(history.findFirstByPlanIdOrderByRevisionDesc(PLAN_ID)).thenReturn(Optional.empty());

        PreferentialRateChangeResponse response = service.detect(MEMBER_ID, PLAN_ID);

        assertThat(response.changes()).isEmpty();
    }

    private PlanInput companyInput(CompanySize companySize) {
        return PlanInput.create(
                PLAN_ID,
                new PlanInputRequest(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        companySize,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        true,
                        Set.of()));
    }
}
