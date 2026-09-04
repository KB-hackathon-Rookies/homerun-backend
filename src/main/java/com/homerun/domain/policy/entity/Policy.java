package com.homerun.domain.policy.entity;

import com.homerun.domain.policy.type.PolicyStatus;
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

/** 정책·상품 마스터. 계획(plan)과 무관한 카탈로그라 여정 바깥에 있다. */
@Entity
@Table(name = "policy")
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(length = 10)
    private String tier;

    @Column(length = 100)
    private String operator;

    @Column(name = "guarantee_agency_id")
    private Long guaranteeAgencyId;

    @Column(nullable = false, length = 30)
    private String source;

    @Column(name = "external_id", length = 100)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PolicyStatus status;

    @Column(name = "detail_url", columnDefinition = "text")
    private String detailUrl;

    @Column(name = "apply_url", columnDefinition = "text")
    private String applyUrl;

    @Column(name = "discontinued_at")
    private LocalDate discontinuedAt;

    @Column(name = "replaced_by_id")
    private Long replacedById;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Policy() {}

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getTier() {
        return tier;
    }

    public Long getGuaranteeAgencyId() {
        return guaranteeAgencyId;
    }

    public PolicyStatus getStatus() {
        return status;
    }

    public String getDetailUrl() {
        return detailUrl;
    }
}
