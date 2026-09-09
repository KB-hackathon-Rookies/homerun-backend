package com.homerun.domain.plan.entity;

import com.homerun.domain.plan.dto.request.PlanInputRequest;
import com.homerun.domain.plan.type.CompanySize;
import com.homerun.domain.plan.type.EmploymentType;
import com.homerun.domain.plan.type.FinancialValueSource;
import com.homerun.domain.plan.type.HouseType;
import com.homerun.domain.plan.type.HouseholderStatus;
import com.homerun.domain.plan.type.MaritalStatus;
import com.homerun.domain.plan.type.PlanInputUnknownField;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "plan_input")
public class PlanInput {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false, unique = true)
    private Long planId;

    @Column(name = "hope_deposit")
    private Long hopeDeposit;

    @Column(name = "current_deposit")
    private Long currentDeposit;

    @Column(name = "monthly_rent")
    private Long monthlyRent;

    @Column(name = "maintenance_fee")
    private Long maintenanceFee;

    @Column(name = "max_monthly_burden")
    private Long maxMonthlyBurden;

    @Column(name = "region_id")
    private Long regionId;

    @Column(name = "area_m2", precision = 6, scale = 2)
    private BigDecimal areaM2;

    @Enumerated(EnumType.STRING)
    @Column(name = "house_type", length = 30)
    private HouseType houseType;

    @Column(name = "is_homeless")
    private Boolean homeless;

    @Enumerated(EnumType.STRING)
    @Column(name = "householder_status", length = 30)
    private HouseholderStatus householderStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "marital_status", length = 20)
    private MaritalStatus maritalStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", length = 30)
    private EmploymentType employmentType;

    @Column(name = "employment_months")
    private Integer employmentMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "company_size", length = 30)
    private CompanySize companySize;

    @Column(name = "household_homeless")
    private Boolean householdHomeless;

    /** 부모와 주민등록상 시·군이 다른가(FCT-043). 주소가 아니라 다른지 여부만 담는다. */
    @Column(name = "lives_apart_from_parents")
    private Boolean livesApartFromParents;

    /** 부모 가구가 이미 주거급여를 받고 있는가(FCT-044). 청년 단독 신청은 불가하다. */
    @Column(name = "parent_on_housing_benefit")
    private Boolean parentOnHousingBenefit;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "military_months")
    private Integer militaryMonths;

    @Column(name = "monthly_income")
    private Long monthlyIncome;

    @Column(name = "net_assets")
    private Long netAssets;

    @Column(name = "available_cash")
    private Long availableCash;

    @Column(name = "has_existing_jeonse_loan")
    private Boolean existingJeonseLoan;

    /**
     * 세대원 기금대출과 차주·배우자의 전세·주택담보대출까지 없음을 사용자가 확인했는가(FCT-258).
     *
     * <p>{@link #existingJeonseLoan} 이 담지 못하는 공식 금지 범위를 사용자가 스스로 확인한 값이다.
     * 사용자 진술이지 은행 검증이 아니라서 확인하지 않아도(null·false) 1루는 끝낼 수 있고, 판정만
     * 추가확인(NEED_INFO)으로 남는다.
     */
    @Column(name = "prohibited_loan_confirmed")
    private Boolean prohibitedLoanConfirmed;

    @Enumerated(EnumType.STRING)
    @Column(name = "income_source", length = 20)
    private FinancialValueSource incomeSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_source", length = 20)
    private FinancialValueSource assetSource;

    @Column(name = "financial_data_confirmed")
    private Boolean financialDataConfirmed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "unknown_fields", nullable = false, columnDefinition = "jsonb")
    private List<PlanInputUnknownField> unknownFields;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private int revision;

    @Version
    @Column(nullable = false)
    private long version;

    protected PlanInput() {}

    private PlanInput(Long planId, PlanInputRequest request) {
        this.planId = planId;
        applyValues(request);
        this.revision = 1;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public static PlanInput create(Long planId, PlanInputRequest request) {
        return new PlanInput(planId, request);
    }

    public static PlanInput createWithOpenBankingIncome(Long planId, Long monthlyIncome) {
        PlanInput input = new PlanInput();
        input.planId = planId;
        input.monthlyIncome = monthlyIncome;
        input.incomeSource = FinancialValueSource.OPEN_BANKING;
        input.financialDataConfirmed = false;
        input.unknownFields = new ArrayList<>();
        input.revision = 1;
        input.createdAt = Instant.now();
        input.updatedAt = input.createdAt;
        return input;
    }

    /**
     * 데모용 가짜 오픈뱅킹 연동. 사용자가 페르소나를 직접 고른 값이라 소득·순자산을 모두 넣고
     * 확인 완료로 둔다 — 그래야 실 연동과 달리 별도 확인 단계 없이 판정이 값을 바로 쓴다.
     */
    public static PlanInput createWithMockFinancials(Long planId, Long monthlyIncome, Long netAssets) {
        PlanInput input = new PlanInput();
        input.planId = planId;
        input.monthlyIncome = monthlyIncome;
        input.netAssets = netAssets;
        input.incomeSource = FinancialValueSource.OPEN_BANKING;
        input.assetSource = FinancialValueSource.OPEN_BANKING;
        input.financialDataConfirmed = true;
        input.unknownFields = new ArrayList<>();
        input.revision = 1;
        input.createdAt = Instant.now();
        input.updatedAt = input.createdAt;
        return input;
    }

    /** 이미 있는 입력에 가짜 오픈뱅킹 자산을 덮어쓴다. 관련 모름 표시는 값이 채워졌으니 지운다. */
    public void applyMockFinancials(Long monthlyIncome, Long netAssets) {
        this.monthlyIncome = monthlyIncome;
        this.netAssets = netAssets;
        this.incomeSource = FinancialValueSource.OPEN_BANKING;
        this.assetSource = FinancialValueSource.OPEN_BANKING;
        this.financialDataConfirmed = true;
        this.unknownFields = this.unknownFields.stream()
                .filter(field -> field != PlanInputUnknownField.MONTHLY_INCOME
                        && field != PlanInputUnknownField.NET_ASSETS
                        && field != PlanInputUnknownField.INCOME_SOURCE
                        && field != PlanInputUnknownField.ASSET_SOURCE
                        && field != PlanInputUnknownField.FINANCIAL_DATA_CONFIRMED)
                .toList();
        this.revision++;
        this.updatedAt = Instant.now();
    }

    public boolean syncOpenBankingIncome(Long value) {
        if (Objects.equals(monthlyIncome, value)
                && incomeSource == FinancialValueSource.OPEN_BANKING
                && !Boolean.TRUE.equals(financialDataConfirmed)
                && !unknownFields.contains(PlanInputUnknownField.MONTHLY_INCOME)) {
            return false;
        }
        monthlyIncome = value;
        incomeSource = FinancialValueSource.OPEN_BANKING;
        financialDataConfirmed = false;
        unknownFields = unknownFields.stream()
                .filter(field -> field != PlanInputUnknownField.MONTHLY_INCOME
                        && field != PlanInputUnknownField.INCOME_SOURCE
                        && field != PlanInputUnknownField.FINANCIAL_DATA_CONFIRMED)
                .toList();
        revision++;
        updatedAt = Instant.now();
        return true;
    }

    public boolean confirmOpenBankingIncome() {
        if (Boolean.TRUE.equals(financialDataConfirmed)) return false;
        financialDataConfirmed = true;
        revision++;
        updatedAt = Instant.now();
        return true;
    }

    public boolean useManualIncome(Long value) {
        if (Objects.equals(monthlyIncome, value)
                && incomeSource == FinancialValueSource.MANUAL
                && !Boolean.TRUE.equals(financialDataConfirmed)
                && !unknownFields.contains(PlanInputUnknownField.MONTHLY_INCOME)) {
            return false;
        }
        monthlyIncome = value;
        incomeSource = FinancialValueSource.MANUAL;
        // 다른 외부 자산값까지 확인한 것으로 오해하지 않도록 전역 확인값은 해제한다.
        financialDataConfirmed = false;
        unknownFields = unknownFields.stream()
                .filter(field -> field != PlanInputUnknownField.MONTHLY_INCOME
                        && field != PlanInputUnknownField.INCOME_SOURCE
                        && field != PlanInputUnknownField.FINANCIAL_DATA_CONFIRMED)
                .toList();
        revision++;
        updatedAt = Instant.now();
        return true;
    }

    public boolean matches(PlanInputRequest request) {
        return Objects.equals(hopeDeposit, request.hopeDeposit())
                && Objects.equals(currentDeposit, request.currentDeposit())
                && Objects.equals(monthlyRent, request.monthlyRent())
                && Objects.equals(maintenanceFee, request.maintenanceFee())
                && Objects.equals(maxMonthlyBurden, request.maxMonthlyBurden())
                && Objects.equals(regionId, request.regionId())
                && equalDecimal(areaM2, request.areaM2())
                && Objects.equals(houseType, request.houseType())
                && Objects.equals(homeless, request.isHomeless())
                && Objects.equals(householderStatus, request.householderStatus())
                && Objects.equals(maritalStatus, request.maritalStatus())
                && Objects.equals(employmentType, request.employmentType())
                && Objects.equals(employmentMonths, request.employmentMonths())
                && Objects.equals(companySize, request.companySize())
                && Objects.equals(householdHomeless, request.householdHomeless())
                && Objects.equals(livesApartFromParents, request.livesApartFromParents())
                && Objects.equals(parentOnHousingBenefit, request.parentOnHousingBenefit())
                && Objects.equals(birthDate, request.birthDate())
                && Objects.equals(militaryMonths, request.militaryMonths())
                && Objects.equals(monthlyIncome, request.monthlyIncome())
                && Objects.equals(netAssets, request.netAssets())
                && Objects.equals(availableCash, request.availableCash())
                && Objects.equals(existingJeonseLoan, request.existingJeonseLoan())
                && Objects.equals(prohibitedLoanConfirmed, request.prohibitedLoanConfirmed())
                && Objects.equals(incomeSource, request.incomeSource())
                && Objects.equals(assetSource, request.assetSource())
                && Objects.equals(financialDataConfirmed, request.financialDataConfirmed())
                && Objects.equals(unknownFields, sortedUnknownFields(request));
    }

    public void update(PlanInputRequest request) {
        applyValues(request);
        revision++;
        updatedAt = Instant.now();
    }

    private void applyValues(PlanInputRequest request) {
        hopeDeposit = request.hopeDeposit();
        currentDeposit = request.currentDeposit();
        monthlyRent = request.monthlyRent();
        maintenanceFee = request.maintenanceFee();
        maxMonthlyBurden = request.maxMonthlyBurden();
        regionId = request.regionId();
        areaM2 = request.areaM2();
        houseType = request.houseType();
        homeless = request.isHomeless();
        householderStatus = request.householderStatus();
        maritalStatus = request.maritalStatus();
        employmentType = request.employmentType();
        employmentMonths = request.employmentMonths();
        companySize = request.companySize();
        householdHomeless = request.householdHomeless();
        livesApartFromParents = request.livesApartFromParents();
        parentOnHousingBenefit = request.parentOnHousingBenefit();
        birthDate = request.birthDate();
        militaryMonths = request.militaryMonths();
        monthlyIncome = request.monthlyIncome();
        netAssets = request.netAssets();
        availableCash = request.availableCash();
        existingJeonseLoan = request.existingJeonseLoan();
        prohibitedLoanConfirmed = request.prohibitedLoanConfirmed();
        incomeSource = request.incomeSource();
        assetSource = request.assetSource();
        financialDataConfirmed = request.financialDataConfirmed();
        unknownFields = sortedUnknownFields(request);
    }

    private List<PlanInputUnknownField> sortedUnknownFields(PlanInputRequest request) {
        return request.normalizedUnknownFields().stream()
                .sorted(java.util.Comparator.comparing(Enum::name))
                .toList();
    }

    private boolean equalDecimal(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }

    public Long getId() {
        return id;
    }

    public Long getPlanId() {
        return planId;
    }

    public Long getHopeDeposit() {
        return hopeDeposit;
    }

    public Long getCurrentDeposit() {
        return currentDeposit;
    }

    public Long getMonthlyRent() {
        return monthlyRent;
    }

    public Long getMaintenanceFee() {
        return maintenanceFee;
    }

    public Long getMaxMonthlyBurden() {
        return maxMonthlyBurden;
    }

    public Long getRegionId() {
        return regionId;
    }

    public BigDecimal getAreaM2() {
        return areaM2;
    }

    public HouseType getHouseType() {
        return houseType;
    }

    public Boolean getHomeless() {
        return homeless;
    }

    public HouseholderStatus getHouseholderStatus() {
        return householderStatus;
    }

    public MaritalStatus getMaritalStatus() {
        return maritalStatus;
    }

    public EmploymentType getEmploymentType() {
        return employmentType;
    }

    public Integer getEmploymentMonths() {
        return employmentMonths;
    }

    public CompanySize getCompanySize() {
        return companySize;
    }

    public Boolean getHouseholdHomeless() {
        return householdHomeless;
    }

    public Boolean getLivesApartFromParents() {
        return livesApartFromParents;
    }

    public Boolean getParentOnHousingBenefit() {
        return parentOnHousingBenefit;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public Integer getMilitaryMonths() {
        return militaryMonths;
    }

    public Long getMonthlyIncome() {
        return monthlyIncome;
    }

    public Long getNetAssets() {
        return netAssets;
    }

    public Long getAvailableCash() {
        return availableCash;
    }

    public Boolean getExistingJeonseLoan() {
        return existingJeonseLoan;
    }

    public Boolean getProhibitedLoanConfirmed() {
        return prohibitedLoanConfirmed;
    }

    public FinancialValueSource getIncomeSource() {
        return incomeSource;
    }

    public FinancialValueSource getAssetSource() {
        return assetSource;
    }

    public Boolean getFinancialDataConfirmed() {
        return financialDataConfirmed;
    }

    public List<PlanInputUnknownField> getUnknownFields() {
        return List.copyOf(unknownFields);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getRevision() {
        return revision;
    }
}
