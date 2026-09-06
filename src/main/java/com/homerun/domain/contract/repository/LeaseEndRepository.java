package com.homerun.domain.contract.repository;

import com.homerun.domain.contract.entity.LeaseEnd;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaseEndRepository extends JpaRepository<LeaseEnd, Long> {
    Optional<LeaseEnd> findByPlanId(Long planId);
}
