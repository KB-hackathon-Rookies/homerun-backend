package com.homerun.domain.property.entity;

import com.homerun.domain.plan.type.HouseType;
import com.homerun.domain.property.type.DataSource;
import com.homerun.domain.property.type.LandlordConsent;
import com.homerun.domain.property.type.OfficialPriceSource;
import com.homerun.domain.property.type.PropertyDiagnosisStep;
import com.homerun.domain.property.type.PropertyWorkflowStatus;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
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

    @Column(name = "official_price_year")
    private Integer officialPriceYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "official_price_source", length = 40)
    private OfficialPriceSource officialPriceSource;

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

    @Column(name = "is_non_residential")
    private Boolean nonResidential;

    @Column(name = "landlord_tax_unpaid")
    private Boolean landlordTaxUnpaid;

    /** 임대인 전세대출 협조 여부(FR-P1-07). REFUSED 여도 RED 아님. */
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "landlord_consent", length = 20)
    private LandlordConsent landlordConsent;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_step", nullable = false, length = 20)
    private PropertyDiagnosisStep workflowStep = PropertyDiagnosisStep.BUILDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_status", nullable = false, length = 30)
    private PropertyWorkflowStatus workflowStatus = PropertyWorkflowStatus.IN_PROGRESS;

    @Column(name = "workflow_revision", nullable = false)
    private int workflowRevision = 1;

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

    public void startWorkflow(boolean buildingDetailsReady, boolean blocked) {
        workflowStep = buildingDetailsReady ? PropertyDiagnosisStep.VIOLATION : PropertyDiagnosisStep.BUILDING;
        workflowStatus = blocked ? PropertyWorkflowStatus.BLOCKED : PropertyWorkflowStatus.IN_PROGRESS;
    }

    public void recordAutomaticSafety(Boolean multiHousehold, Boolean nonResidential) {
        this.multiHousehold = multiHousehold;
        this.nonResidential = nonResidential;
    }

    public void completeBuildingStep(int expectedRevision, HouseType type, BigDecimal area) {
        verifyWorkflow(expectedRevision, PropertyDiagnosisStep.BUILDING);
        houseType = type.name();
        houseTypeSource = DataSource.MANUAL;
        exclusiveArea = area;
        areaSource = DataSource.MANUAL;
        workflowStep = PropertyDiagnosisStep.VIOLATION;
        workflowStatus = PropertyWorkflowStatus.IN_PROGRESS;
        workflowRevision++;
    }

    public void completeViolationStep(int expectedRevision, boolean violation) {
        verifyWorkflow(expectedRevision, PropertyDiagnosisStep.VIOLATION);
        violationBuilding = violation;
        workflowStatus = violation ? PropertyWorkflowStatus.BLOCKED : PropertyWorkflowStatus.IN_PROGRESS;
        if (!violation) {
            workflowStep = PropertyDiagnosisStep.REGISTRY;
        }
        workflowRevision++;
    }

    public void completeRegistryStep(
            int expectedRevision,
            Long officialPrice,
            Integer officialPriceYear,
            OfficialPriceSource officialPriceSource,
            Long seniorDebt,
            Boolean ownerMatches,
            Boolean trustRegistered,
            Boolean leaseholdRegistered,
            Boolean seizureOrDispositionRestricted,
            Boolean auctionInProgress,
            LocalDate seniorDebtRegisteredAt,
            Boolean landlordTaxUnpaid) {
        verifyWorkflow(expectedRevision, PropertyDiagnosisStep.REGISTRY);
        this.officialPrice = officialPrice;
        this.officialPriceYear = officialPriceYear;
        this.officialPriceSource = officialPriceSource;
        this.seniorDebt = seniorDebt;
        this.ownerMatches = ownerMatches;
        this.trustRegistered = trustRegistered;
        this.leaseholdRegistered = leaseholdRegistered;
        this.seizureOrDispositionRestricted = seizureOrDispositionRestricted;
        this.auctionInProgress = auctionInProgress;
        this.seniorDebtRegisteredAt = seniorDebtRegisteredAt;
        this.landlordTaxUnpaid = landlordTaxUnpaid;
        workflowRevision++;
    }

    public void finishRegistry(PropertyWorkflowStatus status) {
        workflowStatus = status;
        if (status == PropertyWorkflowStatus.READY_FOR_CONSULTATION) {
            workflowStep = PropertyDiagnosisStep.COMPLETE;
        }
    }

    public void blockAt(PropertyDiagnosisStep step) {
        workflowStep = step;
        workflowStatus = PropertyWorkflowStatus.BLOCKED;
    }

    public void markConsulted() {
        if (workflowStatus == PropertyWorkflowStatus.READY_FOR_CONSULTATION) {
            workflowStatus = PropertyWorkflowStatus.CONSULTED;
            workflowRevision++;
        }
    }

    private void verifyWorkflow(int expectedRevision, PropertyDiagnosisStep expectedStep) {
        if (workflowRevision != expectedRevision) {
            throw new BusinessException(ErrorCode.PROPERTY_WORKFLOW_REVISION_MISMATCH);
        }
        if (workflowStatus == PropertyWorkflowStatus.BLOCKED || workflowStep != expectedStep) {
            throw new BusinessException(ErrorCode.PROPERTY_WORKFLOW_STEP_INVALID);
        }
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

    public LandlordConsent getLandlordConsent() {
        return landlordConsent;
    }

    public void recordLandlordConsent(LandlordConsent landlordConsent) {
        this.landlordConsent = landlordConsent;
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

    public Integer getOfficialPriceYear() {
        return officialPriceYear;
    }

    public OfficialPriceSource getOfficialPriceSource() {
        return officialPriceSource;
    }

    public Boolean getViolationBuilding() {
        return violationBuilding;
    }

    public Boolean getMultiHousehold() {
        return multiHousehold;
    }

    public Boolean getNonResidential() {
        return nonResidential;
    }

    public String getLegalDistrictCode() {
        return legalDistrictCode;
    }

    public Long getMarketPrice() {
        return marketPrice;
    }

    public Long getSeniorDebt() {
        return seniorDebt;
    }

    public Boolean getOwnerMatches() {
        return ownerMatches;
    }

    public Boolean getTrustRegistered() {
        return trustRegistered;
    }

    public Boolean getLandlordTaxUnpaid() {
        return landlordTaxUnpaid;
    }

    public PropertyDiagnosisStep getWorkflowStep() {
        return workflowStep;
    }

    public PropertyWorkflowStatus getWorkflowStatus() {
        return workflowStatus;
    }

    public int getWorkflowRevision() {
        return workflowRevision;
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
