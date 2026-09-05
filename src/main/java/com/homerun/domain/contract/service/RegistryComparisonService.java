package com.homerun.domain.contract.service;

import com.homerun.domain.contract.dto.request.RegistrySnapshotRequest;
import com.homerun.domain.contract.dto.response.RegistryComparisonResponse;
import com.homerun.domain.contract.entity.LeaseContract;
import com.homerun.domain.contract.entity.RegistrySnapshot;
import com.homerun.domain.contract.repository.LeaseContractRepository;
import com.homerun.domain.contract.repository.RegistrySnapshotRepository;
import com.homerun.domain.contract.type.RegistryComparisonStatus;
import com.homerun.domain.contract.type.RegistrySnapshotStage;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistryComparisonService {

    private final PlanRepository plans;
    private final LeaseContractRepository contracts;
    private final RegistrySnapshotRepository snapshots;

    public RegistryComparisonService(
            PlanRepository plans, LeaseContractRepository contracts, RegistrySnapshotRepository snapshots) {
        this.plans = plans;
        this.contracts = contracts;
        this.snapshots = snapshots;
    }

    @Transactional
    public RegistryComparisonResponse record(Long memberId, Long planId, RegistrySnapshotRequest request) {
        LeaseContract contract = ownedContract(memberId, planId);
        RegistrySnapshot snapshot = snapshots
                .findByContractIdAndStage(contract.getId(), request.stage())
                .orElseGet(() -> new RegistrySnapshot(contract.getId(), request));
        snapshot.overwrite(request);
        snapshots.save(snapshot);
        return compare(contract);
    }

    @Transactional(readOnly = true)
    public RegistryComparisonResponse compare(Long memberId, Long planId) {
        return compare(ownedContract(memberId, planId));
    }

    private RegistryComparisonResponse compare(LeaseContract contract) {
        RegistrySnapshot signing = snapshots
                .findByContractIdAndStage(contract.getId(), RegistrySnapshotStage.CONTRACT_SIGNING)
                .orElse(null);
        RegistrySnapshot settlement = snapshots
                .findByContractIdAndStage(contract.getId(), RegistrySnapshotStage.SETTLEMENT_DAY)
                .orElse(null);
        if (signing == null || settlement == null) {
            return new RegistryComparisonResponse(
                    RegistryComparisonStatus.NEED_INFO,
                    false,
                    signing == null ? null : signing.getIssuedAt(),
                    settlement == null ? null : settlement.getIssuedAt(),
                    List.of(),
                    signing == null ? "계약 체결 당시 등기부를 먼저 기록합니다." : "잔금일 최신 등기부를 기록하고 송금 전에 대조합니다.");
        }

        List<String> changes = new ArrayList<>();
        addIf(changes, !Boolean.TRUE.equals(settlement.getOwnerMatchesContractParty()), "OWNER_CHANGED");
        addIf(changes, increased(signing.getSeniorDebt(), settlement.getSeniorDebt()), "SENIOR_DEBT_INCREASED");
        addIf(changes, increased(signing.getMortgageCount(), settlement.getMortgageCount()), "MORTGAGE_INCREASED");
        addIf(
                changes,
                newlyTrue(signing.getLeaseholdRegistered(), settlement.getLeaseholdRegistered()),
                "LEASEHOLD_REGISTERED");
        addIf(
                changes,
                newlyTrue(signing.getSeizureOrDispositionRestricted(), settlement.getSeizureOrDispositionRestricted()),
                "SEIZURE_OR_DISPOSITION_RESTRICTION");
        addIf(changes, newlyTrue(signing.getAuctionInProgress(), settlement.getAuctionInProgress()), "AUCTION_STARTED");
        addIf(changes, newlyTrue(signing.getTrustRegistered(), settlement.getTrustRegistered()), "TRUST_REGISTERED");

        boolean unknown = settlement.getOwnerMatchesContractParty() == null
                || signing.getSeniorDebt() == null
                || settlement.getSeniorDebt() == null
                || signing.getMortgageCount() == null
                || settlement.getMortgageCount() == null
                || settlement.getLeaseholdRegistered() == null
                || settlement.getSeizureOrDispositionRestricted() == null
                || settlement.getAuctionInProgress() == null
                || settlement.getTrustRegistered() == null;
        RegistryComparisonStatus status = !changes.isEmpty()
                ? RegistryComparisonStatus.BLOCK
                : unknown ? RegistryComparisonStatus.NEED_INFO : RegistryComparisonStatus.SAFE;
        return new RegistryComparisonResponse(
                status,
                status == RegistryComparisonStatus.BLOCK,
                signing.getIssuedAt(),
                settlement.getIssuedAt(),
                changes,
                status == RegistryComparisonStatus.BLOCK
                        ? "송금을 중단하고 은행·중개사에 연락한 뒤 변경된 권리를 해소하거나 계약 해제를 검토합니다."
                        : status == RegistryComparisonStatus.NEED_INFO
                                ? "확인하지 못한 등기 항목을 채운 뒤 다시 대조합니다."
                                : "계약 당시보다 불리해진 권리가 확인되지 않았습니다.");
    }

    private LeaseContract ownedContract(Long memberId, Long planId) {
        plans.findById(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND))
                .verifyOwner(memberId);
        return contracts.findByPlanId(planId).orElseThrow(() -> new BusinessException(ErrorCode.CONTRACT_NOT_FOUND));
    }

    private boolean increased(Number before, Number after) {
        return before != null && after != null && after.longValue() > before.longValue();
    }

    private boolean newlyTrue(Boolean before, Boolean after) {
        return !Boolean.TRUE.equals(before) && Boolean.TRUE.equals(after);
    }

    private void addIf(List<String> changes, boolean condition, String code) {
        if (condition) {
            changes.add(code);
        }
    }
}
