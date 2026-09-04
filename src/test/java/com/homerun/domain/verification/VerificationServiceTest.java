package com.homerun.domain.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.policy.repository.PolicyRepository;
import com.homerun.domain.policy.repository.PolicyVerdictRepository;
import com.homerun.domain.policy.repository.VerdictBasisRepository;
import com.homerun.domain.verification.dto.response.VerificationDtos.PendingCondition;
import com.homerun.domain.verification.dto.response.VerificationDtos.PendingConditionList;
import com.homerun.domain.verification.service.VerificationService;
import com.homerun.global.exception.BusinessException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class VerificationServiceTest {

    private final EntityManager em;
    private final VerificationService service;

    private Long ownerId;
    private Long planId;

    VerificationServiceTest(
            @Autowired PlanRepository plans,
            @Autowired PolicyVerdictRepository verdicts,
            @Autowired VerdictBasisRepository basisRepository,
            @Autowired PolicyRepository policies,
            @Autowired EntityManager em) {
        this.em = em;
        this.service = new VerificationService(plans, verdicts, basisRepository, policies);
    }

    @BeforeEach
    void setUpPlan() {
        ownerId = (Long) em.createNativeQuery("""
                        INSERT INTO app_user (auth_provider, provider_user_id, nickname)
                        VALUES ('KAKAO', 'test-' || nextval('app_user_id_seq'), 'tester')
                        RETURNING id
                        """).getSingleResult();
        planId = (Long)
                em.createNativeQuery("INSERT INTO plan (user_id, lease_type) VALUES (:uid, 'JEONSE') RETURNING id")
                        .setParameter("uid", ownerId)
                        .getSingleResult();
        em.flush();
        em.clear();
    }

    private Long newPolicy(String name) {
        return (Long) em.createNativeQuery("""
                        INSERT INTO policy (code, name, category, operator, source)
                        VALUES ('P' || nextval('policy_id_seq'), :name, 'JEONSE_LOAN', '주택도시기금', 'MANUAL')
                        RETURNING id
                        """).setParameter("name", name).getSingleResult();
    }

    private Long newVerdict(Long policyId, String verdict) {
        return (Long) em.createNativeQuery("""
                        INSERT INTO policy_verdict (plan_id, policy_id, verdict, engine_version)
                        VALUES (:planId, :policyId, :verdict, 'v1')
                        RETURNING id
                        """)
                .setParameter("planId", planId)
                .setParameter("policyId", policyId)
                .setParameter("verdict", verdict)
                .getSingleResult();
    }

    private void newBasis(
            Long verdictId, String code, String label, String requiredText, Boolean isMet, String sourceUrl) {
        em.createNativeQuery("""
                        INSERT INTO verdict_basis (verdict_id, condition_code, condition_label, required_text, is_met, source_url)
                        VALUES (:verdictId, :code, :label, :requiredText, :isMet, :sourceUrl)
                        """)
                .setParameter("verdictId", verdictId)
                .setParameter("code", code)
                .setParameter("label", label)
                .setParameter("requiredText", requiredText)
                .setParameter("isMet", isMet)
                .setParameter("sourceUrl", sourceUrl)
                .executeUpdate();
    }

    @Test
    @DisplayName("NEED_INFO 조건만 골라 정책 정보와 함께 돌려준다")
    void should_return_only_need_info_conditions() {
        Long policyId = newPolicy("청년전용 버팀목전세자금대출");
        Long verdictId = newVerdict(policyId, "NEED_INFO");
        newBasis(verdictId, "INCOME_LIMIT", "소득 기준", "연 5,000만원 이하", true, null);
        newBasis(verdictId, "NET_ASSET_LIMIT", "순자산 기준", "3.45억 이하", null, "https://nhuf.molit.go.kr");
        em.flush();
        em.clear();

        PendingConditionList result = service.listPending(ownerId, planId);

        assertThat(result.conditions()).hasSize(1);
        PendingCondition condition = result.conditions().get(0);
        assertThat(condition.conditionCode()).isEqualTo("NET_ASSET_LIMIT");
        assertThat(condition.policyCode()).isNotBlank();
        assertThat(condition.sourceUrl()).isEqualTo("https://nhuf.molit.go.kr");
    }

    @Test
    @DisplayName("모든 조건이 판정 완료면 빈 목록이다")
    void should_return_empty_when_nothing_pending() {
        Long policyId = newPolicy("청년전용 버팀목전세자금대출");
        Long verdictId = newVerdict(policyId, "PASS");
        newBasis(verdictId, "INCOME_LIMIT", "소득 기준", "연 5,000만원 이하", true, null);
        em.flush();
        em.clear();

        PendingConditionList result = service.listPending(ownerId, planId);

        assertThat(result.conditions()).isEmpty();
    }

    @Test
    @DisplayName("판정 자체가 없는 계획도 빈 목록이다")
    void should_return_empty_when_no_verdict_yet() {
        PendingConditionList result = service.listPending(ownerId, planId);

        assertThat(result.conditions()).isEmpty();
    }

    @Test
    @DisplayName("남의 계획은 조회할 수 없다")
    void should_reject_other_members_plan() {
        Long stranger = ownerId + 9999;

        assertThatThrownBy(() -> service.listPending(stranger, planId)).isInstanceOf(BusinessException.class);
    }
}
