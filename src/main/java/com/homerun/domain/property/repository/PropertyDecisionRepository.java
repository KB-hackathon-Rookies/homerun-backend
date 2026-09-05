package com.homerun.domain.property.repository;

import com.homerun.domain.property.entity.PropertyDecision;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyDecisionRepository extends JpaRepository<PropertyDecision, Long> {
    Optional<PropertyDecision> findByPlanId(Long planId);
}
