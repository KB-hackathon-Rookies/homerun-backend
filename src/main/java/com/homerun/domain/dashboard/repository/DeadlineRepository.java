package com.homerun.domain.dashboard.repository;

import com.homerun.domain.dashboard.entity.Deadline;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeadlineRepository extends JpaRepository<Deadline, Long> {

    List<Deadline> findAllByPlanIdAndTaskIdIsNotNull(Long planId);

    /** 다시 계산할 때 이전 것을 지운다. 사람이 넣은 마감은 fact_code 가 없어 남는다. */
    void deleteByPlanIdAndFactCodeIn(Long planId, Collection<String> factCodes);

    List<Deadline> findAllByPlanIdAndFactCodeIn(Long planId, Collection<String> factCodes);
}
