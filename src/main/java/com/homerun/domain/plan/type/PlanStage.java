package com.homerun.domain.plan.type;

import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;

public enum PlanStage {
    BENCH,
    FIRST,
    SECOND,
    THIRD,
    HOME;

    public PlanStage next() {
        return switch (this) {
            case BENCH -> FIRST;
            case FIRST -> SECOND;
            case SECOND -> THIRD;
            case THIRD -> HOME;
            case HOME -> throw new BusinessException(ErrorCode.INVALID_STAGE_TRANSITION);
        };
    }
}
