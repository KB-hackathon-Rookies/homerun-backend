package com.homerun.domain.plan.policy;

import java.util.Set;
import org.springframework.stereotype.Component;

/** 서비스 이용을 막지 않아도 되는 선택 할 일의 건너뛰기 정책. */
@Component
public class StepTaskSkipPolicy {

    private static final Set<String> SKIPPABLE_TASK_CODES =
            Set.of("MONTHLY_SUPPORT_CHECK", "REGISTER_FIXED_EXPENSE", "FIRST_MONTH_CHECKIN");

    public boolean isSkippable(String taskCode) {
        return SKIPPABLE_TASK_CODES.contains(taskCode);
    }
}
