package com.homerun.domain.plan.plan_jeonse.repository;


import com.homerun.domain.plan.plan_jeonse.domain.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, Long> {
}