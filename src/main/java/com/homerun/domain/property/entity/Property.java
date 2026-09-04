package com.homerun.domain.property.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 매물 한 건.
 *
 * <p>{@code candidate(...)}로 계획에 속한 후보 매물과 분석 시점의 사실 정보를 저장한다.
 * {@link #select()}와 {@link #deselect()}로 최종 매물 선택 상태를 관리한다.
 */
@Entity
@Table(name = "property")
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "legal_district_code", length = 10)
    private String legalDistrictCode;

    @Column(length = 300)
    private String address;

    @Column(name = "road_address", length = 300)
    private String roadAddress;

    @Column(name = "building_name", length = 200)
    private String buildingName;

    @Column(name = "house_type", length = 30)
    private String houseType;

    private Long deposit;

    @Column(name = "market_price")
    private Long marketPrice;

    @Column(name = "official_price")
    private Long officialPrice;

    @Column(name = "senior_debt")
    private Long seniorDebt;

    @Column(name = "owner_matches")
    private Boolean ownerMatches;

    @Column(name = "is_violation_building")
    private Boolean violationBuilding;

    @Column(name = "is_trust_registered")
    private Boolean trustRegistered;

    @Column(name = "is_multi_household")
    private Boolean multiHousehold;

    @Column(name = "landlord_tax_unpaid")
    private Boolean landlordTaxUnpaid;

    @Column(name = "is_selected", nullable = false)
    private boolean selected;

    @Column(name = "analyzed_at")
    private Instant analyzedAt;

    protected Property() {}

    public static Property candidate(
            Long planId,
            String legalDistrictCode,
            String address,
            String roadAddress,
            String buildingName,
            String houseType,
            Long deposit,
            Long marketPrice,
            Long officialPrice,
            Long seniorDebt,
            Boolean ownerMatches,
            Boolean violationBuilding,
            Boolean trustRegistered,
            Boolean multiHousehold,
            Boolean landlordTaxUnpaid,
            Instant analyzedAt) {
        Property property = new Property();
        property.planId = planId;
        property.legalDistrictCode = legalDistrictCode;
        property.address = address;
        property.roadAddress = roadAddress;
        property.buildingName = buildingName;
        property.houseType = houseType;
        property.deposit = deposit;
        property.marketPrice = marketPrice;
        property.officialPrice = officialPrice;
        property.seniorDebt = seniorDebt;
        property.ownerMatches = ownerMatches;
        property.violationBuilding = violationBuilding;
        property.trustRegistered = trustRegistered;
        property.multiHousehold = multiHousehold;
        property.landlordTaxUnpaid = landlordTaxUnpaid;
        property.analyzedAt = analyzedAt;
        return property;
    }

    public void select() {
        selected = true;
    }

    public void deselect() {
        selected = false;
    }

    public Long getId() {
        return id;
    }

    public Long getPlanId() {
        return planId;
    }

    public String getAddress() {
        return address;
    }

    public String getRoadAddress() {
        return roadAddress;
    }

    public String getBuildingName() {
        return buildingName;
    }

    public String getHouseType() {
        return houseType;
    }

    public Long getDeposit() {
        return deposit;
    }

    public Long getOfficialPrice() {
        return officialPrice;
    }

    public boolean isSelected() {
        return selected;
    }

    public Instant getAnalyzedAt() {
        return analyzedAt;
    }
}
