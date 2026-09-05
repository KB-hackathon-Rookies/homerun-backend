package com.homerun.domain.diagnosis.repository;

import com.homerun.domain.diagnosis.entity.Diagnosis;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiagnosisRepository extends JpaRepository<Diagnosis, Long> {
    Optional<Diagnosis> findFirstByPlanIdAndCostEstimateIdIsNotNullOrderByCreatedAtDescIdDesc(Long planId);

    Optional<Diagnosis> findByIdAndPlanId(Long id, Long planId);
}
