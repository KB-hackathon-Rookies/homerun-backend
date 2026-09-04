package com.homerun.domain.policy.entity;

import com.homerun.domain.policy.type.PolicyVerdictResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 계획(plan) × 정책(policy) 판정 결과 하나. 여정과 정책 마스터가 만나는 지점이다.
 *
 * <p>재판정은 새 행을 쌓지 않고 같은 (plan_id, policy_id, rule_id) 행을 덮어쓴다 — 이력이
 * 필요해지면 별도 이슈에서 다룬다. 매물 기준 판정(반환보증)은 {@code property_id} 에 남지만
 * UNIQUE 제약에는 안 들어가 있어 매물별 이력은 못 남긴다(#89).
 *
 * <p>{@code expected_rate} 컬럼은 하나뿐이라 최저·최고 금리 범위를 그대로 못 담는다. 최저금리를
 * 저장하고, 범위 전체는 응답 DTO({@code ExpectedEstimate})로만 노출한다(#95).
 */
@Entity
@Table(name = "policy_verdict")
public class PolicyVerdict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(name = "rule_id")
    private Long ruleId;

    /** 반환보증처럼 매물 기준으로 판정한 경우에만 채워진다(#89). plan_input 기반 정책은 계속
     * NULL 이다 — UNIQUE 제약(plan_id, policy_id, rule_id)에는 안 들어가 있으니 매물을 여러
     * 개 비교해도 가장 최근 판정 하나만 남는다는 점을 호출하는 쪽이 알아야 한다. */
    @Column(name = "property_id")
    private Long propertyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PolicyVerdictResult verdict;

    @Column(name = "expected_amount")
    private Long expectedAmount;

    @Column(name = "expected_rate", precision = 6, scale = 3)
    private BigDecimal expectedRate;

    @Column(name = "is_selected", nullable = false)
    private boolean selected;

    @Column(name = "engine_version", nullable = false, length = 20)
    private String engineVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PolicyVerdict() {}

    private PolicyVerdict(
            Long planId,
            Long policyId,
            Long ruleId,
            Long propertyId,
            PolicyVerdictResult verdict,
            Long expectedAmount,
            BigDecimal expectedRate,
            String engineVersion) {
        this.planId = planId;
        this.policyId = policyId;
        this.ruleId = ruleId;
        this.propertyId = propertyId;
        this.verdict = verdict;
        this.expectedAmount = expectedAmount;
        this.expectedRate = expectedRate;
        this.selected = false;
        this.engineVersion = engineVersion;
        this.createdAt = Instant.now();
    }

    public static PolicyVerdict create(
            Long planId,
            Long policyId,
            Long ruleId,
            Long propertyId,
            PolicyVerdictResult verdict,
            Long expectedAmount,
            BigDecimal expectedRate,
            String engineVersion) {
        return new PolicyVerdict(
                planId, policyId, ruleId, propertyId, verdict, expectedAmount, expectedRate, engineVersion);
    }

    public void reevaluate(
            PolicyVerdictResult verdict,
            Long propertyId,
            Long expectedAmount,
            BigDecimal expectedRate,
            String engineVersion) {
        this.verdict = verdict;
        this.propertyId = propertyId;
        this.expectedAmount = expectedAmount;
        this.expectedRate = expectedRate;
        this.engineVersion = engineVersion;
    }

    public Long getId() {
        return id;
    }

    public Long getPlanId() {
        return planId;
    }

    public Long getPolicyId() {
        return policyId;
    }

    public Long getRuleId() {
        return ruleId;
    }

    public Long getPropertyId() {
        return propertyId;
    }

    public PolicyVerdictResult getVerdict() {
        return verdict;
    }
}
