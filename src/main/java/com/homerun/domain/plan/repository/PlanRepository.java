package com.homerun.domain.plan.repository;

import com.homerun.domain.plan.entity.Plan;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from Plan plan where plan.id = :id")
    Optional<Plan> findByIdForUpdate(@Param("id") Long id);
}
