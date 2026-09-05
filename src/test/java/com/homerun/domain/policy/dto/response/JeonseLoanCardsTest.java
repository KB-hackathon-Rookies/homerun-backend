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

    // --- 후보 정책 정렬(POL-02-01) ---

    @Test
    void should_orderPassBeforeNeedInfo_evenWhenNeedInfoLooksCheaper() {
        // 추가 확인이 필요한 상품은 아무리 싸 보여도 아직 실행할 수 없다.
        var results = List.of(
                priced("CHEAP-BUT-UNSURE", PolicyVerdictResult.NEED_INFO, 100_000_000L, 10_000_000L, 100_000L),
                priced("SURE-BUT-PRICEY", PolicyVerdictResult.PASS, 100_000_000L, 90_000_000L, 300_000L));

        assertThat(sortedCodes(results, 100_000_000L)).containsExactly("SURE-BUT-PRICEY", "CHEAP-BUT-UNSURE");
    }

    @Test
    void should_orderByLowerShortfall_when_bothPass() {
        // 자기자금이 덜 부족한 쪽이 실제로 실행 가능하다 — 월 이자가 더 비싸도 먼저다.
        var results = List.of(
                priced("BIG-GAP", PolicyVerdictResult.PASS, 100_000_000L, 90_000_000L, 100_000L),
                priced("SMALL-GAP", PolicyVerdictResult.PASS, 100_000_000L, 60_000_000L, 300_000L));

        assertThat(sortedCodes(results, 50_000_000L)).containsExactly("SMALL-GAP", "BIG-GAP");
    }

    @Test
    void should_orderByLowerMonthlyInterest_when_shortfallTies() {
        var results = List.of(
                priced("EXPENSIVE", PolicyVerdictResult.PASS, 100_000_000L, 36_000_000L, 300_000L),
                priced("CHEAP", PolicyVerdictResult.PASS, 100_000_000L, 36_000_000L, 100_000L));

        assertThat(sortedCodes(results, 10_000_000L)).containsExactly("CHEAP", "EXPENSIVE");
    }

    @Test
    void should_orderByLargerLoanAmount_when_costTies() {
        // 부족액도 월 이자도 같으면 더 많이 빌려주는 쪽이 낫다.
        var results = List.of(
                priced("SMALL-LOAN", PolicyVerdictResult.PASS, 100_000_000L, 36_000_000L, 100_000L),
                priced("BIG-LOAN", PolicyVerdictResult.PASS, 200_000_000L, 36_000_000L, 100_000L));

        assertThat(sortedCodes(results, 10_000_000L)).containsExactly("BIG-LOAN", "SMALL-LOAN");
    }

    @Test
    void should_placeUnknownEstimateLast_ratherThanGuessingFavourably() {
        // 계산할 수 없는 상품에 유리한 기본값을 채우지 않는다. 아는 것부터 줄 세우고 모르는 건 뒤로.
        var results = List.of(
                result("UNKNOWN", PolicyVerdictResult.PASS, null),
                priced("KNOWN", PolicyVerdictResult.PASS, 100_000_000L, 90_000_000L, 900_000L));

        assertThat(sortedCodes(results, 10_000_000L)).containsExactly("KNOWN", "UNKNOWN");
    }

    @Test
    void should_keepInputOrder_when_everyKeyTies() {
        // 정책 코드로 tie-break 하면 알파벳순(GENERAL→SEOUL→YOUTH)으로 흔들린다.
        // 전부 동률이면 큐레이션된 입력 순서가 그대로 남아야 한다.
        var results = List.of(
                result("JEONSE-YOUTH-BEOTIMMOK", PolicyVerdictResult.PASS),
                result("JEONSE-GENERAL-BEOTIMMOK", PolicyVerdictResult.PASS),
                result("JEONSE-SEOUL-INTEREST-SUPPORT", PolicyVerdictResult.PASS));

        assertThat(sortedCodes(results, 10_000_000L))
                .containsExactly("JEONSE-YOUTH-BEOTIMMOK", "JEONSE-GENERAL-BEOTIMMOK", "JEONSE-SEOUL-INTEREST-SUPPORT");
    }

    @Test
    void should_keepConsultationLast_evenWhenPolicyCardsAreReordered() {
        // 상담 카드는 정책이 아니라 안내다. 정렬에 끼어들면 안 된다.
        var results = List.of(
                priced("EXPENSIVE", PolicyVerdictResult.PASS, 100_000_000L, 36_000_000L, 300_000L),
                priced("CHEAP", PolicyVerdictResult.PASS, 100_000_000L, 36_000_000L, 100_000L));

        var cards = JeonsePolicyVerdictListResponse.loans(10L, results, Instant.EPOCH, 10_000_000L)
                .cards();

        assertThat(cards)
                .extracting(JeonseLoanCardResponse::code)
                .containsExactly("CHEAP", "EXPENSIVE", "KB-JEONSE-CONSULTATION");
        assertThat(cards.get(2).type()).isEqualTo(JeonseLoanCardResponse.CardType.CONSULTATION);
    }

    private List<String> sortedCodes(List<PolicyVerdictResponse> results, Long availableCash) {
        return JeonsePolicyVerdictListResponse.loans(10L, results, Instant.EPOCH, availableCash).cards().stream()
                .filter(card -> card.type() == JeonseLoanCardResponse.CardType.POLICY)
                .map(JeonseLoanCardResponse::code)
                .toList();
    }

    private PolicyVerdictResponse result(String code, PolicyVerdictResult verdict) {
        return result(
                code,
                verdict,
                verdict == PolicyVerdictResult.FAIL
                        ? null
                        : new LoanEstimateResponse(null, 144_000_000L, 36_000_000L, null, null, null, null));
    }

    private PolicyVerdictResponse priced(
            String code, PolicyVerdictResult verdict, Long loanAmount, Long ownFundsRequired, Long monthlyInterest) {
        return result(
                code,
                verdict,
                new LoanEstimateResponse(null, loanAmount, ownFundsRequired, null, null, monthlyInterest, null));
    }

    private PolicyVerdictResponse result(String code, PolicyVerdictResult verdict, LoanEstimateResponse estimate) {
        return new PolicyVerdictResponse(
                code, code, verdict, 1, List.of(), List.of(), List.of(), estimate, null, List.of());
    }
}
