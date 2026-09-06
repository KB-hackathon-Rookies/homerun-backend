package com.homerun.domain.property.repository;

import com.homerun.domain.property.entity.PropertyDecision;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PropertyDecisionRepository extends JpaRepository<PropertyDecision, Long> {
    Optional<PropertyDecision> findByPlanId(Long planId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT decision FROM PropertyDecision decision WHERE decision.planId = :planId")
    Optional<PropertyDecision> findByPlanIdForUpdate(@Param("planId") Long planId);
}
