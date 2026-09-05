package com.homerun.domain.contract.entity;

import com.homerun.domain.contract.dto.request.RegistrySnapshotRequest;
import com.homerun.domain.contract.type.RegistrySnapshotStage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "registry_snapshot")
public class RegistrySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RegistrySnapshotStage stage;

    @Column(name = "owner_matches_contract_party")
    private Boolean ownerMatchesContractParty;

    @Column(name = "senior_debt")
    private Long seniorDebt;

    @Column(name = "mortgage_count")
    private Integer mortgageCount;

    @Column(name = "is_leasehold_registered")
    private Boolean leaseholdRegistered;

    @Column(name = "has_seizure_or_disposition_restriction")
    private Boolean seizureOrDispositionRestricted;

    @Column(name = "is_auction_in_progress")
    private Boolean auctionInProgress;

    @Column(name = "is_trust_registered")
    private Boolean trustRegistered;

    @Column(name = "issued_at", nullable = false)
    private LocalDate issuedAt;

    @Column(name = "recorded_at", nullable = false, insertable = false, updatable = false)
    private Instant recordedAt;

    protected RegistrySnapshot() {}

    public RegistrySnapshot(Long contractId, RegistrySnapshotRequest request) {
        this.contractId = contractId;
        overwrite(request);
    }

    public void overwrite(RegistrySnapshotRequest request) {
        this.stage = request.stage();
        this.ownerMatchesContractParty = request.ownerMatchesContractParty();
        this.seniorDebt = request.seniorDebt();
        this.mortgageCount = request.mortgageCount();
        this.leaseholdRegistered = request.leaseholdRegistered();
        this.seizureOrDispositionRestricted = request.seizureOrDispositionRestricted();
        this.auctionInProgress = request.auctionInProgress();
        this.trustRegistered = request.trustRegistered();
        this.issuedAt = request.issuedAt();
    }

    public Long getId() {
        return id;
    }

    public Long getContractId() {
        return contractId;
    }

    public RegistrySnapshotStage getStage() {
        return stage;
    }

    public Boolean getOwnerMatchesContractParty() {
        return ownerMatchesContractParty;
    }

    public Long getSeniorDebt() {
        return seniorDebt;
    }

    public Integer getMortgageCount() {
        return mortgageCount;
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

    public Boolean getTrustRegistered() {
        return trustRegistered;
    }

    public LocalDate getIssuedAt() {
        return issuedAt;
    }
}
