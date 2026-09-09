package com.homerun.domain.openbanking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.openbanking.dto.response.ExternalDataCoverage;
import com.homerun.domain.openbanking.dto.response.FinancialSummaryStatus;
import com.homerun.domain.openbanking.dto.response.OpenBankingFinancialSummaryResponse;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class FinancialSnapshotPersistenceIntegrationTest {

    private final FinancialSnapshotService service;
    private final EntityManager entityManager;
    private Long memberId;

    FinancialSnapshotPersistenceIntegrationTest(
            @Autowired FinancialSnapshotService service, @Autowired EntityManager entityManager) {
        this.service = service;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void setUpMember() {
        memberId = ((Number) entityManager.createNativeQuery("""
                        INSERT INTO app_user (auth_provider, provider_user_id, name)
                        VALUES ('KAKAO', 'snapshot-test-' || nextval('app_user_id_seq'), 'tester')
                        RETURNING id
                        """).getSingleResult()).longValue();
    }

    @Test
    void should_persistSnapshotAndReturnTheNewestCapture() {
        service.capture(memberId, summary(Instant.parse("2026-09-05T00:00:00Z"), "15000000"), 2_900_000L);
        service.capture(memberId, summary(Instant.parse("2026-09-05T01:00:00Z"), "16000000"), 3_000_000L);
        entityManager.flush();
        entityManager.clear();

        var latest = service.latest(memberId);

        assertThat(latest.financialAsset()).isEqualTo(16_000_000L);
        assertThat(latest.monthlyIncome()).isEqualTo(3_000_000L);
        assertThat(latest.monthlyDebtPayment()).isEqualTo(300_000L);
        assertThat(latest.capturedAt()).isEqualTo(Instant.parse("2026-09-05T01:00:00Z"));
    }

    private OpenBankingFinancialSummaryResponse summary(Instant fetchedAt, String balance) {
        return new OpenBankingFinancialSummaryResponse(
                FinancialSummaryStatus.COMPLETE,
                new ExternalDataCoverage(2, 2),
                new ExternalDataCoverage(2, 2),
                new ExternalDataCoverage(2, 2),
                2,
                new BigDecimal(balance),
                new BigDecimal(balance),
                List.of(),
                new BigDecimal("3000000"),
                3,
                List.of(),
                new BigDecimal("300000"),
                2,
                0,
                false,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 8, 31),
                3,
                List.of("004", "020"),
                List.of(),
                List.of(),
                fetchedAt);
    }
}
