package com.homerun.domain.policy.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.policy.entity.PolicyRule;
import com.homerun.domain.policy.model.ConditionResult;
import com.homerun.domain.policy.model.RuleCondition;
import com.homerun.domain.policy.repository.PolicyRuleRepository;
import com.homerun.domain.property.entity.Property;
import com.homerun.domain.property.repository.PropertyRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * V47 이 심은 주택유형 조건(BR-09, #190)을 실제 Postgres 에서 확인한다.
 *
 * <p>시드는 기존 ACTIVE v3 의 rule_json 을 복사해 조건 하나를 붙이는 방식이라, v3 의 조건이
 * 전부 남아 있는지와 HOUSE_TYPE 이 실제로 평가되는지를 함께 본다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class HouseTypeConditionIntegrationTest {

    private static final List<String> JEONSE_CODES =
            List.of("JEONSE-YOUTH-BEOTIMMOK", "JEONSE-GENERAL-BEOTIMMOK", "JEONSE-SEOUL-INTEREST-SUPPORT");

    private final PolicyRuleEngine engine;
    private final PolicyRuleRepository rules;
    private final PropertyRepository properties;
    private final EntityManager em;

    HouseTypeConditionIntegrationTest(
            @Autowired PolicyRuleEngine engine,
            @Autowired PolicyRuleRepository rules,
            @Autowired PropertyRepository properties,
            @Autowired EntityManager em) {
        this.engine = engine;
        this.rules = rules;
        this.properties = properties;
        this.em = em;
    }

    @Test
    void should_seedDraftV4WithHouseTypeAppended_forAllThreeJeonseProducts() {
        for (String code : JEONSE_CODES) {
            PolicyRule v4 = draftV4(code);
            PolicyRule v3 = version(code, 3);

            // v3 의 조건이 그대로 남고 HOUSE_TYPE 이 맨 뒤에 붙었는지 — 복사가 어긋나면 검수가
            // 엉뚱한 조건식을 보게 된다.
            List<String> v3Codes = v3.getRuleJson().conditions().stream()
                    .map(RuleCondition::code)
                    .toList();
            List<String> v4Codes = v4.getRuleJson().conditions().stream()
                    .map(RuleCondition::code)
                    .toList();
            assertThat(v4Codes).as(code).startsWith(v3Codes.toArray(String[]::new));
            assertThat(v4Codes).as(code).endsWith("HOUSE_TYPE");
        }
    }

    @Test
    void should_evaluateSeededHouseTypeCondition_againstRealProperty() {
        RuleCondition seeded = houseTypeConditionOf(draftV4("JEONSE-YOUTH-BEOTIMMOK"));

        // 자동판별 유형은 통과, 목록 밖 유형(수동 입력이 생기면 들어올 값)은 미충족.
        assertThat(evaluate(seeded, "OFFICETEL").isMet()).isTrue();
        assertThat(evaluate(seeded, "DETACHED").isMet()).isFalse();
    }

    private ConditionResult evaluate(RuleCondition condition, String houseType) {
        Long planId = plan();
        Property property = properties.save(Property.candidate(
                planId,
                null,
                null,
                null,
                null,
                houseType,
                180_000_000L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));
        return engine.evaluate(
                        new com.homerun.domain.policy.model.RuleDocument("AND", List.of(condition)), null, property)
                .get(0);
    }

    private RuleCondition houseTypeConditionOf(PolicyRule rule) {
        return rule.getRuleJson().conditions().stream()
                .filter(condition -> "HOUSE_TYPE".equals(condition.code()))
                .findFirst()
                .orElseThrow();
    }

    private PolicyRule draftV4(String code) {
        return version(code, 4);
    }

    private PolicyRule version(String code, int version) {
        Long id = ((Number) em.createNativeQuery("""
                        SELECT id FROM policy_rule
                        WHERE policy_id = (SELECT id FROM policy WHERE code = :code) AND version = :version
                        """)
                        .setParameter("code", code)
                        .setParameter("version", version)
                        .getSingleResult())
                .longValue();
        return rules.findById(id).orElseThrow();
    }

    private Long plan() {
        Long memberId = ((Number) em.createNativeQuery("""
                        INSERT INTO app_user (auth_provider, provider_user_id, nickname)
                        VALUES ('KAKAO', 'house-type-' || nextval('app_user_id_seq'), 'tester')
                        RETURNING id
                        """).getSingleResult()).longValue();
        return ((Number) em.createNativeQuery(
                                "INSERT INTO plan (user_id, lease_type) VALUES (:uid, 'JEONSE') RETURNING id")
                        .setParameter("uid", memberId)
                        .getSingleResult())
                .longValue();
    }
}
