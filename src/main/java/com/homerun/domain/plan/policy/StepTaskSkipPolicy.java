package com.homerun.domain.plan.policy;

import java.util.Set;
import org.springframework.stereotype.Component;

/** 서비스 이용을 막지 않아도 되는 선택 할 일의 건너뛰기 정책. */
@Component
public class StepTaskSkipPolicy {

    // 명세에서 선택 작업으로 확정된 코드만 등록한다. 현재 확정된 선택 작업은 없다.
    private static final Set<String> SKIPPABLE_TASK_CODES = Set.of();

    public boolean isRequired(String taskCode) {
        return !isSkippable(taskCode);
    }

    public boolean isSkippable(String taskCode) {
        return SKIPPABLE_TASK_CODES.contains(taskCode);
    }
}
