package com.homerun.domain.policy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 판정 근거 조건 하나. 금액이 아니라 충족 여부만 남긴다(SEC-01-01) — {@code isMet} 이 null 이면
 * 아직 확인 못 한 것이지 불충족이 아니다.
 */
@Entity
@Table(name = "verdict_basis")
public class VerdictBasis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "verdict_id", nullable = false)
    private Long verdictId;

    @Column(name = "condition_code", nullable = false, length = 50)
    private String conditionCode;

    @Column(name = "condition_label", nullable = false, length = 200)
    private String conditionLabel;

    @Column(name = "required_text", length = 200)
    private String requiredText;

    @Column(name = "is_met")
    private Boolean met;

    @Column(name = "fact_code", length = 20)
    private String factCode;

    @Column(name = "source_url", columnDefinition = "text")
    private String sourceUrl;

    @Column(name = "snapshot_at")
    private Instant snapshotAt;

    protected VerdictBasis() {}

    private VerdictBasis(
            Long verdictId,
            String conditionCode,
            String conditionLabel,
            String requiredText,
            Boolean met,
            String factCode,
            String sourceUrl) {
        this.verdictId = verdictId;
        this.conditionCode = conditionCode;
        this.conditionLabel = conditionLabel;
        this.requiredText = requiredText;
        this.met = met;
        this.factCode = factCode;
        this.sourceUrl = sourceUrl;
        this.snapshotAt = Instant.now();
    }

    public static VerdictBasis create(
            Long verdictId,
            String conditionCode,
            String conditionLabel,
            String requiredText,
            Boolean met,
            String factCode,
            String sourceUrl) {
        return new VerdictBasis(verdictId, conditionCode, conditionLabel, requiredText, met, factCode, sourceUrl);
    }

    public String getConditionCode() {
        return conditionCode;
    }

    public Boolean getMet() {
        return met;
    }
}
