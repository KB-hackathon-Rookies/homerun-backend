package com.homerun.domain.plan.plan_J.repository;

import com.homerun.domain.plan.plan_J.domain.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, Long> {
}