package com.homerun.domain.asset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.asset.dto.request.AssetComparisonRequest;
import com.homerun.domain.asset.dto.response.AssetComparisonResponse;
import com.homerun.domain.asset.dto.response.AssetComparisonResult;
import com.homerun.domain.asset.repository.AssetOptionRepository;
import com.homerun.domain.asset.type.AssetType;
import com.homerun.domain.fact.exception.FactNotFoundException;
import com.homerun.domain.fact.model.Fact;
import com.homerun.domain.fact.service.FactRegistry;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * IRP 세금 계산은 CLAUDE.md에 실제로 적힌 예시("2,000만 인출 시 세금 330만")와 정확히
 * 맞는지로 검증한다 — 임의로 지어낸 숫자가 아니라 문서에 이미 있는 근거값이다.
 */
class AssetComparisonServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;

    private final PlanRepository plans = mock(PlanRepository.class);
    private final AssetOptionRepository assetOptions = mock(AssetOptionRepository.class);
    private final FactRegistry facts = mock(FactRegistry.class);
    private final AssetComparisonService service = new AssetComparisonService(plans, assetOptions, facts);

    @BeforeEach
    void setUp() {
        when(plans.findById(PLAN_ID))
                .thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, LocalDate.of(2026, 12, 1))));
    }

    @Test
    void should_calculateIrpTaxAndNetAmount_when_withdrawing20Million() {
        when(facts.require("FCT-078")).thenReturn(fact("FCT-078", "16.5"));
        when(facts.require("FCT-172")).thenReturn(fact("FCT-172", "2.2"));
        when(facts.require("FCT-173")).thenReturn(fact("FCT-173", "3.3"));

        AssetComparisonResponse response = service.compare(MEMBER_ID, PLAN_ID, request(AssetType.IRP, 20_000_000L));

        AssetComparisonResult result = response.results().get(0);
        assertThat(result.taxPenaltyRate()).isEqualByComparingTo("16.5");
        assertThat(result.taxPenaltyAmount()).isEqualTo(3_300_000L); // CLAUDE.md 예시와 동일
        assertThat(result.netAmount()).isEqualTo(16_700_000L); // "실수령 1,670만"
        assertThat(result.comparedLoanInterestMin()).isEqualTo(440_000L); // 2000만 × 2.2%
        assertThat(result.comparedLoanInterestMax()).isEqualTo(660_000L); // 2000만 × 3.3%
    }

    @Test
    void should_leaveTaxFieldsNull_when_housingSubscription() {
        when(facts.require("FCT-080")).thenReturn(fact("FCT-080", "소득공제 추징 + 청약 순위 초기화"));
        when(facts.require("FCT-081")).thenReturn(fact("FCT-081", "가입 5년 경과 · 국민주택규모 당첨 · 사망·해외이주"));
        when(facts.require("FCT-082")).thenReturn(fact("FCT-082", "연 납입 300만원 × 40% = 최대 120만원"));
        when(facts.require("FCT-172")).thenReturn(fact("FCT-172", "2.2"));
        when(facts.require("FCT-173")).thenReturn(fact("FCT-173", "3.3"));

        AssetComparisonResponse response =
                service.compare(MEMBER_ID, PLAN_ID, request(AssetType.HOUSING_SUBSCRIPTION, 20_000_000L));

        AssetComparisonResult result = response.results().get(0);
        assertThat(result.taxPenaltyRate()).isNull();
        assertThat(result.taxPenaltyAmount()).isNull();
        assertThat(result.netAmount()).isNull(); // 0원이 아니라 모른다는 뜻
        assertThat(result.comparedLoanInterestMin()).isEqualTo(440_000L); // 세금과 무관하게 계산됨
        assertThat(result.notes()).isNotEmpty();
    }

    @Test
    void should_returnNullComparedInterest_when_rateFactMissing() {
        when(facts.require("FCT-078")).thenReturn(fact("FCT-078", "16.5"));
        when(facts.require("FCT-172")).thenThrow(new FactNotFoundException("FCT-172"));
        when(facts.require("FCT-173")).thenThrow(new FactNotFoundException("FCT-173"));

        AssetComparisonResponse response = service.compare(MEMBER_ID, PLAN_ID, request(AssetType.IRP, 20_000_000L));

        AssetComparisonResult result = response.results().get(0);
        assertThat(result.comparedLoanInterestMin()).isNull();
        assertThat(result.comparedLoanInterestMax()).isNull();
        assertThat(result.taxPenaltyAmount()).isNotNull(); // 금리와 세금은 독립적으로 계산된다
    }

    @Test
    void should_throw_when_requesterIsNotPlanOwner() {
        when(plans.findById(PLAN_ID))
                .thenReturn(Optional.of(Plan.create(999L, LeaseType.JEONSE, LocalDate.of(2026, 12, 1))));

        assertThatThrownBy(() -> service.compare(MEMBER_ID, PLAN_ID, request(AssetType.IRP, 20_000_000L)))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        e -> assertThat(((BusinessException) e).errorCode()).isEqualTo(ErrorCode.PLAN_ACCESS_DENIED));
    }

    private Fact fact(String code, String value) {
        return new Fact(
                code,
                code,
                isNumeric(value) ? new BigDecimal(value) : null,
                "%",
                value,
                "https://example.org/" + code,
                false);
    }

    private boolean isNumeric(String value) {
        try {
            new BigDecimal(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private AssetComparisonRequest request(AssetType assetType, Long withdrawAmount) {
        return new AssetComparisonRequest(
                List.of(new AssetComparisonRequest.Entry(assetType, withdrawAmount, withdrawAmount)));
    }
}
