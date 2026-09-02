package com.homerun.domain.plan.entity;

import com.homerun.domain.plan.dto.request.PlanInputRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
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

    @Column(name = "house_type", length = 30)
    private String houseType;

    @Column(name = "is_homeless")
    private Boolean homeless;

    @Column(name = "householder_status", length = 30)
    private String householderStatus;

    @Column(name = "marital_status", length = 20)
    private String maritalStatus;

    @Column(name = "employment_type", length = 30)
    private String employmentType;

    @Column(name = "employment_months")
    private Integer employmentMonths;

    @Column(name = "company_size", length = 30)
    private String companySize;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "unknown_fields", nullable = false, columnDefinition = "jsonb")
    private List<String> unknownFields;

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
        unknownFields = sortedUnknownFields(request);
    }

    private List<String> sortedUnknownFields(PlanInputRequest request) {
        return request.normalizedUnknownFields().stream().sorted().toList();
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

    public String getHouseType() {
        return houseType;
    }

    public Boolean getHomeless() {
        return homeless;
    }

    public String getHouseholderStatus() {
        return householderStatus;
    }

    public String getMaritalStatus() {
        return maritalStatus;
    }

    public String getEmploymentType() {
        return employmentType;
    }

    public Integer getEmploymentMonths() {
        return employmentMonths;
    }

    public String getCompanySize() {
        return companySize;
    }

    public List<String> getUnknownFields() {
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
