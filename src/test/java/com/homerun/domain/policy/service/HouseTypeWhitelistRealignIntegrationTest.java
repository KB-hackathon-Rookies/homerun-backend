package com.homerun.domain.policy.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.policy.entity.PolicyRule;
import com.homerun.domain.policy.model.RuleCondition;
import com.homerun.domain.policy.repository.PolicyRuleRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** V48(#193)이 HOUSE_TYPE 화이트리스트를 도메인 어휘로 맞췄는지 실제 Postgres 에서 확인한다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class HouseTypeWhitelistRealignIntegrationTest {

    private final PolicyRuleRepository rules;
    private final EntityManager em;

    HouseTypeWhitelistRealignIntegrationTest(@Autowired PolicyRuleRepository rules, @Autowired EntityManager em) {
        this.rules = rules;
        this.em = em;
    }

    @Test
    void should_useDomainVocabularyInRealignedWhitelist_notTheRealEstateApiVocabulary() {
        Long ruleId = ((Number) em.createNativeQuery("""
                        SELECT id FROM policy_rule
                        WHERE policy_id = (SELECT id FROM policy WHERE code = 'JEONSE-YOUTH-BEOTIMMOK')
                          AND version = 10
                        """).getSingleResult()).longValue();

        PolicyRule v5 = rules.findById(ruleId).orElseThrow();
        RuleCondition houseType = v5.getRuleJson().conditions().stream()
                .filter(condition -> "HOUSE_TYPE".equals(condition.code()))
                .findFirst()
                .orElseThrow();

        @SuppressWarnings("unchecked")
        List<String> allowed = (List<String>) houseType.value();
        // 자동판별한 연립다세대는 VILLA 로 저장되므로 화이트리스트도 VILLA 여야 통과한다.
        assertThat(allowed).containsExactly("APARTMENT", "OFFICETEL", "VILLA");
        assertThat(allowed).doesNotContain("ROW_HOUSE");
    }
}
