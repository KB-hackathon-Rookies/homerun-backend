package com.homerun.domain.policy.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.policy.dto.response.ConditionBasisResponse;
import com.homerun.domain.policy.dto.response.JeonsePolicyVerdictListResponse;
import com.homerun.domain.policy.dto.response.PolicyVerdictResponse;
import com.homerun.domain.policy.type.PolicyVerdictResult;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * 버팀목 중복대출 금지(NO_DUPLICATE_LOAN)의 세 갈래를 실 시드 규칙으로 확인한다.
 *
 * <p>조건식을 테스트에서 새로 심지 않는 것이 요점이다 — V66 이 심은 ACTIVE version 11 을 그대로
 * 돌려야 "PASS 분기가 아예 없어서 구조적으로 FAIL 아니면 NEED_INFO 만 나온다"는 원래 문제를
 * 다시 잡는다. 나머지 조건은 전부 통과하도록 채워서 NO_DUPLICATE_LOAN 하나만 결과를 가른다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ProhibitedLoanConfirmationIntegrationTest {

    private static final List<String> BEOTIMMOK_CODES = List.of("JEONSE-YOUTH-BEOTIMMOK", "JEONSE-GENERAL-BEOTIMMOK");

    private final JeonsePolicyVerdictService service;
    private final EntityManager em;

    private Long memberId;
    private Long planId;
    private Long propertyId;

    ProhibitedLoanConfirmationIntegrationTest(
            @Autowired JeonsePolicyVerdictService service, @Autowired EntityManager em) {
        this.service = service;
        this.em = em;
    }

    @BeforeEach
    void setUp() {
        memberId = ((Number) em.createNativeQuery("""
                        INSERT INTO app_user (auth_provider, provider_user_id, name)
                        VALUES ('KAKAO', 'prohibited-loan-' || nextval('app_user_id_seq'), 'tester')
                        RETURNING id
                        """).getSingleResult()).longValue();
        planId = ((Number) em.createNativeQuery(
                                "INSERT INTO plan (user_id, lease_type) VALUES (:uid, 'JEONSE') RETURNING id")
                        .setParameter("uid", memberId)
                        .getSingleResult())
                .longValue();
        Long regionId = ((Number) em.createNativeQuery("SELECT id FROM region WHERE code = 'JEONSE_GYEONGGI'")
                        .getSingleResult())
                .longValue();

        // NO_DUPLICATE_LOAN 을 뺀 version 11 의 모든 조건을 통과하는 값 — 연소득 4,800만(FCT-003
        // 5,000만 이하), 순자산 1억(FCT-170 3.45억 이하), 보증금 1.5억(FCT-175·FCT-192 3억 이하),
        // 만 30세(FCT-174 만 34세 이하). income_source 는 비워 오픈뱅킹 확인 요구를 피한다.
        em.createNativeQuery("""
                        INSERT INTO plan_input
                            (plan_id, household_homeless, householder_status, has_existing_jeonse_loan,
                             birth_date, monthly_income, net_assets, hope_deposit, region_id)
                        VALUES (:pid, true, 'CURRENT', false, :birthDate, 4000000, 100000000, 150000000, :regionId)
                        """)
                .setParameter("pid", planId)
                .setParameter("birthDate", LocalDate.now().minusYears(30))
                .setParameter("regionId", regionId)
                .executeUpdate();

        propertyId =
                ((Number) em.createNativeQuery("""
                        INSERT INTO property
                            (plan_id, deposit, exclusive_area, is_violation_building,
                             is_multi_household, is_non_residential)
                        VALUES (:pid, 150000000, 42.35, false, false, false)
                        RETURNING id
                        """).setParameter("pid", planId).getSingleResult()).longValue();
    }

    @Test
    @DisplayName("기존 전세자금대출이 있다고 답하면 중복대출 조건은 불충족이고 버팀목은 FAIL 이다")
    void should_failBeotimmok_when_existingJeonseLoanIsTrue() {
        setProhibitedLoan(true, true); // 확인값이 true 여도 실제 대출이 있으면 불충족이 이긴다

        JeonsePolicyVerdictListResponse response = service.evaluate(memberId, planId, propertyId);

        for (String code : BEOTIMMOK_CODES) {
            PolicyVerdictResponse verdict = verdictOf(response, code);
            assertThat(verdict.verdict()).as(code).isEqualTo(PolicyVerdictResult.FAIL);
            assertThat(duplicateLoanBasis(verdict).isMet()).as(code).isFalse();
        }
    }

    /**
     * 이 테스트가 엔진의 PASS 분기 없이는 통과하지 못한다는 점이 핵심이다 — 분기를 빼면
     * NO_DUPLICATE_LOAN 이 NEED_INFO 로 떨어져 전체 판정도 NEED_INFO 가 된다.
     */
    @Test
    @DisplayName("대출이 없고 나머지 금지범위까지 확인했다고 답하면 두 버팀목 모두 PASS 다")
    void should_passBothBeotimmok_when_noLoanAndProhibitedLoanConfirmed() {
        setProhibitedLoan(false, true);

        JeonsePolicyVerdictListResponse response = service.evaluate(memberId, planId, propertyId);

        for (String code : BEOTIMMOK_CODES) {
            PolicyVerdictResponse verdict = verdictOf(response, code);
            assertThat(verdict.verdict())
                    .as("%s (미충족·추가확인 조건: %s)", code, verdict.missingFields())
                    .isEqualTo(PolicyVerdictResult.PASS);
            assertThat(duplicateLoanBasis(verdict).isMet()).as(code).isTrue();
            assertThat(verdict.missingFields()).as(code).doesNotContain("NO_DUPLICATE_LOAN");
        }
    }

    @ParameterizedTest(name = "prohibited_loan_confirmed = {0}")
    @ValueSource(strings = {"null", "false"})
    @DisplayName("확인하지 않았으면 예전처럼 NEED_INFO 로 남고 임의로 PASS 시키지 않는다")
    void should_needInfo_when_prohibitedLoanNotConfirmed(String confirmed) {
        setProhibitedLoan(false, "null".equals(confirmed) ? null : Boolean.FALSE);

        JeonsePolicyVerdictListResponse response = service.evaluate(memberId, planId, propertyId);

        for (String code : BEOTIMMOK_CODES) {
            PolicyVerdictResponse verdict = verdictOf(response, code);
            assertThat(verdict.verdict()).as(code).isEqualTo(PolicyVerdictResult.NEED_INFO);
            assertThat(duplicateLoanBasis(verdict).isMet()).as(code).isNull();
            assertThat(verdict.missingFields()).as(code).contains("NO_DUPLICATE_LOAN");
        }
    }

    private void setProhibitedLoan(Boolean existingLoan, Boolean confirmed) {
        em.createNativeQuery("""
                        UPDATE plan_input
                        SET has_existing_jeonse_loan = :existingLoan, prohibited_loan_confirmed = :confirmed
                        WHERE plan_id = :pid
                        """)
                .setParameter("existingLoan", existingLoan)
                .setParameter("confirmed", confirmed)
                .setParameter("pid", planId)
                .executeUpdate();
        em.flush();
        em.clear();
    }

    private PolicyVerdictResponse verdictOf(JeonsePolicyVerdictListResponse response, String policyCode) {
        return response.results().stream()
                .filter(result -> result.policyCode().equals(policyCode))
                .findFirst()
                .orElseThrow();
    }

    private ConditionBasisResponse duplicateLoanBasis(PolicyVerdictResponse verdict) {
        return verdict.basis().stream()
                .filter(basis -> basis.code().equals("NO_DUPLICATE_LOAN"))
                .findFirst()
                .orElseThrow();
    }
}
