package com.homerun.domain.plan.repository;

import com.homerun.domain.plan.entity.PlanInputHistory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanInputHistoryRepository extends JpaRepository<PlanInputHistory, Long> {

    Optional<PlanInputHistory> findFirstByPlanIdOrderByRevisionDesc(Long planId);
}
