package com.homerun.domain.policy.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.domain.policy.type.PolicyVerdictResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class JeonseLoanCardsTest {
    @Test
    void should_keepAllMatchingProducts_evenWhenCashIsInsufficient() {
        var results = List.of(
                result("JEONSE-YOUTH-BEOTIMMOK", PolicyVerdictResult.PASS),
                result("JEONSE-GENERAL-BEOTIMMOK", PolicyVerdictResult.PASS),
                result("JEONSE-SEOUL-INTEREST-SUPPORT", PolicyVerdictResult.PASS));
        var response = JeonsePolicyVerdictListResponse.loans(10L, results, Instant.EPOCH, 10_000_000L);
        assertThat(response.cards())
                .extracting(JeonseLoanCardResponse::code)
                .containsExactly(
                        "JEONSE-YOUTH-BEOTIMMOK",
                        "JEONSE-GENERAL-BEOTIMMOK",
                        "JEONSE-SEOUL-INTEREST-SUPPORT",
                        "KB-JEONSE-CONSULTATION");
        assertThat(response.cards().get(1).ownFundsShortfall()).isEqualTo(26_000_000L);
        assertThat(response.results()).isEqualTo(results);
    }

    @Test
    void should_preserveNeedInfoAndFailure_withoutTreatingBankAsApproved() {
        var results =
                List.of(result("YOUTH", PolicyVerdictResult.NEED_INFO), result("GENERAL", PolicyVerdictResult.FAIL));
        var response = JeonsePolicyVerdictListResponse.loans(10L, results, Instant.EPOCH, null);
        assertThat(response.cards()).hasSize(2);
        assertThat(response.cards().get(0).verdict()).isEqualTo(PolicyVerdictResult.NEED_INFO);
        assertThat(response.cards().get(0).ownFundsShortfall()).isNull();
        assertThat(response.results()).hasSize(2);
        var bank = response.cards().get(1);
        assertThat(bank.type()).isEqualTo(JeonseLoanCardResponse.CardType.CONSULTATION);
        assertThat(bank.verdict()).isNull();
        assertThat(bank.estimate()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"35999999,1", "36000000,0", "36000001,0"})
    void should_boundCashShortfallAtZero(long cash, long shortfall) {
        assertThat(JeonseLoanCardResponse.policy(result("GENERAL", PolicyVerdictResult.PASS), cash)
                        .ownFundsShortfall())
                .isEqualTo(shortfall);
    }

    @Test
    void should_notAddLoanCardsToGuaranteeResponses() {
        assertThat(new JeonsePolicyVerdictListResponse(10L, List.of(), Instant.EPOCH).cards())
                .isEmpty();
    }

    private PolicyVerdictResponse result(String code, PolicyVerdictResult verdict) {
        return new PolicyVerdictResponse(
                code,
                code,
                verdict,
                List.of(),
                List.of(),
                verdict == PolicyVerdictResult.FAIL
                        ? null
                        : new LoanEstimateResponse(null, 144_000_000L, 36_000_000L, null, null, null, null));
    }
}
