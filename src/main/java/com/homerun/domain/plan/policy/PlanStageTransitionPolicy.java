package com.homerun.domain.plan.policy;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanStep;
import com.homerun.domain.plan.type.PlanGate;
import com.homerun.domain.plan.type.PlanStage;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class PlanStageTransitionPolicy {

    public void applyCompletedGate(Plan plan, PlanStep completedStep) {
        PlanGate gate = PlanGate.findByCode(completedStep.getStepCode()).orElse(null);
        if (gate == null) {
            return;
        }
        if (gate.stage() != plan.getStage()) {
            throw new BusinessException(ErrorCode.INVALID_STAGE_TRANSITION);
        }
        if (gate.stage() == PlanStage.HOME) {
            // 홈은 완료 시점이 없는 지속 관리 단계다. 현재 작업 묶음이 끝나도 계획을 닫지 않는다.
            return;
        }
        plan.advance();
    }
}
