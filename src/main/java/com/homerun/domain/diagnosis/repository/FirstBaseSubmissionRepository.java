package com.homerun.domain.diagnosis.repository;

import com.homerun.domain.diagnosis.entity.FirstBaseSubmission;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FirstBaseSubmissionRepository extends JpaRepository<FirstBaseSubmission, Long> {
    Optional<FirstBaseSubmission> findByPlanIdAndInputRevision(Long planId, int inputRevision);

    Optional<FirstBaseSubmission> findFirstByPlanIdOrderByCreatedAtDescIdDesc(Long planId);
}
