package com.homerun.domain.plan.service;

import com.homerun.domain.dashboard.entity.Deadline;
import com.homerun.domain.dashboard.repository.DeadlineRepository;
import com.homerun.domain.plan.dto.request.CompletePlanStepRequest;
import com.homerun.domain.plan.dto.request.CreatePlanRequest;
import com.homerun.domain.plan.dto.request.UpdatePlanLocationRequest;
import com.homerun.domain.plan.dto.response.PlanProgressResponse;
import com.homerun.domain.plan.dto.response.PlanResponse;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanStep;
import com.homerun.domain.plan.entity.StepTask;
import com.homerun.domain.plan.policy.PlanStageTransitionPolicy;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.repository.PlanStepRepository;
import com.homerun.domain.plan.repository.StepTaskRepository;
import com.homerun.domain.plan.type.PlanGate;
import com.homerun.domain.plan.type.PlanStage;
import com.homerun.domain.plan.type.PlanStatus;
import com.homerun.domain.plan.type.PlanStepStatus;
import com.homerun.domain.plan.type.StepTaskTemplate;
import com.homerun.domain.plan.validation.PlanInputCompletionValidator;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanService {

    private final PlanRepository planRepository;
    private final PlanStepRepository planStepRepository;
    private final StepTaskRepository stepTaskRepository;
    private final DeadlineRepository deadlineRepository;
    private final PlanStageTransitionPolicy transitionPolicy;
    private final PlanInputCompletionValidator inputCompletionValidator;

    public PlanService(
            PlanRepository planRepository,
            PlanStepRepository planStepRepository,
            StepTaskRepository stepTaskRepository,
            DeadlineRepository deadlineRepository,
            PlanStageTransitionPolicy transitionPolicy,
            PlanInputCompletionValidator inputCompletionValidator) {
        this.planRepository = planRepository;
        this.planStepRepository = planStepRepository;
        this.stepTaskRepository = stepTaskRepository;
        this.deadlineRepository = deadlineRepository;
        this.transitionPolicy = transitionPolicy;
        this.inputCompletionValidator = inputCompletionValidator;
    }

    @Transactional
    public PlanResponse create(Long memberId, CreatePlanRequest request) {
        /*
         * 아직 벤치에 있는 같은 유형의 계획이 있으면 그것을 이어 쓴다.
         *
         * 준비 문진은 계획 생성 · 입력 저장 · 관문 완료 세 번에 걸쳐 저장한다. 뒤에서 한 번
         * 실패하면 사용자는 문진을 다시 마치는데, 그때 계획을 또 만들면 앞서 만든 계획이
         * ACTIVE 인 채로 남는다. {@code GET /plans/active} 는 가장 최근 것만 주므로 그 계획은
         * 어디서도 다시 열리지 않는다.
         *
         * 벤치를 벗어난 계획은 집어 오지 않는다 — 진단을 이미 시작한 사람이 새로 시작하려는
         * 것일 수 있고, 그 계획을 덮어쓰면 안 된다. 임대차 유형이 다른 것도 같은 이유로 뺀다.
         * 계획을 여러 개 두는 것 자체는 막지 않는다.
         */
        Optional<Plan> resumable = planRepository
                .findFirstByMemberIdAndStatusOrderByUpdatedAtDescIdDesc(memberId, PlanStatus.ACTIVE)
                .filter(found -> found.getStage() == PlanStage.BENCH)
                .filter(found -> found.getLeaseType() == request.leaseType());
        if (resumable.isPresent()) {
            Plan existing = resumable.get();
            return PlanResponse.from(existing, findSteps(existing.getId()));
        }

        Plan plan = planRepository.save(Plan.create(memberId, request.leaseType(), request.targetMoveDate()));
        List<PlanStep> steps = planStepRepository.saveAll(PlanStep.defaultSteps(plan.getId()));
        List<StepTask> tasks = createDefaultTasks(plan, steps);
        createDefaultDeadlines(plan, tasks);
        return PlanResponse.from(plan, steps);
    }

    @Transactional(readOnly = true)
    public PlanResponse get(Long memberId, Long planId) {
        Plan plan = findOwnedPlan(memberId, planId);
        return PlanResponse.from(plan, findSteps(planId));
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> getAll(Long memberId) {
        return planRepository.findAllByMemberIdOrderByUpdatedAtDescIdDesc(memberId).stream()
                .map(plan -> PlanResponse.from(plan, findSteps(plan.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PlanResponse getActive(Long memberId) {
        Plan plan = planRepository
                .findFirstByMemberIdAndStatusOrderByUpdatedAtDescIdDesc(memberId, PlanStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_ACTIVE_NOT_FOUND));
        return PlanResponse.from(plan, findSteps(plan.getId()));
    }

    @Transactional(readOnly = true)
    public PlanProgressResponse getProgress(Long memberId, Long planId) {
        Plan plan = findOwnedPlan(memberId, planId);
        return PlanProgressResponse.from(plan, findSteps(planId));
    }

    @Transactional
    public PlanResponse updateLastLocation(Long memberId, Long planId, UpdatePlanLocationRequest request) {
        Plan plan = findOwnedPlan(memberId, planId);
        plan.updateLastLocation(request.locationCode());
        return PlanResponse.from(plan, findSteps(planId));
    }

    @Transactional
    public PlanResponse enterStage(
            Long memberId, Long planId, PlanStage targetStage, UpdatePlanLocationRequest request) {
        Plan plan = findOwnedPlan(memberId, planId);
        plan.enterStage(targetStage, request.locationCode());
        return PlanResponse.from(plan, findSteps(planId));
    }

    @Transactional
    public PlanProgressResponse completeStep(
            Long memberId, Long planId, String stepCode, CompletePlanStepRequest request) {
        Plan plan = findOwnedPlan(memberId, planId);
        plan.verifyRuleVersion(request.ruleVersion());
        List<PlanStep> steps = findSteps(planId);
        PlanStep target = steps.stream()
                .filter(step -> step.getStepCode().equals(stepCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_STEP_NOT_FOUND));

        if (target.getStatus() == PlanStepStatus.LOCKED) {
            throw new BusinessException(ErrorCode.PLAN_STEP_LOCKED);
        }
        if (target.getStatus() != PlanStepStatus.DONE) {
            PlanGate gate = PlanGate.findByCode(target.getStepCode())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_STEP_NOT_FOUND));
            inputCompletionValidator.validate(planId, gate);
        }
        if (target.complete()) {
            transitionPolicy.applyCompletedGate(plan, target);
            List<String> completedCodes = steps.stream()
                    .filter(step -> step.getStatus() == PlanStepStatus.DONE)
                    .map(PlanStep::getStepCode)
                    .toList();
            steps.forEach(step -> step.unlockWhenDependenciesCompleted(completedCodes));
        }
        return PlanProgressResponse.from(plan, steps);
    }

    @Transactional
    public PlanProgressResponse reset(Long memberId, Long planId) {
        Plan plan = findOwnedPlan(memberId, planId);
        List<PlanStep> steps = findSteps(planId);
        plan.reset();
        steps.forEach(PlanStep::reset);
        stepTaskRepository.findAllByPlanId(planId).forEach(StepTask::reset);
        return PlanProgressResponse.from(plan, steps);
    }

    private Plan findOwnedPlan(Long memberId, Long planId) {
        Plan plan = planRepository.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        return plan;
    }

    private List<PlanStep> findSteps(Long planId) {
        return planStepRepository.findAllByPlanIdOrderBySequenceAsc(planId);
    }

    private List<StepTask> createDefaultTasks(Plan plan, List<PlanStep> steps) {
        List<StepTask> tasks = steps.stream()
                .flatMap(step -> PlanGate.findByCode(step.getStepCode())
                        .map(gate -> StepTaskTemplate.of(gate, plan.getLeaseType()).stream()
                                .map(template -> StepTask.of(step.getId(), template)))
                        .orElseGet(java.util.stream.Stream::empty))
                .toList();
        return stepTaskRepository.saveAll(tasks);
    }

    private void createDefaultDeadlines(Plan plan, List<StepTask> tasks) {
        if (plan.getTargetMoveDate() == null) {
            return;
        }
        // 은행 사전상담은 계약보다 먼저 해야 한다(PRP-02-01). 입주 예정일에서 역산한
        // 권장 착수일이라 LEGAL 이 아니라 RECOMMENDED 다.
        tasks.stream()
                .filter(task -> task.getTaskCode().equals(StepTaskTemplate.BANK_CONSULTATION.code()))
                .findFirst()
                .map(task -> Deadline.movePreparation(plan.getId(), task.getId(), plan.getTargetMoveDate()))
                .ifPresent(deadlineRepository::save);
    }
}
