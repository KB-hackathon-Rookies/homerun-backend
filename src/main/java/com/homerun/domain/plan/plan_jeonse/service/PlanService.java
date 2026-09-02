package com.homerun.domain.plan.plan_jeonse.service;


import com.homerun.domain.plan.plan_jeonse.domain.Plan;
import com.homerun.domain.plan.plan_jeonse.dto.PlanCreateRequest;
import com.homerun.domain.plan.plan_jeonse.dto.PlanResponse;
import com.homerun.domain.plan.plan_jeonse.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PlanService {

    private final PlanRepository planRepository;

    public PlanResponse create(PlanCreateRequest request) {

        Plan plan = new Plan();

        plan.setUserId(request.getUserId());
        plan.setLeaseType(request.getLeaseType());
        plan.setStartSituation(request.getStartSituation());

        Plan saved = planRepository.save(plan);

        return PlanResponse.from(saved);
    }
}