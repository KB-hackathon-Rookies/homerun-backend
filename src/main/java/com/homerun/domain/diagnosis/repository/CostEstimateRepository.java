package com.homerun.domain.diagnosis.repository;

import com.homerun.domain.diagnosis.entity.CostEstimate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CostEstimateRepository extends JpaRepository<CostEstimate, Long> {}
