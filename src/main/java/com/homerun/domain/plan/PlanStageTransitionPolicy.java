package com.homerun.domain.plan;

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
            plan.finish();
            return;
        }
        plan.advance();
    }
}
