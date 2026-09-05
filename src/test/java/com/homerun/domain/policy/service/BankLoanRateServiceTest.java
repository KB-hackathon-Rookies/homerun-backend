package com.homerun.domain.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.homerun.domain.fact.exception.FactNotFoundException;
import com.homerun.domain.fact.exception.UnusableFactException;
import com.homerun.domain.fact.model.Fact;
import com.homerun.domain.fact.service.FactRegistry;
import com.homerun.domain.fact.type.Confidence;
import com.homerun.domain.policy.dto.response.BankLoanRateListResponse;
import com.homerun.domain.policy.dto.response.BankLoanRateResponse;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 시드가 맞는지는 {@code BankLoanRateIntegrationTest} 가 본다. 여기서는 조립 규칙만 본다 —
 * 정렬, 못 쓰는 팩트 처리, 그리고 전체 평균을 다시 계산하지 않는다는 것.
 *
 * <p>스터빙은 전부 {@code doReturn/doThrow ... when(mock)} 형태다. 기본 스텁이 예외를 던지므로
 * {@code when(mock.require(...))} 를 쓰면 스텁을 등록하는 그 호출에서 먼저 터진다.
 */
class BankLoanRateServiceTest {

    private final FactRegistry facts = mock(FactRegistry.class);
    private final BankLoanRateService service = new BankLoanRateService(facts);

    @BeforeEach
    void setUp() {
        // 기본은 "그런 팩트 없음". 각 테스트가 필요한 것만 채운다.
        doThrow(new FactNotFoundException("none")).when(facts).require(anyString());
    }

    private void stubBank(String code, String bankName, String rate) {
        doReturn(new Fact(
                        code,
                        bankName + " 전세대출 평균금리",
                        new BigDecimal(rate),
                        "%",
                        bankName + " 연 " + rate + "%",
                        "https://www.kfb.or.kr",
                        true))
                .when(facts)
                .require(code);
    }

    @Test
    void should_orderByRateAscending_soCheapestBankIsFirst() {
        stubBank("FCT-201", "토스뱅크", "3.34");
        stubBank("FCT-206", "신한은행", "3.86");
        stubBank("FCT-202", "KB국민은행", "3.39");

        BankLoanRateListResponse response = service.compare();

        assertThat(response.banks())
                .extracting(BankLoanRateResponse::bankName)
                .containsExactly("토스뱅크", "KB국민은행", "신한은행");
    }

    @Test
    void should_stripItemSuffix_soBankNameIsUsable() {
        stubBank("FCT-202", "KB국민은행", "3.39");

        assertThat(service.compare().banks().get(0).bankName()).isEqualTo("KB국민은행");
    }

    @Test
    void should_notRecomputeAverageFromListedBanks() {
        // 나열된 두 은행의 산술평균은 3.5 다. 공시 전체 평균 3.75 를 그대로 내보내야 한다 —
        // 직접 계산하면 은행연합회가 공시한 값과 갈라진다.
        stubBank("FCT-201", "토스뱅크", "3.00");
        stubBank("FCT-202", "KB국민은행", "4.00");
        doReturn(new Fact("FCT-101", "은행 전세대출 공시 전체 평균금리", new BigDecimal("3.75"), "%", "전체 평균 3.75%", null, false))
                .when(facts)
                .require("FCT-101");

        BankLoanRateListResponse response = service.compare();

        assertThat(response.publishedAverage()).isEqualByComparingTo("3.75");
        assertThat(response.publishedAverageText()).isEqualTo("전체 평균 3.75%");
    }

    @Test
    void should_markProvisional_when_factConfidenceIsReview() {
        // 주간 공시라 값이 바뀔 수 있다는 표시가 응답까지 따라와야 한다.
        stubBank("FCT-201", "토스뱅크", "3.34");

        assertThat(service.compare().banks().get(0).provisional()).isTrue();
    }

    @Test
    void should_skipOneBank_ratherThanFailWholeComparison_when_factIsUnusable() {
        stubBank("FCT-201", "토스뱅크", "3.34");
        doThrow(new UnusableFactException("FCT-202", Confidence.CONFLICT))
                .when(facts)
                .require("FCT-202");

        BankLoanRateListResponse response = service.compare();

        assertThat(response.banks()).extracting(BankLoanRateResponse::bankName).containsExactly("토스뱅크");
    }

    @Test
    void should_dropRateWithoutNumber_ratherThanTreatingItAsZero() {
        // 수치 없는 팩트를 0 으로 읽으면 그 은행이 가장 싼 것처럼 맨 위로 올라간다.
        stubBank("FCT-201", "토스뱅크", "3.34");
        doReturn(new Fact("FCT-202", "KB국민은행 전세대출 평균금리", null, null, "상품별 상이", null, true))
                .when(facts)
                .require("FCT-202");

        BankLoanRateListResponse response = service.compare();

        assertThat(response.banks()).extracting(BankLoanRateResponse::bankName).containsExactly("토스뱅크");
    }

    @Test
    void should_returnEmptyComparison_ratherThanThrow_when_noFactsAreSeeded() {
        BankLoanRateListResponse response = service.compare();

        assertThat(response.banks()).isEmpty();
        assertThat(response.publishedAverage()).isNull();
        assertThat(response.caution()).isNull();
    }

    @Test
    void should_carryAdRateCaution_soUiDoesNotShowAdvertisedRates() {
        doReturn(new Fact("FCT-102", "광고금리 사용 금지", null, null, "공시 평균금리를 쓸 것", null, false))
                .when(facts)
                .require("FCT-102");

        assertThat(service.compare().caution()).isEqualTo("공시 평균금리를 쓸 것");
    }
}
