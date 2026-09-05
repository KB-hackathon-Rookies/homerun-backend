package com.homerun.domain.contract.dto.request;

import com.homerun.domain.contract.type.RegistrySnapshotStage;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

public record RegistrySnapshotRequest(
        @NotNull RegistrySnapshotStage stage,
        Boolean ownerMatchesContractParty,
        @PositiveOrZero Long seniorDebt,
        @PositiveOrZero Integer mortgageCount,
        Boolean leaseholdRegistered,
        Boolean seizureOrDispositionRestricted,
        Boolean auctionInProgress,
        Boolean trustRegistered,
        @NotNull LocalDate issuedAt) {}
