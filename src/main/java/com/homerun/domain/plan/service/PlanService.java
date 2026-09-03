package com.homerun.domain.plan.service;

import com.homerun.domain.plan.dto.request.PlanCreateRequest;
import com.homerun.domain.plan.dto.request.UpdatePlanLocationRequest;
import com.homerun.domain.plan.dto.response.PlanProgressResponse;
import com.homerun.domain.plan.dto.response.PlanResponse;
import com.homerun.domain.plan.dto.response.PlanStepResponse;
import com.homerun.domain.plan.dto.response.StepTaskResponse;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanStep;
import com.homerun.domain.plan.entity.StepTask;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.repository.PlanStepRepository;
import com.homerun.domain.plan.repository.StepTaskRepository;
import com.homerun.domain.plan.type.PlanStepStatus;
import com.homerun.domain.plan.type.StepTaskStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PlanService {

    private final PlanRepository planRepository;
    private final PlanStepRepository planStepRepository;
    private final PlanStepInitializer planStepInitializer;
    private final StepTaskRepository stepTaskRepository;

    /**
     * Plan 생성
     */
    public PlanResponse create(Long memberId, PlanCreateRequest request) {

        // 1. Plan 생성
        Plan plan = new Plan();
        plan.setUserId(memberId);
        plan.setLeaseType(request.getLeaseType());
        plan.setStartSituation(request.getStartSituation());

        // 2. Plan 저장
        Plan savedPlan = planRepository.save(plan);

        // 3. 기본 Step 생성
        planStepInitializer.initialize(savedPlan.getId(), request.getLeaseType());

        // 4. Plan + Step + Task 반환
        return createPlanResponse(savedPlan);
    }

    /**
     * Plan 조회
     */
    @Transactional(readOnly = true)
    public PlanResponse get(Long memberId, Long planId) {

        Plan plan = getPlan(memberId, planId);

        return createPlanResponse(plan);
    }

    /**
     * Plan 진행률 조회
     */
    @Transactional(readOnly = true)
    public PlanProgressResponse getProgress(Long memberId, Long planId) {

        Plan plan = getPlan(memberId, planId);

        List<PlanStep> steps = getSteps(planId);

        int totalSteps = steps.size();

        int completedSteps = countCompletedSteps(steps);

        int progressPercent = calculateProgress(completedSteps, totalSteps);

        // 현재 진행 중인 Step
        PlanStep currentStep = findCurrentStep(steps);

        // 현재 진행 중인 Task
        StepTask currentTask = null;

        if (currentStep != null) {
            currentTask = stepTaskRepository.findAllByPlanStepIdOrderBySequence(currentStep.getId()).stream()
                    .filter(task -> task.getStatus() == StepTaskStatus.TODO)
                    .findFirst()
                    .orElse(null);
        }

        return PlanProgressResponse.builder()
                .totalSteps(totalSteps)
                .completedSteps(completedSteps)
                .progressPercent(progressPercent)

                // 현재 Step
                .currentStepCode(currentStep != null ? currentStep.getStepCode() : null)
                .currentStepName(currentStep != null ? currentStep.getStepName() : null)

                // 현재 Task
                .currentTaskCode(currentTask != null ? currentTask.getTaskCode() : null)
                .currentTaskName(currentTask != null ? currentTask.getTaskName() : null)
                .build();
    }

    /**
     * 마지막 진행 위치 저장
     */
    public PlanResponse updateLastLocation(Long memberId, Long planId, UpdatePlanLocationRequest request) {

        Plan plan = getPlan(memberId, planId);

        // Step 존재 여부 확인
        PlanStep step = getStep(planId, request.getStepCode());

        // Task 존재 여부 확인
        getTask(step.getId(), request.getTaskCode());

        // 마지막 위치 저장
        plan.setLastStepCode(request.getStepCode());
        plan.setLastTaskCode(request.getTaskCode());

        return createPlanResponse(plan);
    }

    /**
     * Plan 진행 상태 초기화
     */
    public PlanProgressResponse reset(Long memberId, Long planId) {

        Plan plan = getPlan(memberId, planId);

        List<PlanStep> steps = getSteps(planId);

        for (PlanStep step : steps) {

            // Step 초기화
            step.reset();

            // Task 초기화
            List<StepTask> tasks = getTasks(step.getId());

            tasks.forEach(StepTask::reset);
        }

        // 마지막 위치 초기화
        plan.setLastStepCode(null);
        plan.setLastTaskCode(null);

        return getProgress(memberId, planId);
    }

    /**
     * Task 완료
     *
     * stepCode (enum 으로 정의 예정)
     *
     *
     * "USER_INFO"
     * "DIAGNOSIS"
     * "PROGRESS"
     * "AFTERCARE"
     *
     * "사용자 정보 입력 받기"
     * "진단"
     * "진행"
     * "사후"
     *
     *
     * taskCode  (enum 으로 정의 예정 , 플로우 정의 테스트 후 재정의 예정)
     *
     * "BANK_CONSULTATION"
     * "LOAN_LIMIT_CHECK"
     * "DOCUMENT_CHECK"
     * "PROPERTY_SEARCH"
     * "BUILDING_REGISTER_CHECK"
     * "ACTUAL_PRICE_CHECK"
     * "REGISTER_CHECK"
     * "CONTRACT_CHECK"
     * "SPECIAL_CLAUSE_CHECK"
     * "DEPOSIT_PAYMENT"
     * "BALANCE_PAYMENT"
     * "MOVE_IN_REPORT"
     * "FIXED_DATE"
     * "GUARANTEE_CHECK"
     *
     * "은행 상담"
     * "전세대출 한도 확인"
     * "대출 필요 서류 확인"
     * "매물 확인"
     * "건축물대장 확인"
     * "실거래가 확인"
     * "등기부등본 확인"
     * "계약서 확인"
     * "특약 확인"
     * "계약금 지급"
     * "잔금 지급"
     * "전입신고"
     * "확정일자 확인"
     * "전세보증 가입 확인"
     */



    public PlanProgressResponse completeTask(Long memberId, Long planId, String stepCode, String taskCode) {

        Plan plan = getPlan(memberId, planId);

        // Step 조회
        PlanStep step = getStep(planId, stepCode);

        // Task 조회
        StepTask task = getTask(step.getId(), taskCode);

        // 이미 완료된 Task인지 확인
        validateTaskNotCompleted(task);

        // Task 완료
        task.complete();

        // Step이 READY라면 DOING으로 변경
        startStepIfReady(step);

        // 해당 Step의 모든 Task 완료 여부 확인
        List<StepTask> tasks = getTasks(step.getId());

        if (isAllTasksDone(tasks)) {

            // Step 완료
            step.complete();

            // 다음 Step READY
            readyNextStep(planId, step);
        }

        return getProgress(memberId, planId);
    }

    // =========================================================
    // Private Methods
    // =========================================================

    /**
     * Plan 조회 + 소유권 검증
     */
    private Plan getPlan(Long memberId, Long planId) {

        Plan plan = planRepository.findById(planId).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 계획입니다."));

        if (!plan.getUserId().equals(memberId)) {
            throw new IllegalArgumentException("해당 계획에 접근할 수 없습니다.");
        }

        return plan;
    }

    /**
     * Plan의 Step 목록 조회
     */
    private List<PlanStep> getSteps(Long planId) {

        return planStepRepository.findAllByPlanIdOrderByStepGroup(planId);
    }

    /**
     * Step 조회
     */
    private PlanStep getStep(Long planId, String stepCode) {

        return planStepRepository
                .findByPlanIdAndStepCode(planId, stepCode)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 단계입니다."));
    }

    /**
     * Task 조회
     */
    private StepTask getTask(Long planStepId, String taskCode) {

        return stepTaskRepository
                .findByPlanStepIdAndTaskCode(planStepId, taskCode)
                .orElseThrow(() -> new IllegalArgumentException("해당 단계에 존재하지 않는 작업입니다."));
    }

    /**
     * Step의 Task 목록 조회
     */
    private List<StepTask> getTasks(Long planStepId) {

        return stepTaskRepository.findAllByPlanStepIdOrderBySequence(planStepId);
    }

    /**
     * PlanResponse 생성
     *
     * Plan
     *  └── Step
     *       └── Task
     */
    private PlanResponse createPlanResponse(Plan plan) {

        List<PlanStepResponse> stepResponses = getStepResponses(plan.getId());

        return PlanResponse.from(plan, stepResponses);
    }

    /**
     * Step + Task → Response 변환
     */
    private List<PlanStepResponse> getStepResponses(Long planId) {

        List<PlanStep> steps = getSteps(planId);

        return steps.stream().map(this::createStepResponse).toList();
    }

    /**
     * 하나의 Step Response 생성
     */
    private PlanStepResponse createStepResponse(PlanStep step) {

        List<StepTaskResponse> taskResponses =
                getTasks(step.getId()).stream().map(StepTaskResponse::from).toList();

        return PlanStepResponse.from(step, taskResponses);
    }

    /**
     * 완료된 Step 수 계산
     */
    private int countCompletedSteps(List<PlanStep> steps) {

        return (int) steps.stream()
                .filter(step -> step.getStatus() == PlanStepStatus.DONE)
                .count();
    }

    /**
     * 진행률 계산
     */
    private int calculateProgress(int completedSteps, int totalSteps) {

        if (totalSteps == 0) {
            return 0;
        }

        return (completedSteps * 100) / totalSteps;
    }

    /**
     * 현재 진행 중인 Step 조회
     */
    private PlanStep findCurrentStep(List<PlanStep> steps) {

        return steps.stream()
                .filter(step -> step.getStatus() == PlanStepStatus.READY || step.getStatus() == PlanStepStatus.DOING)
                .findFirst()
                .orElse(null);
    }

    /**
     * Task 완료 여부 검증
     */
    private void validateTaskNotCompleted(StepTask task) {

        if (task.getStatus() == StepTaskStatus.DONE) {
            throw new IllegalArgumentException("이미 완료된 작업입니다.");
        }
    }

    /**
     * READY Step을 DOING으로 변경
     */
    private void startStepIfReady(PlanStep step) {

        if (step.getStatus() == PlanStepStatus.READY) {
            step.start();
        }
    }

    /**
     * 모든 Task가 완료되었는지 확인
     */
    private boolean isAllTasksDone(List<StepTask> tasks) {

        return !tasks.isEmpty() && tasks.stream().allMatch(task -> task.getStatus() == StepTaskStatus.DONE);
    }

    /**
     * 다음 Step을 READY로 변경
     */
    private void readyNextStep(Long planId, PlanStep currentStep) {

        List<PlanStep> steps = getSteps(planId);

        steps.stream()
                .filter(step -> step.getStepGroup() == currentStep.getStepGroup() + 1)
                .findFirst()
                .ifPresent(PlanStep::ready);
    }
}
