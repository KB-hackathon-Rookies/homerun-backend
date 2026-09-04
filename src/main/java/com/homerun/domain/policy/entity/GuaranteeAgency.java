package com.homerun.domain.policy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** 보증기관(HF/HUG/SGI) 마스터. 계획과 무관한 참조 데이터라 policy 처럼 여정 바깥에 있다. */
@Entity
@Table(name = "guarantee_agency")
public class GuaranteeAgency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "agency_type", nullable = false, length = 20)
    private String agencyType;

    @Column(name = "limit_basis", length = 50)
    private String limitBasis;

    @Column(name = "fee_rate_min")
    private BigDecimal feeRateMin;

    @Column(name = "fee_rate_max")
    private BigDecimal feeRateMax;

    protected GuaranteeAgency() {}

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getAgencyType() {
        return agencyType;
    }

    public String getLimitBasis() {
        return limitBasis;
    }

    public BigDecimal getFeeRateMin() {
        return feeRateMin;
    }

    public BigDecimal getFeeRateMax() {
        return feeRateMax;
    }
}
