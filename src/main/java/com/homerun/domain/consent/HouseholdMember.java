package com.homerun.domain.consent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 가구원.
 *
 * <p>이름과 주민식별정보를 담지 않는다. 관계와 확인 여부만 관리한다(SEC-01-05). 정책 판정에
 * 필요한 것은 "동의를 받았는가"이지 그 사람이 누구인지가 아니다.
 */
@Entity
@Table(name = "household_member")
public class HouseholdMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(nullable = false)
    private String relation;

    @Column(name = "consent_required", nullable = false)
    private boolean consentRequired;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    protected HouseholdMember() {}

    public HouseholdMember(Long planId, String relation, boolean consentRequired) {
        this.planId = planId;
        this.relation = relation;
        this.consentRequired = consentRequired;
    }

    public Long id() {
        return id;
    }

    public Long planId() {
        return planId;
    }

    public String relation() {
        return relation;
    }

    public boolean consentRequired() {
        return consentRequired;
    }

    public Instant verifiedAt() {
        return verifiedAt;
    }

    /** 확인이 끝났는가. 끝나지 않았으면 자격을 확정하지 않는다(FAM-01-04). */
    public boolean verified() {
        return verifiedAt != null;
    }

    void markVerified(Instant at) {
        this.verifiedAt = at;
    }
}
