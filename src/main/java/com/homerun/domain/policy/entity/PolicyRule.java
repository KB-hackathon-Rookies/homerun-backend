package com.homerun.domain.policy.entity;

import com.homerun.domain.policy.model.RuleDocument;
import com.homerun.domain.policy.type.PolicyRuleStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 정책 하나의 조건식 한 버전. ACTIVE 상태인 버전만 판정에 쓴다. */
@Entity
@Table(name = "policy_rule")
public class PolicyRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(nullable = false)
    private int version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule_json", nullable = false, columnDefinition = "jsonb")
    private RuleDocument ruleJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PolicyRuleStatus status;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "drafted_by", length = 50)
    private String draftedBy;

    @Column(name = "reviewed_by", length = 50)
    private String reviewedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PolicyRule() {}

    public Long getId() {
        return id;
    }

    public Long getPolicyId() {
        return policyId;
    }

    public int getVersion() {
        return version;
    }

    public RuleDocument getRuleJson() {
        return ruleJson;
    }

    public PolicyRuleStatus getStatus() {
        return status;
    }
}
