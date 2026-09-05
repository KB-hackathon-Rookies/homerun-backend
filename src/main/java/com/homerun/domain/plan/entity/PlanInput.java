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
                && Objects.equals(birthDate, request.birthDate())
                && Objects.equals(militaryMonths, request.militaryMonths())
                && Objects.equals(monthlyIncome, request.monthlyIncome())
                && Objects.equals(netAssets, request.netAssets())
                && Objects.equals(availableCash, request.availableCash())
                && Objects.equals(existingJeonseLoan, request.existingJeonseLoan())
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
        birthDate = request.birthDate();
        militaryMonths = request.militaryMonths();
        monthlyIncome = request.monthlyIncome();
        netAssets = request.netAssets();
        availableCash = request.availableCash();
        existingJeonseLoan = request.existingJeonseLoan();
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
