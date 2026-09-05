package com.homerun.domain.contract.repository;

import com.homerun.domain.contract.entity.RegistrySnapshot;
import com.homerun.domain.contract.type.RegistrySnapshotStage;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrySnapshotRepository extends JpaRepository<RegistrySnapshot, Long> {
    Optional<RegistrySnapshot> findByContractIdAndStage(Long contractId, RegistrySnapshotStage stage);
}
