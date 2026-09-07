package com.homerun.domain.settlement.repository;

import com.homerun.domain.settlement.entity.ReturnGuaranteeEnrollment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReturnGuaranteeEnrollmentRepository extends JpaRepository<ReturnGuaranteeEnrollment, Long> {
    Optional<ReturnGuaranteeEnrollment> findByPlanId(Long planId);
}
