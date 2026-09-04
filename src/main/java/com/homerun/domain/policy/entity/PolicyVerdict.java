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
import java.time.Instant;

/**
 * 계획(plan) × 정책(policy) 판정 결과 하나. 여정과 정책 마스터가 만나는 지점이다.
 *
 * <p>재판정은 새 행을 쌓지 않고 같은 (plan_id, policy_id, rule_id) 행을 덮어쓴다 — 이력이
 * 필요해지면 별도 이슈에서 다룬다. v1 은 예상 금액·금리를 계산하지 않는다(#80 과의 관계가
 * 정리되기 전까지는 판정만 한다).
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PolicyVerdictResult verdict;

    @Column(name = "is_selected", nullable = false)
    private boolean selected;

    @Column(name = "engine_version", nullable = false, length = 20)
    private String engineVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PolicyVerdict() {}

    private PolicyVerdict(Long planId, Long policyId, Long ruleId, PolicyVerdictResult verdict, String engineVersion) {
        this.planId = planId;
        this.policyId = policyId;
        this.ruleId = ruleId;
        this.verdict = verdict;
        this.selected = false;
        this.engineVersion = engineVersion;
        this.createdAt = Instant.now();
    }

    public static PolicyVerdict create(
            Long planId, Long policyId, Long ruleId, PolicyVerdictResult verdict, String engineVersion) {
        return new PolicyVerdict(planId, policyId, ruleId, verdict, engineVersion);
    }

    public void reevaluate(PolicyVerdictResult verdict, String engineVersion) {
        this.verdict = verdict;
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

    public PolicyVerdictResult getVerdict() {
        return verdict;
    }
}
