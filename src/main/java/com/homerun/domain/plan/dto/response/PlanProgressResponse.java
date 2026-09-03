package com.homerun.domain.plan.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlanProgressResponse {

    //전체 Step 수
    private int totalSteps;
    //완료한 Step 수
    private int completedSteps;
    //전체 진행률
    private int progressPercent;
    //현재 진행 중인 Step
    private String currentStepCode;
    //현재 진행 중인 Step 이름
    private String currentStepName;

    // 현재 진행 중인 Task
    private String currentTaskCode;
    private String currentTaskName;
}