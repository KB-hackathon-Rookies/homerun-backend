package com.homerun.domain.asset.entity;

import com.homerun.domain.asset.type.AssetType;
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

/**
 * 노후자산(IRP·주택청약)을 깨서 보증금을 만드는 선택 하나의 계산 스냅샷.
 *
 * <p>{@code policy_verdict}와 달리 재계산해도 기존 행을 덮어쓰지 않고 새 행을 쌓는다 —
 * 잔액·인출액을 바꿔가며 여러 번 비교해볼 수 있어야 하고, 그 이력 자체가 의미 있다.
 *
 * <p>이 테이블에 남는 옵션은 전부 비가역이다({@code is_reversible = false}) — 대출(가역)과
 * 비교하기 위한 기준점이지, 대출 자체는 여기 안 남는다.
 */
@Entity
@Table(name = "asset_option")
public class AssetOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 30)
    private AssetType assetType;

    @Column
    private Long balance;

    @Column(name = "withdraw_amount")
    private Long withdrawAmount;

    @Column(name = "tax_penalty_rate", precision = 6, scale = 3)
    private BigDecimal taxPenaltyRate;

    @Column(name = "tax_penalty_amount")
    private Long taxPenaltyAmount;

    @Column(name = "re_contribution_years", precision = 4, scale = 1)
    private BigDecimal reContributionYears;

    @Column(name = "is_reversible", nullable = false)
    private boolean reversible;

    @Column(name = "compared_loan_interest")
    private Long comparedLoanInterest;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AssetOption() {}

    private AssetOption(
            Long planId,
            AssetType assetType,
            Long balance,
            Long withdrawAmount,
            BigDecimal taxPenaltyRate,
            Long taxPenaltyAmount,
            Long comparedLoanInterest) {
        this.planId = planId;
        this.assetType = assetType;
        this.balance = balance;
        this.withdrawAmount = withdrawAmount;
        this.taxPenaltyRate = taxPenaltyRate;
        this.taxPenaltyAmount = taxPenaltyAmount;
        this.reContributionYears = null;
        this.reversible = false;
        this.comparedLoanInterest = comparedLoanInterest;
        this.createdAt = Instant.now();
    }

    public static AssetOption create(
            Long planId,
            AssetType assetType,
            Long balance,
            Long withdrawAmount,
            BigDecimal taxPenaltyRate,
            Long taxPenaltyAmount,
            Long comparedLoanInterest) {
        return new AssetOption(
                planId, assetType, balance, withdrawAmount, taxPenaltyRate, taxPenaltyAmount, comparedLoanInterest);
    }

    public Long getId() {
        return id;
    }

    public AssetType getAssetType() {
        return assetType;
    }
}
