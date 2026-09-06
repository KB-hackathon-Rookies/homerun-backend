package com.homerun.domain.policy.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.policy.dto.response.PolicyVerdictResponse;
import com.homerun.domain.policy.type.PolicyVerdictResult;
import com.homerun.domain.region.repository.RegionRepository;
import com.homerun.domain.region.type.PolicyArea;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import({TestcontainersConfiguration.class, RegionalPolicyIntegrationTest.FixedClock.class})
@Transactional
class RegionalPolicyIntegrationTest {
    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock regionalClock() {
            return Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    EntityManager em;

    @Autowired
    JeonsePolicyVerdictService service;

    @Autowired
    RegionRepository regions;

    private Long userId;
    private Long planId;

    @BeforeEach
    void setUp() {
        userId = ((Number) em.createNativeQuery(
                                "INSERT INTO app_user (auth_provider,provider_user_id,name) VALUES ('KAKAO','regional-'||nextval('app_user_id_seq'),'regional') RETURNING id")
                        .getSingleResult())
                .longValue();
        planId = ((Number)
                        em.createNativeQuery("INSERT INTO plan(user_id,lease_type) VALUES (:uid,'JEONSE') RETURNING id")
                                .setParameter("uid", userId)
                                .getSingleResult())
                .longValue();
        em.createNativeQuery(
                        "INSERT INTO plan_input(plan_id,hope_deposit,monthly_income,net_assets,available_cash,birth_date) VALUES (:pid,180000000,2000000,20000000,50000000,'1998-05-14')")
                .setParameter("pid", planId)
                .executeUpdate();
    }

    @ParameterizedTest
    @CsvSource({
        "JEONSE_SEOUL,120000000",
        "JEONSE_INCHEON,120000000",
        "JEONSE_GYEONGGI,120000000",
        "JEONSE_OTHER,80000000"
    })
    void should_selectRegionalLoanCapAndSaveEstimate(String code, long expected) {
        activate("JEONSE-GENERAL-BEOTIMMOK");
        inputRegion(code, 180_000_000L);
        var result = result("JEONSE-GENERAL-BEOTIMMOK");
        assertThat(result.verdict()).isEqualTo(PolicyVerdictResult.NEED_INFO); // 주택·중복대출 미확인은 통과시키지 않는다.
        assertThat(result.estimate().estimatedLoanAmount()).isEqualTo(expected);
        assertThat(result.estimate().ownFundsRequired()).isEqualTo(180_000_000L - expected);
        assertThat(result.estimate().rateMin()).isNull();
        em.flush();
        assertThat(((Number) em.createNativeQuery(
                                        "SELECT expected_amount FROM policy_verdict WHERE plan_id=:pid AND policy_id=(SELECT id FROM policy WHERE code='JEONSE-GENERAL-BEOTIMMOK')")
                                .setParameter("pid", planId)
                                .getSingleResult())
                        .longValue())
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "JEONSE_SEOUL,299999999,true",
        "JEONSE_SEOUL,300000000,true",
        "JEONSE_SEOUL,300000001,false",
        "JEONSE_OTHER,199999999,true",
        "JEONSE_OTHER,200000000,true",
        "JEONSE_OTHER,200000001,false"
    })
    void should_checkDepositBoundaryAndPersistSelectedFact(String code, long deposit, boolean met) {
        activate("JEONSE-GENERAL-BEOTIMMOK");
        inputRegion(code, deposit);
        var result = result("JEONSE-GENERAL-BEOTIMMOK");
        var basis = result.basis().stream()
                .filter(b -> b.code().equals("DEPOSIT_CAP"))
                .findFirst()
                .orElseThrow();
        assertThat(basis.isMet()).isEqualTo(met);
        assertThat(basis.factCode()).isEqualTo(code.equals("JEONSE_OTHER") ? "FCT-193" : "FCT-192");
        if (!met) {
            assertThat(result.verdict()).isEqualTo(PolicyVerdictResult.FAIL);
            assertThat(result.estimate()).isNull();
        }
    }

    @ParameterizedTest
    @CsvSource({"JEONSE_SEOUL,true", "JEONSE_INCHEON,false", "JEONSE_OTHER,false"})
    void should_limitSeoulSupportToSeoul(String code, boolean met) {
        activate("JEONSE-SEOUL-INTEREST-SUPPORT");
        inputRegion(code, 180_000_000L);
        var result = result("JEONSE-SEOUL-INTEREST-SUPPORT");
        assertThat(result.basis().stream()
                        .filter(b -> b.code().equals("REGION_TARGET"))
                        .findFirst()
                        .orElseThrow()
                        .isMet())
                .isEqualTo(met);
        if (met) assertThat(result.estimate().estimatedLoanAmount()).isEqualTo(162_000_000L);
        else assertThat(result.verdict()).isEqualTo(PolicyVerdictResult.FAIL);
    }

    @Test
    void should_notGuessRegionalCap_when_regionIsMissing() {
        activate("JEONSE-GENERAL-BEOTIMMOK");
        var result = result("JEONSE-GENERAL-BEOTIMMOK");
        assertThat(result.verdict()).isEqualTo(PolicyVerdictResult.NEED_INFO);
        assertThat(result.estimate()).isNull();
    }

    @Test
    void should_preserveDraftGateAndSeedUsableOptions() {
        // #140이 이 정책 v3를 실제로 ACTIVE 승격해서, 게이트 자체를 확인하려면 이 테스트
        // 안에서만 일부러 DRAFT로 되돌려 미승격 상태를 재현한다(트랜잭션 롤백되니 안전).
        em.createNativeQuery(
                        "UPDATE policy_rule SET status='DRAFT' WHERE status='ACTIVE' AND policy_id=(SELECT id FROM policy WHERE code='JEONSE-GENERAL-BEOTIMMOK')")
                .executeUpdate();
        em.clear();

        assertThat(result("JEONSE-GENERAL-BEOTIMMOK").basis())
                .extracting(b -> b.code())
                .containsExactly("RULE_NOT_ACTIVE");
        assertThat(regions.findAllByCodeInOrderByCodeAsc(
                        List.of("JEONSE_SEOUL", "JEONSE_INCHEON", "JEONSE_GYEONGGI", "JEONSE_OTHER")))
                .hasSize(4)
                .extracting(r -> r.getPolicyArea())
                .containsExactly(PolicyArea.CAPITAL, PolicyArea.CAPITAL, PolicyArea.NON_CAPITAL, PolicyArea.SEOUL);
    }

    private void activate(String code) {
        em.createNativeQuery(
                        "UPDATE policy_rule SET status='ACTIVE' WHERE version=3 AND policy_id=(SELECT id FROM policy WHERE code=:code)")
                .setParameter("code", code)
                .executeUpdate();
    }

    private void inputRegion(String code, long deposit) {
        em.createNativeQuery(
                        "UPDATE plan_input SET region_id=(SELECT id FROM region WHERE code=:code),hope_deposit=:deposit WHERE plan_id=:pid")
                .setParameter("code", code)
                .setParameter("deposit", deposit)
                .setParameter("pid", planId)
                .executeUpdate();
        em.clear();
    }

    private PolicyVerdictResponse result(String code) {
        return service.evaluate(userId, planId).results().stream()
                .filter(r -> r.policyCode().equals(code))
                .findFirst()
                .orElseThrow();
    }
}
