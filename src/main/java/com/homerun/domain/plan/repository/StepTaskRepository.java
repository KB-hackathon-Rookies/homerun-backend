package com.homerun.domain.plan.repository;

import com.homerun.domain.plan.entity.StepTask;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StepTaskRepository extends JpaRepository<StepTask, Long> {

    /**
     * 계획 하나에 달린 모든 할 일. step_task 는 plan_id 를 직접 갖지 않고 plan_step 을 거치므로
     * 조인해서 가져온다. 대시보드가 매 요청마다 관문 수만큼 쿼리하지 않도록 한 번에 읽는다.
     */
    @Query("""
            select t from StepTask t
            where t.planStepId in (select s.id from PlanStep s where s.planId = :planId)
            order by t.planStepId asc, t.sequence asc
            """)
    List<StepTask> findAllByPlanId(@Param("planId") Long planId);

    List<StepTask> findAllByPlanStepIdOrderBySequenceAsc(Long planStepId);

    Optional<StepTask> findByPlanStepIdAndTaskCode(Long planStepId, String taskCode);
}
