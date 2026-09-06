package com.homerun.domain.policy.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.policy.dto.response.CollateralLoanLimitListResponse;
import com.homerun.domain.policy.dto.response.CollateralLoanLimitResponse;
import com.homerun.domain.policy.model.CollateralType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * V51 이 심은 대출보증 한도로 담보별 계산이 실제로 도는지 확인한다(BR-19).
 *
 * <p>기대값을 응답에서 다시 뽑지 않고 시드를 SQL 로 직접 읽어 대조한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class CollateralLoanLimitIntegrationTest {

    private final CollateralLoanLimitService service;
    private final EntityManager em;

    private Long memberId;
    private Long planId;

    CollateralLoanLimitIntegrationTest(@Autowired CollateralLoanLimitService service, @Autowired EntityManager em) {
        this.service = service;
        this.em = em;
    }

    @Test
    void should_useSeededCollateralCaps_notReturnGuaranteeCaps() {
        // 대출보증 한도(FCT-211~213)와 반환보증 한도(FCT-056~058)는 다르다. 섞이면 한도가 틀린다.
        long hfCap = seededWon("FCT-211");
        long hugCap = seededWon("FCT-212");
        long sgiCap = seededWon("FCT-213");
        assertThat(hfCap).isEqualTo(222_000_000L);
        assertThat(hugCap).isEqualTo(400_000_000L);
        assertThat(sgiCap).isEqualTo(500_000_000L);

        // 보증금 10억으로 세 담보 모두 상한에 걸리게 한다(소득도 넉넉히).
        setUpPlan(1_000_000_000L, 30_000_000L, "1990-01-01");
        CollateralLoanLimitListResponse result = service.forPlan(memberId, planId);

        assertThat(of(result, CollateralType.HF).limit()).isEqualTo(hfCap);
        assertThat(of(result, CollateralType.HUG).limit()).isEqualTo(hugCap);
        assertThat(of(result, CollateralType.SGI).limit()).isEqualTo(sgiCap);
    }

    @Test
    void should_applyHug90_forYoungApplicant_againstRealPostgres() {
        setUpPlan(180_000_000L, 5_000_000L, "1993-01-01"); // 만 33세
        CollateralLoanLimitListResponse result = service.forPlan(memberId, planId);

        // 1.8억 × 0.9 = 1.62억.
        assertThat(of(result, CollateralType.HUG).limit()).isEqualTo(162_000_000L);
    }

    private CollateralLoanLimitResponse of(CollateralLoanLimitListResponse r, CollateralType t) {
        return r.collaterals().stream()
                .filter(c -> c.collateral() == t)
                .findFirst()
                .orElseThrow();
    }

    private long seededWon(String factCode) {
        return ((Number) em.createNativeQuery("SELECT value_num FROM config_effective WHERE fact_code = :c")
                        .setParameter("c", factCode)
                        .getSingleResult())
                .longValue();
    }

    private void setUpPlan(long deposit, long monthlyIncome, String birthDate) {
        memberId = ((Number) em.createNativeQuery("""
                        INSERT INTO app_user (auth_provider, provider_user_id, nickname)
                        VALUES ('KAKAO', 'collat-' || nextval('app_user_id_seq'), 'tester')
                        RETURNING id
                        """).getSingleResult()).longValue();
        planId = ((Number) em.createNativeQuery(
                                "INSERT INTO plan (user_id, lease_type) VALUES (:uid, 'JEONSE') RETURNING id")
                        .setParameter("uid", memberId)
                        .getSingleResult())
                .longValue();
        em.createNativeQuery("""
                        INSERT INTO plan_input (plan_id, hope_deposit, monthly_income, birth_date, marital_status,
                                                financial_data_confirmed)
                        VALUES (:pid, :dep, :inc, :birth, 'SINGLE', true)
                        """)
                .setParameter("pid", planId)
                .setParameter("dep", deposit)
                .setParameter("inc", monthlyIncome)
                .setParameter("birth", java.time.LocalDate.parse(birthDate))
                .executeUpdate();
    }
}
