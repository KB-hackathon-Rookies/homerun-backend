package com.homerun.domain.settlement.repository;

import com.homerun.domain.settlement.entity.FixedExpense;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FixedExpenseRepository extends JpaRepository<FixedExpense, Long> {
    List<FixedExpense> findAllByPlanIdAndActiveTrueOrderByIdAsc(Long planId);

    Optional<FixedExpense> findByIdAndPlanId(Long id, Long planId);
}
