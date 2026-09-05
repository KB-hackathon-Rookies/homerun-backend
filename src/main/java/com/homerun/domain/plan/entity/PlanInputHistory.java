package com.homerun.domain.plan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "plan_input_history")
public class PlanInputHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_input_id", nullable = false)
    private PlanInput planInput;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(nullable = false)
    private int revision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> snapshot;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    protected PlanInputHistory() {}

    private PlanInputHistory(PlanInput input) {
        this.planInput = input;
        this.planId = input.getPlanId();
        this.revision = input.getRevision();
        this.snapshot = snapshot(input);
        this.savedAt = Instant.now();
    }

    public static PlanInputHistory capture(PlanInput input) {
        return new PlanInputHistory(input);
    }

    private Map<String, Object> snapshot(PlanInput input) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("hopeDeposit", input.getHopeDeposit());
        values.put("currentDeposit", input.getCurrentDeposit());
        values.put("monthlyRent", input.getMonthlyRent());
        values.put("maintenanceFee", input.getMaintenanceFee());
        values.put("maxMonthlyBurden", input.getMaxMonthlyBurden());
        values.put("regionId", input.getRegionId());
        values.put("areaM2", input.getAreaM2());
        values.put("houseType", input.getHouseType());
        values.put("isHomeless", input.getHomeless());
        values.put("householderStatus", input.getHouseholderStatus());
        values.put("maritalStatus", input.getMaritalStatus());
        values.put("employmentType", input.getEmploymentType());
        values.put("employmentMonths", input.getEmploymentMonths());
        values.put("companySize", input.getCompanySize());
        values.put("householdHomeless", input.getHouseholdHomeless());
        values.put("livesApartFromParents", input.getLivesApartFromParents());
        values.put("parentOnHousingBenefit", input.getParentOnHousingBenefit());
        values.put("birthDate", input.getBirthDate());
        values.put("militaryMonths", input.getMilitaryMonths());
        values.put("monthlyIncome", input.getMonthlyIncome());
        values.put("netAssets", input.getNetAssets());
        values.put("availableCash", input.getAvailableCash());
        values.put("existingJeonseLoan", input.getExistingJeonseLoan());
        values.put("incomeSource", input.getIncomeSource());
        values.put("assetSource", input.getAssetSource());
        values.put("financialDataConfirmed", input.getFinancialDataConfirmed());
        values.put("unknownFields", input.getUnknownFields());
        return values;
    }

    public int getRevision() {
        return revision;
    }

    public Map<String, Object> getSnapshot() {
        return Collections.unmodifiableMap(snapshot);
    }
}
