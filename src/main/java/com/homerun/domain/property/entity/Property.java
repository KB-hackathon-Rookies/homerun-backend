package com.homerun.domain.property.entity;

import com.homerun.domain.property.type.DataSource;
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
import java.time.LocalDate;

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

    @Column(name = "is_leasehold_registered")
    private Boolean leaseholdRegistered;

    @Column(name = "has_seizure_or_disposition_restriction")
    private Boolean seizureOrDispositionRestricted;

    @Column(name = "is_auction_in_progress")
    private Boolean auctionInProgress;

    @Column(name = "senior_debt_registered_at")
    private LocalDate seniorDebtRegisteredAt;

    @Column(name = "jibun", length = 50)
    private String jibun;

    /** 동·호수. 집합건물은 여기까지 정확해야 등기부가 맞다(FR-P2-02). */
    @Column(name = "detail_address", length = 100)
    private String detailAddress;

    /** 전용면적(㎡). BR-09 의 85㎡ 판정 입력. null 이면 아직 확보하지 못한 것이다. */
    @Column(name = "exclusive_area", precision = 8, scale = 2)
    private BigDecimal exclusiveArea;

    @Enumerated(EnumType.STRING)
    @Column(name = "area_source", length = 10)
    private DataSource areaSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "house_type_source", length = 10)
    private DataSource houseTypeSource;

    /** 실거래 목록에서 이 집과 일치하는 거래를 찾았는가. */
    @Column(name = "price_matched")
    private Boolean priceMatched;

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

    /**
     * 주소 상세와 조회로 확보한 값을 채운다. 면적을 실거래에서 못 가져왔으면 {@code exclusiveArea}
     * 가 null 이고 {@code areaSource} 도 null 이다 — 값이 없다는 것과 직접 입력했다는 것을 섞지
     * 않는다.
     */
    public void recordSourcedFacts(
            String jibun,
            String detailAddress,
            BigDecimal exclusiveArea,
            DataSource areaSource,
            DataSource houseTypeSource,
            Boolean priceMatched) {
        this.jibun = jibun;
        this.detailAddress = detailAddress;
        this.exclusiveArea = exclusiveArea;
        this.areaSource = exclusiveArea == null ? null : areaSource;
        this.houseTypeSource = houseTypeSource;
        this.priceMatched = priceMatched;
    }

    public String getJibun() {
        return jibun;
    }

    public String getDetailAddress() {
        return detailAddress;
    }

    public BigDecimal getExclusiveArea() {
        return exclusiveArea;
    }

    public DataSource getAreaSource() {
        return areaSource;
    }

    public DataSource getHouseTypeSource() {
        return houseTypeSource;
    }

    public Boolean getPriceMatched() {
        return priceMatched;
    }

    public void recordRegistryRisks(
            Boolean leaseholdRegistered,
            Boolean seizureOrDispositionRestricted,
            Boolean auctionInProgress,
            LocalDate seniorDebtRegisteredAt) {
        this.leaseholdRegistered = leaseholdRegistered;
        this.seizureOrDispositionRestricted = seizureOrDispositionRestricted;
        this.auctionInProgress = auctionInProgress;
        this.seniorDebtRegisteredAt = seniorDebtRegisteredAt;
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

    public Boolean getViolationBuilding() {
        return violationBuilding;
    }

    public Boolean getMultiHousehold() {
        return multiHousehold;
    }

    public boolean isSelected() {
        return selected;
    }

    public Instant getAnalyzedAt() {
        return analyzedAt;
    }

    public Boolean getLeaseholdRegistered() {
        return leaseholdRegistered;
    }

    public Boolean getSeizureOrDispositionRestricted() {
        return seizureOrDispositionRestricted;
    }

    public Boolean getAuctionInProgress() {
        return auctionInProgress;
    }

    public LocalDate getSeniorDebtRegisteredAt() {
        return seniorDebtRegisteredAt;
    }
}
