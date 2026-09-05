package com.homerun.domain.property.entity;

import com.homerun.domain.policy.type.PolicyVerdictResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 매물 하나 × 상품 하나의 판정(요구사항 명세서 DR-10).
 *
 * <p>{@code policy_verdict} 와 나란히 존재하는 이유가 있다. 그쪽은 {@code UNIQUE (plan_id,
 * policy_id, rule_id)} 라 매물을 여러 개 판정하면 마지막 하나만 남는다. 명세서는 매물을 최대
 * 5개까지 나란히 비교하라고 하므로(FR-P1-02) 매물 축을 가진 자리가 따로 필요하다.
 *
 * <p>계획 단위 판정(1루 사람 조건)은 {@code policy_verdict}, 매물 단위 판정(2루 집 조건)은 여기다.
 */
@Entity
@Table(name = "property_policy_verdict")
public class PropertyPolicyVerdict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(name = "rule_id")
    private Long ruleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PolicyVerdictResult status;

    /** 미충족 조건 코드 전부. 첫 실패에서 멈추지 않는다(BR-12) — 무엇을 몇 개 고쳐야 하는지 알아야 한다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fail_codes", nullable = false, columnDefinition = "jsonb")
    private List<String> failCodes = List.of();

    /** 명세서 2-1 의 STEP 1~4 중 어디서 걸렸는가. 통과했으면 null 이다. */
    @Column(name = "fail_step")
    private Short failStep;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    protected PropertyPolicyVerdict() {}

    private PropertyPolicyVerdict(
            Long propertyId,
            Long policyId,
            Long ruleId,
            PolicyVerdictResult status,
            List<String> failCodes,
            Short failStep,
            Instant evaluatedAt) {
        this.propertyId = propertyId;
        this.policyId = policyId;
        this.ruleId = ruleId;
        this.status = status;
        this.failCodes = List.copyOf(failCodes);
        this.failStep = failStep;
        this.evaluatedAt = evaluatedAt;
    }

    public static PropertyPolicyVerdict create(
            Long propertyId,
            Long policyId,
            Long ruleId,
            PolicyVerdictResult status,
            List<String> failCodes,
            Short failStep,
            Instant evaluatedAt) {
        return new PropertyPolicyVerdict(propertyId, policyId, ruleId, status, failCodes, failStep, evaluatedAt);
    }

    /** 같은 매물·상품을 다시 판정하면 덮어쓴다. 이력이 아니라 지금 상태를 담는 자리다. */
    public void reevaluate(
            Long ruleId, PolicyVerdictResult status, List<String> failCodes, Short failStep, Instant evaluatedAt) {
        this.ruleId = ruleId;
        this.status = status;
        this.failCodes = List.copyOf(failCodes);
        this.failStep = failStep;
        this.evaluatedAt = evaluatedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getPropertyId() {
        return propertyId;
    }

    public Long getPolicyId() {
        return policyId;
    }

    public Long getRuleId() {
        return ruleId;
    }

    public PolicyVerdictResult getStatus() {
        return status;
    }

    public List<String> getFailCodes() {
        return failCodes;
    }

    public Short getFailStep() {
        return failStep;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }
}
