package com.homerun.domain.plan.entity;

import com.homerun.domain.plan.type.CompanySize;
import com.homerun.domain.plan.type.EmploymentType;
import com.homerun.domain.plan.type.HouseType;
import com.homerun.domain.plan.type.HouseholderStatus;
import com.homerun.domain.plan.type.MaritalStatus;
import com.homerun.domain.plan.type.PlanInputUnknownField;
import com.homerun.domain.region.entity.Region;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "plan_input")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanInput {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    // 주거 비용
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

    // 주거 조건
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id")
    private Region region;

    @Column(name = "area_m2", precision = 6, scale = 2)
    private BigDecimal areaM2;

    @Enumerated(EnumType.STRING)
    @Column(name = "house_type", length = 30)
    private HouseType houseType;

    // 가구 조건
    @Column(name = "is_homeless")
    private Boolean isHomeless;

    @Enumerated(EnumType.STRING)
    @Column(name = "householder_status", length = 30)
    private HouseholderStatus householderStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "marital_status", length = 20)
    private MaritalStatus maritalStatus;

    // 직업 조건
    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", length = 30)
    private EmploymentType employmentType;

    @Column(name = "employment_months")
    private Integer employmentMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "company_size", length = 30)
    private CompanySize companySize;

    // 사용자가 "모름"으로 선택한 필드
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "unknown_fields", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<PlanInputUnknownField> unknownFields = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {

        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }

        if (unknownFields == null) {
            unknownFields = new ArrayList<>();
        }
    }
}
