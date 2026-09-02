package com.homerun.domain.rent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

class RentPolicyControllerTest {

    private final RentSupportResolver supportResolver = new RentSupportResolver();
    private final RentTaxCreditCalculator taxCreditCalculator = mock(RentTaxCreditCalculator.class);
    private final EffectiveRentCalculator effectiveRentCalculator = mock(EffectiveRentCalculator.class);
    private final MockMvcTester mvc = MockMvcTester.of(
            List.of(new RentPolicyController(supportResolver, taxCreditCalculator, effectiveRentCalculator)),
            builder -> builder.setControllerAdvice(new GlobalExceptionHandler()).build());

    @Test
    @DisplayName("배타 관계인 지원금은 함께 담긴 조합이 응답에 없다")
    void should_exclude_conflicting_combination() {
        assertThat(mvc.post()
                        .uri("/api/v1/policies/rent/supports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eligibleSupports":[
                                  {"type":"HOUSING_BENEFIT","monthlyAmount":300000,"months":12,"provisional":false},
                                  {"type":"YOUTH_RENT_NATIONAL","monthlyAmount":200000,"months":24,"provisional":false}
                                ]}
                                """))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data.combinations")
                .asList()
                .hasSize(2);
    }

    @Test
    @DisplayName("총액이 큰 조합을 추천으로 표시한다")
    void should_mark_recommended_combination() {
        assertThat(mvc.post()
                        .uri("/api/v1/policies/rent/supports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eligibleSupports":[
                                  {"type":"HOUSING_BENEFIT","monthlyAmount":300000,"months":12,"provisional":false},
                                  {"type":"YOUTH_RENT_NATIONAL","monthlyAmount":200000,"months":24,"provisional":false}
                                ]}
                                """))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data.combinations[0].totalAmount")
                .isEqualTo(4800000);
    }

    @Test
    @DisplayName("첫 조합만 추천으로 표시된다")
    void should_mark_only_first_as_recommended() {
        assertThat(mvc.post()
                        .uri("/api/v1/policies/rent/supports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eligibleSupports":[
                                  {"type":"HOUSING_BENEFIT","monthlyAmount":300000,"months":12,"provisional":false},
                                  {"type":"YOUTH_RENT_NATIONAL","monthlyAmount":200000,"months":24,"provisional":false}
                                ]}
                                """))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data.combinations[0].recommended")
                .isEqualTo(true);
    }

    @Test
    @DisplayName("받을 수 있는 지원금이 없으면 빈 목록을 준다")
    void should_return_empty_when_nothing_eligible() {
        assertThat(mvc.post()
                        .uri("/api/v1/policies/rent/supports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eligibleSupports\":[]}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data.combinations")
                .asList()
                .isEmpty();
    }

    @Test
    @DisplayName("음수 지원액은 400 과 사유를 준다")
    void should_reject_negative_amount() {
        assertThat(mvc.post()
                        .uri("/api/v1/policies/rent/supports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eligibleSupports":[
                                  {"type":"HOUSING_BENEFIT","monthlyAmount":-1,"months":12,"provisional":false}
                                ]}
                                """))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("COMMON_002");
    }

    @Test
    @DisplayName("세액공제 결과를 그대로 내려준다")
    void should_return_tax_credit_result() {
        when(taxCreditCalculator.calculate(any()))
                .thenReturn(new TaxCreditResult(
                        true, 6_000_000, 2_400_000, 3_600_000, new BigDecimal("0.17"), 612_000, null));

        assertThat(mvc.post()
                        .uri("/api/v1/policies/rent/tax-credit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"monthlyRent":500000,"months":12,"annualSalary":42000000,
                                 "supportWithinYear":2400000,"homeless":true,"residentRegistered":true}
                                """))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data.refund")
                .isEqualTo(612000);
    }

    @Test
    @DisplayName("공제 대상이 아니어도 200 으로 사유와 함께 응답한다")
    void should_return_ok_with_reason_when_not_eligible() {
        when(taxCreditCalculator.calculate(any()))
                .thenReturn(TaxCreditResult.notEligible(6_000_000, "전입신고를 마쳐야 공제 대상이다"));

        assertThat(mvc.post()
                        .uri("/api/v1/policies/rent/tax-credit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"monthlyRent":500000,"months":12,"annualSalary":42000000,
                                 "supportWithinYear":0,"homeless":true,"residentRegistered":false}
                                """))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data.eligible")
                .isEqualTo(false);
    }

    @Test
    @DisplayName("개월 수가 12 를 넘으면 400 이다")
    void should_reject_months_over_twelve() {
        assertThat(mvc.post()
                        .uri("/api/v1/policies/rent/tax-credit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"monthlyRent":500000,"months":13,"annualSalary":42000000,
                                 "supportWithinYear":0,"homeless":true,"residentRegistered":true}
                                """))
                .hasStatus(400);
    }

    @Test
    @DisplayName("확정되지 않은 기준 수치를 만나면 422 로 끊고 팩트코드를 알려준다")
    void should_return_unprocessable_on_unusable_fact() {
        when(effectiveRentCalculator.calculate(any())).thenThrow(new BusinessException(ErrorCode.UNUSABLE_FACT));

        assertThat(mvc.post()
                        .uri("/api/v1/policies/rent/effective-cost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"monthlyRent":500000,"managementFee":70000,"months":12,
                                 "annualSalary":42000000,"eligibleSupports":[],
                                 "homeless":true,"residentRegistered":true}
                                """))
                .hasStatus(422)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("FACT_001");
    }

    @Test
    @DisplayName("실질 주거비 계산 결과를 내려준다")
    void should_return_effective_cost() {
        when(effectiveRentCalculator.calculate(any()))
                .thenReturn(new EffectiveRentResult(
                        500_000,
                        70_000,
                        0,
                        85_000,
                        415_000,
                        485_000,
                        new RentSupportCombination(List.of(), 0, true),
                        TaxCreditResult.notEligible(6_000_000, null)));

        assertThat(mvc.post()
                        .uri("/api/v1/policies/rent/effective-cost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"monthlyRent":500000,"managementFee":70000,"months":12,
                                 "annualSalary":42000000,"eligibleSupports":[],
                                 "homeless":true,"residentRegistered":true}
                                """))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data.effectiveHousingCost")
                .isEqualTo(485000);
    }
}
