package com.homerun.domain.settlement.repository;

import com.homerun.domain.settlement.entity.LoanAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanAccountRepository extends JpaRepository<LoanAccount, Long> {
    Optional<LoanAccount> findByPlanId(Long planId);
}
