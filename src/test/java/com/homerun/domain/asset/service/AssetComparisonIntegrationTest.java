package com.homerun.domain.asset.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.asset.dto.request.AssetComparisonRequest;
import com.homerun.domain.asset.dto.response.AssetComparisonResponse;
import com.homerun.domain.asset.type.AssetType;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 config_effective 시드값(FCT-078)을 직접 읽어서 CLAUDE.md 에 적힌 예시("2,000만 인출 시
 * 세금 330만")와 맞는지 확인한다 — mock 이 아니라 실제 시드가 문서와 같은지 보는 것이라
 * AssetComparisonServiceTest 와 검증 대상이 다르다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class AssetComparisonIntegrationTest {

    private final AssetComparisonService service;
    private final EntityManager em;

    private Long memberId;
    private Long planId;

    AssetComparisonIntegrationTest(@Autowired AssetComparisonService service, @Autowired EntityManager em) {
        this.service = service;
        this.em = em;
    }

    @BeforeEach
    void setUp() {
        memberId = ((Number) em.createNativeQuery("""
                        INSERT INTO app_user (auth_provider, provider_user_id, name)
                        VALUES ('KAKAO', 'asset-test-' || nextval('app_user_id_seq'), 'tester')
                        RETURNING id
                        """).getSingleResult()).longValue();
        planId = ((Number) em.createNativeQuery(
                                "INSERT INTO plan (user_id, lease_type) VALUES (:uid, 'JEONSE') RETURNING id")
                        .setParameter("uid", memberId)
                        .getSingleResult())
                .longValue();
    }

    @Test
    void should_matchDocumentedExample_when_readingRealFct078() {
        BigDecimal seededRate =
                (BigDecimal) em.createNativeQuery("SELECT value_num FROM config_effective WHERE fact_code = 'FCT-078'")
                        .getSingleResult();
        assertThat(seededRate).isEqualByComparingTo("16.5");

        AssetComparisonResponse response = service.compare(memberId, planId, request(AssetType.IRP, 20_000_000L));

        assertThat(response.results().get(0).taxPenaltyAmount()).isEqualTo(3_300_000L);
        assertThat(response.results().get(0).netAmount()).isEqualTo(16_700_000L);
    }

    @Test
    void should_persistAssetOptionRow_when_comparing() {
        service.compare(memberId, planId, request(AssetType.HOUSING_SUBSCRIPTION, 10_000_000L));
        em.flush();
        em.clear();

        Object[] row =
                (Object[]) em.createNativeQuery("""
                        SELECT asset_type, withdraw_amount, tax_penalty_amount, is_reversible
                        FROM asset_option WHERE plan_id = :pid
                        """).setParameter("pid", planId).getSingleResult();

        assertThat(row[0]).isEqualTo("HOUSING_SUBSCRIPTION");
        assertThat(((Number) row[1]).longValue()).isEqualTo(10_000_000L);
        assertThat(row[2]).isNull(); // 청약은 세금 계산 안 함
        assertThat(row[3]).isEqualTo(false); // is_reversible - 항상 비가역
    }

    private AssetComparisonRequest request(AssetType assetType, Long withdrawAmount) {
        return new AssetComparisonRequest(
                List.of(new AssetComparisonRequest.Entry(assetType, withdrawAmount, withdrawAmount)));
    }
}
