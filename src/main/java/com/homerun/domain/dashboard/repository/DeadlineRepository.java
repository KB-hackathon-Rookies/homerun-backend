package com.homerun.domain.dashboard.repository;

import com.homerun.domain.dashboard.entity.Deadline;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeadlineRepository extends JpaRepository<Deadline, Long> {

    List<Deadline> findAllByPlanIdAndStepIdIsNotNull(Long planId);
}
