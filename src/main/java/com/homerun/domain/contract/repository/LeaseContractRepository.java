package com.homerun.domain.contract.repository;

import com.homerun.domain.contract.entity.LeaseContract;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaseContractRepository extends JpaRepository<LeaseContract, Long> {

    Optional<LeaseContract> findByPlanId(Long planId);
}
