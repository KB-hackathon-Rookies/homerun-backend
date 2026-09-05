package com.homerun.domain.policy.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.policy.dto.response.BankLoanRateListResponse;
import com.homerun.domain.policy.dto.response.BankLoanRateResponse;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * V33 이 실제로 심은 은행별 금리로 전체 경로를 확인한다.
 *
 * <p>기대값을 응답에서 다시 뽑아 비교하지 않는다 — 그러면 아무것도 검증하지 않는 테스트가 된다.
 * 시드를 SQL 로 직접 읽어서 대조한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class BankLoanRateIntegrationTest {

    private final BankLoanRateService service;
    private final EntityManager em;

    BankLoanRateIntegrationTest(@Autowired BankLoanRateService service, @Autowired EntityManager em) {
        this.service = service;
        this.em = em;
    }

    @Test
    void should_listEverySeededBank_inRateOrderAgainstRealPostgres() {
        @SuppressWarnings("unchecked")
        List<Object[]> seeded = em.createNativeQuery("""
                        SELECT item, value_num FROM config_effective
                        WHERE fact_code BETWEEN 'FCT-201' AND 'FCT-208'
                        ORDER BY value_num ASC, item ASC
                        """).getResultList();

        BankLoanRateListResponse response = service.compare();

        assertThat(seeded).hasSize(8);
        assertThat(response.banks()).hasSize(seeded.size());
        // 시드에서 읽은 금리가 오름차순 그대로 응답에 실려야 한다.
        assertThat(response.banks())
                .extracting(BankLoanRateResponse::rate)
                .usingComparatorForType(Comparator.naturalOrder(), BigDecimal.class)
                .containsExactlyElementsOf(
                        seeded.stream().map(row -> (BigDecimal) row[1]).toList());
    }

    @Test
    void should_useSeededPublishedAverage_notTheMeanOfListedBanks() {
        BigDecimal seededAverage =
                (BigDecimal) em.createNativeQuery("SELECT value_num FROM config_effective WHERE fact_code = 'FCT-101'")
                        .getSingleResult();

        BankLoanRateListResponse response = service.compare();

        assertThat(response.publishedAverage()).isEqualByComparingTo(seededAverage);

        // 공시 전체 평균은 나열된 은행들의 산술평균과 다르다. 같아지면 누군가 직접 계산하도록
        // 바꾼 것이다 — 그러면 은행연합회 공시값과 갈라진다.
        BigDecimal meanOfListed = response.banks().stream()
                .map(BankLoanRateResponse::rate)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(response.banks().size()), 3, java.math.RoundingMode.HALF_UP);
        assertThat(response.publishedAverage()).isNotEqualByComparingTo(meanOfListed);
    }

    @Test
    void should_markSeededBankRatesProvisional_becauseTheyArePublishedWeekly() {
        // V33 이 REVIEW 로 심었다. 주간 공시라 "변경 가능" 표시가 응답까지 와야 한다.
        String confidence =
                (String) em.createNativeQuery("SELECT confidence FROM config_effective WHERE fact_code = 'FCT-201'")
                        .getSingleResult();

        assertThat(confidence).isEqualTo("REVIEW");
        assertThat(service.compare().banks()).allMatch(BankLoanRateResponse::provisional);
    }

    @Test
    void should_carrySeededAdRateCaution() {
        String seededCaution =
                (String) em.createNativeQuery("SELECT value_text FROM config_effective WHERE fact_code = 'FCT-102'")
                        .getSingleResult();

        assertThat(service.compare().caution()).isEqualTo(seededCaution);
    }
}
