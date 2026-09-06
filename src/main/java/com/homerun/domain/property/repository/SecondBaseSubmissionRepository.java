package com.homerun.domain.property.repository;

import com.homerun.domain.property.entity.SecondBaseSubmission;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecondBaseSubmissionRepository extends JpaRepository<SecondBaseSubmission, Long> {

    long countByPlanId(Long planId);

    Optional<SecondBaseSubmission> findByPlanIdAndDecisionRevision(Long planId, int decisionRevision);

    Optional<SecondBaseSubmission> findFirstByPlanIdOrderByCreatedAtDescIdDesc(Long planId);
}
