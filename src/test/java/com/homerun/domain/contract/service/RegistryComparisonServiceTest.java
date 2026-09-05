package com.homerun.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.homerun.domain.contract.dto.request.RegistrySnapshotRequest;
import com.homerun.domain.contract.dto.response.RegistryComparisonResponse;
import com.homerun.domain.contract.entity.LeaseContract;
import com.homerun.domain.contract.entity.RegistrySnapshot;
import com.homerun.domain.contract.repository.LeaseContractRepository;
import com.homerun.domain.contract.repository.RegistrySnapshotRepository;
import com.homerun.domain.contract.type.RegistryComparisonStatus;
import com.homerun.domain.contract.type.RegistrySnapshotStage;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.LeaseType;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RegistryComparisonServiceTest {

    @Mock
    private PlanRepository plans;

    @Mock
    private LeaseContractRepository contracts;

    @Mock
    private RegistrySnapshotRepository snapshots;

    private RegistryComparisonService service;
    private LeaseContract contract;

    @BeforeEach
    void setUp() {
        service = new RegistryComparisonService(plans, contracts, snapshots);
        Plan plan = Plan.create(1L, LeaseType.JEONSE, null);
        contract = new LeaseContract(100L, LeaseType.JEONSE, 200_000_000L, 0L);
        ReflectionTestUtils.setField(contract, "id", 200L);
        when(plans.findById(100L)).thenReturn(Optional.of(plan));
        when(contracts.findByPlanId(100L)).thenReturn(Optional.of(contract));
    }

    @Test
    @DisplayName("계약 후 근저당과 처분 제한이 생기면 잔금 송금을 중단시킨다")
    void blocks_payment_when_registry_risk_increases() {
        RegistrySnapshot signing =
                snapshot(RegistrySnapshotStage.CONTRACT_SIGNING, true, 50_000_000L, 1, false, false, false, false);
        RegistrySnapshot settlement =
                snapshot(RegistrySnapshotStage.SETTLEMENT_DAY, true, 70_000_000L, 2, false, true, false, false);
        when(snapshots.findByContractIdAndStage(200L, RegistrySnapshotStage.CONTRACT_SIGNING))
                .thenReturn(Optional.of(signing));
        when(snapshots.findByContractIdAndStage(200L, RegistrySnapshotStage.SETTLEMENT_DAY))
                .thenReturn(Optional.of(settlement));

        RegistryComparisonResponse response = service.compare(1L, 100L);

        assertThat(response.status()).isEqualTo(RegistryComparisonStatus.BLOCK);
        assertThat(response.stopPayment()).isTrue();
        assertThat(response.changedRisks())
                .containsExactly("SENIOR_DEBT_INCREASED", "MORTGAGE_INCREASED", "SEIZURE_OR_DISPOSITION_RESTRICTION");
    }

    @Test
    @DisplayName("잔금일 등기부가 없으면 추가 입력이 필요하다고 안내한다")
    void needs_information_when_settlement_snapshot_is_missing() {
        RegistrySnapshot signing =
                snapshot(RegistrySnapshotStage.CONTRACT_SIGNING, true, 50_000_000L, 1, false, false, false, false);
        when(snapshots.findByContractIdAndStage(200L, RegistrySnapshotStage.CONTRACT_SIGNING))
                .thenReturn(Optional.of(signing));
        when(snapshots.findByContractIdAndStage(200L, RegistrySnapshotStage.SETTLEMENT_DAY))
                .thenReturn(Optional.empty());

        RegistryComparisonResponse response = service.compare(1L, 100L);

        assertThat(response.status()).isEqualTo(RegistryComparisonStatus.NEED_INFO);
        assertThat(response.stopPayment()).isFalse();
    }

    private RegistrySnapshot snapshot(
            RegistrySnapshotStage stage,
            Boolean ownerMatches,
            Long seniorDebt,
            Integer mortgageCount,
            Boolean leasehold,
            Boolean seizure,
            Boolean auction,
            Boolean trust) {
        return new RegistrySnapshot(
                200L,
                new RegistrySnapshotRequest(
                        stage,
                        ownerMatches,
                        seniorDebt,
                        mortgageCount,
                        leasehold,
                        seizure,
                        auction,
                        trust,
                        stage == RegistrySnapshotStage.CONTRACT_SIGNING
                                ? LocalDate.of(2026, 10, 20)
                                : LocalDate.of(2026, 11, 20)));
    }
}
