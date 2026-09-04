package com.homerun.domain.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.plan.dto.request.UpdateStepTaskStatusRequest;
import com.homerun.domain.plan.dto.response.PlanTaskProgressResponse;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.entity.PlanStep;
import com.homerun.domain.plan.entity.StepTask;
import com.homerun.domain.plan.policy.PlanStageTransitionPolicy;
import com.homerun.domain.plan.policy.StepTaskSkipPolicy;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.repository.PlanStepRepository;
import com.homerun.domain.plan.repository.StepTaskRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.plan.type.PlanInputUnknownField;
import com.homerun.domain.plan.type.PlanStage;
import com.homerun.domain.plan.type.PlanStepStatus;
import com.homerun.domain.plan.type.StepTaskStatus;
import com.homerun.domain.plan.type.StepTaskTemplate;
import com.homerun.domain.plan.validation.PlanInputCompletionValidator;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.exception.FieldValidationException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PlanTaskServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private PlanStepRepository planStepRepository;

    @Mock
    private StepTaskRepository stepTaskRepository;

    @Mock
    private PlanInputRepository planInputRepository;

    private PlanTaskService planTaskService;
    private Plan plan;
    private List<PlanStep> steps;

    @BeforeEach
    void setUp() {
        planTaskService = new PlanTaskService(
                planRepository,
                planStepRepository,
                stepTaskRepository,
                new PlanStageTransitionPolicy(),
                new StepTaskSkipPolicy(),
                new PlanInputCompletionValidator(planInputRepository));
        plan = Plan.create(MEMBER_ID, LeaseType.JEONSE, LocalDate.of(2027, 2, 1));
        ReflectionTestUtils.setField(plan, "id", PLAN_ID);
        steps = PlanStep.defaultSteps(PLAN_ID);
        for (int i = 0; i < steps.size(); i++) {
            ReflectionTestUtils.setField(steps.get(i), "id", 100L + i);
        }
    }

    @Test
    void should_returnTaskProgressAndSkipPolicy_when_tasksAreRead() {
        StepTask required = task(steps.get(0), StepTaskTemplate.AGREE_TERMS, 1000L);
        StepTask optional = task(steps.get(4), StepTaskTemplate.FIRST_MONTH_CHECKIN, 1001L);
        givenPlanAndProgress(List.of(required, optional));

        PlanTaskProgressResponse response = planTaskService.getTasks(MEMBER_ID, PLAN_ID);

        assertThat(response.totalTasks()).isEqualTo(2);
        assertThat(response.completedTasks()).isZero();
        assertThat(response.tasks()).extracting("taskCode").containsExactly("AGREE_TERMS", "FIRST_MONTH_CHECKIN");
        assertThat(response.tasks()).extracting("skippable").containsExactly(false, false);
        assertThat(response.tasks()).extracting("required").containsExactly(true, true);
    }

    @Test
    void should_markStepDoing_withoutAdvancing_when_taskStarts() {
        StepTask task = task(steps.get(0), StepTaskTemplate.AGREE_TERMS, 1000L);
        givenUpdate(List.of(task));

        PlanTaskProgressResponse response = update(task, StepTaskStatus.DOING);

        assertThat(task.getStatus()).isEqualTo(StepTaskStatus.DOING);
        assertThat(steps.get(0).getStatus()).isEqualTo(PlanStepStatus.DOING);
        assertThat(plan.getStage()).isEqualTo(PlanStage.BENCH);
        assertThat(response.progressPercent()).isZero();
    }

    @Test
    void should_completeGateAndUnlockNextGate_when_lastTaskIsCompleted() {
        StepTask first = task(steps.get(0), StepTaskTemplate.AGREE_TERMS, 1000L);
        StepTask last = task(steps.get(0), StepTaskTemplate.INPUT_BASIC_PROFILE, 1001L);
        first.complete();
        givenUpdate(List.of(first, last));

        PlanTaskProgressResponse response = update(last, StepTaskStatus.DONE);

        assertThat(last.getStatus()).isEqualTo(StepTaskStatus.DONE);
        assertThat(steps.get(0).getStatus()).isEqualTo(PlanStepStatus.DONE);
        assertThat(steps.get(1).getStatus()).isEqualTo(PlanStepStatus.READY);
        assertThat(plan.getStage()).isEqualTo(PlanStage.FIRST);
        assertThat(response.completedTasks()).isEqualTo(2);
        assertThat(response.progressPercent()).isEqualTo(100);
    }

    @Test
    void should_rejectUpdate_when_taskBelongsToLockedGate() {
        StepTask task = task(steps.get(1), StepTaskTemplate.INPUT_INCOME_ASSET, 1000L);
        givenUpdate(List.of(task));

        assertThatThrownBy(() -> update(task, StepTaskStatus.DOING))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.PLAN_STEP_LOCKED));
        assertThat(task.getStatus()).isEqualTo(StepTaskStatus.TODO);
    }

    @Test
    void should_rejectSkip_when_taskIsRequired() {
        StepTask task = task(steps.get(0), StepTaskTemplate.AGREE_TERMS, 1000L);
        givenUpdate(List.of(task));

        assertThatThrownBy(() -> update(task, StepTaskStatus.SKIPPED))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.PLAN_TASK_SKIP_NOT_ALLOWED));
    }

    @Test
    void should_rejectSkip_when_optionalPolicyIsNotConfirmed() {
        PlanStep homeStep = steps.get(4);
        ReflectionTestUtils.setField(homeStep, "status", PlanStepStatus.READY);
        ReflectionTestUtils.setField(plan, "stage", PlanStage.HOME);
        StepTask task = task(homeStep, StepTaskTemplate.FIRST_MONTH_CHECKIN, 1000L);
        givenUpdate(List.of(task));

        assertThatThrownBy(() -> update(task, StepTaskStatus.SKIPPED))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.PLAN_TASK_SKIP_NOT_ALLOWED));
    }

    @Test
    void should_rejectChangingSettledTask() {
        StepTask task = task(steps.get(0), StepTaskTemplate.AGREE_TERMS, 1000L);
        task.complete();
        givenUpdate(List.of(task));

        assertThatThrownBy(() -> update(task, StepTaskStatus.DOING))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_TASK_STATUS_TRANSITION));
    }

    @Test
    void should_rejectStatusThatCannotBeSetDirectly() {
        StepTask task = task(steps.get(0), StepTaskTemplate.AGREE_TERMS, 1000L);
        givenUpdate(List.of(task));

        assertThatThrownBy(() -> update(task, StepTaskStatus.RECALC_REQUIRED))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_TASK_STATUS_TRANSITION));
    }

    @Test
    void should_rejectAutomaticGateCompletion_when_inputSnapshotIsMissing() {
        StepTask last = prepareFirstGate();
        when(planInputRepository.findByPlanId(PLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> update(last, StepTaskStatus.DONE))
                .isInstanceOfSatisfying(
                        FieldValidationException.class,
                        exception ->
                                assertThat(exception.errorCode()).isEqualTo(ErrorCode.PLAN_REQUIRED_INPUT_MISSING));
        assertThat(steps.get(1).getStatus()).isNotEqualTo(PlanStepStatus.DONE);
        assertThat(steps.get(2).getStatus()).isEqualTo(PlanStepStatus.LOCKED);
        assertThat(plan.getStage()).isEqualTo(PlanStage.FIRST);
    }

    @Test
    void should_unlockNextGate_when_requiredInputValidationPasses() {
        StepTask last = prepareFirstGate();
        PlanInput input = mock(PlanInput.class);
        when(input.getUnknownFields()).thenReturn(List.of(PlanInputUnknownField.values()));
        when(planInputRepository.findByPlanId(PLAN_ID)).thenReturn(Optional.of(input));

        update(last, StepTaskStatus.DONE);

        assertThat(steps.get(1).getStatus()).isEqualTo(PlanStepStatus.DONE);
        assertThat(steps.get(2).getStatus()).isEqualTo(PlanStepStatus.READY);
        assertThat(plan.getStage()).isEqualTo(PlanStage.SECOND);
    }

    @Test
    void should_excludeOptionalTasksAndSkippedRequiredTasks_fromProgress() {
        StepTask done = task(steps.get(0), StepTaskTemplate.AGREE_TERMS, 1000L);
        StepTask skipped = task(steps.get(0), StepTaskTemplate.INPUT_BASIC_PROFILE, 1001L);
        StepTask optional = task(steps.get(4), StepTaskTemplate.FIRST_MONTH_CHECKIN, 1002L);
        done.complete();
        skipped.skip();
        optional.complete();
        StepTaskSkipPolicy policy = new StepTaskSkipPolicy() {
            @Override
            public boolean isSkippable(String code) {
                return code.equals("FIRST_MONTH_CHECKIN");
            }
        };

        PlanTaskProgressResponse response =
                PlanTaskProgressResponse.from(plan, steps, List.of(done, skipped, optional), policy);

        assertThat(response.totalTasks()).isEqualTo(2);
        assertThat(response.completedTasks()).isEqualTo(1);
        assertThat(response.progressPercent()).isEqualTo(50);
    }

    @Test
    void should_allowCompletionOfPreviouslySkippedRequiredTask() {
        StepTask task = task(steps.get(0), StepTaskTemplate.INPUT_BASIC_PROFILE, 1000L);
        task.skip();
        givenUpdate(List.of(task));

        update(task, StepTaskStatus.DONE);

        assertThat(task.getStatus()).isEqualTo(StepTaskStatus.DONE);
        assertThat(plan.getStage()).isEqualTo(PlanStage.FIRST);
    }

    private StepTask prepareFirstGate() {
        steps.get(0).complete();
        ReflectionTestUtils.setField(steps.get(1), "status", PlanStepStatus.READY);
        ReflectionTestUtils.setField(plan, "stage", PlanStage.FIRST);
        StepTask last = task(steps.get(1), StepTaskTemplate.REGISTER_CHECK, 1000L);
        givenUpdate(List.of(last));
        return last;
    }

    private StepTask task(PlanStep step, StepTaskTemplate template, Long id) {
        StepTask task = StepTask.of(step.getId(), template);
        ReflectionTestUtils.setField(task, "id", id);
        return task;
    }

    private void givenPlanAndProgress(List<StepTask> tasks) {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
        when(planStepRepository.findAllByPlanIdOrderBySequenceAsc(PLAN_ID)).thenReturn(steps);
        when(stepTaskRepository.findAllByPlanId(PLAN_ID)).thenReturn(tasks);
    }

    private void givenUpdate(List<StepTask> tasks) {
        givenPlanAndProgress(tasks);
    }

    private PlanTaskProgressResponse update(StepTask task, StepTaskStatus status) {
        return planTaskService.updateStatus(
                MEMBER_ID,
                PLAN_ID,
                task.getTaskCode(),
                new UpdateStepTaskStatusRequest(status, Plan.CURRENT_RULE_VERSION));
    }
}
