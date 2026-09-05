package com.homerun.domain.openbanking.repository;

import com.homerun.domain.openbanking.entity.FinancialSnapshot;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialSnapshotRepository extends JpaRepository<FinancialSnapshot, Long> {
    Optional<FinancialSnapshot> findFirstByUserIdOrderByCreatedAtDescIdDesc(Long userId);
}
