package com.homerun.domain.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.auth.type.AuthProvider;
import com.homerun.domain.dashboard.dto.response.DashboardResponse;
import com.homerun.domain.dashboard.repository.DeadlineRepository;
import com.homerun.domain.dashboard.type.DeadlineType;
import com.homerun.domain.member.entity.Member;
import com.homerun.domain.member.repository.MemberRepository;
import com.homerun.domain.plan.dto.request.CompletePlanStepRequest;
import com.homerun.domain.plan.dto.request.CreatePlanRequest;
import com.homerun.domain.plan.dto.request.PlanInputRequest;
import com.homerun.domain.plan.dto.response.PlanResponse;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.service.PlanInputService;
import com.homerun.domain.plan.service.PlanService;
import com.homerun.domain.plan.type.CompanySize;
import com.homerun.domain.plan.type.EmploymentType;
import com.homerun.domain.plan.type.HouseType;
import com.homerun.domain.plan.type.HouseholderStatus;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.plan.type.MaritalStatus;
import com.homerun.domain.plan.type.PlanInputUnknownField;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class DashboardDeadlineIntegrationTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PlanService planService;

    @Autowired
    private PlanInputService planInputService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private DeadlineRepository deadlineRepository;

    @Test
    @DisplayName("계획을 만들면 벤치 관문의 할 일부터 대시보드에 뜬다")
    void should_exposeFirstGateTasks_when_planIsCreated() {
        Long memberId = givenMember();
        PlanResponse plan = planService.create(memberId, new CreatePlanRequest(LeaseType.JEONSE, moveDate()));

        DashboardResponse dashboard = dashboardService.get(memberId, plan.id());

        // 뒤 관문은 아직 잠겨 있으므로 그 안의 할 일도 목록에 없다.
        assertThat(dashboard.prioritizedTasks())
                .extracting(task -> task.taskCode())
                .containsExactly("AGREE_TERMS", "INPUT_BASIC_PROFILE");
        assertThat(dashboard.progress().totalTasks()).isGreaterThan(2);
        assertThat(dashboard.progress().completedTasks()).isZero();
    }

    @Test
    @DisplayName("은행 사전상담 할 일에 입주 예정일에서 역산한 권장 마감이 붙는다")
    void should_exposePersistedDeadline_onBankConsultationTask() {
        Long memberId = givenMember();
        LocalDate targetMoveDate = moveDate();
        PlanResponse plan = planService.create(memberId, new CreatePlanRequest(LeaseType.JEONSE, targetMoveDate));

        CompletePlanStepRequest completeRequest = new CompletePlanStepRequest(Plan.CURRENT_RULE_VERSION);
        planService.completeStep(memberId, plan.id(), "BENCH_ONBOARDING", completeRequest);
        givenDiagnosisInput(memberId, plan.id());
        planService.completeStep(memberId, plan.id(), "FIRST_DIAGNOSIS", completeRequest);

        DashboardResponse dashboard = dashboardService.get(memberId, plan.id());

        assertThat(deadlineRepository.findAllByPlanIdAndTaskIdIsNotNull(plan.id()))
                .hasSize(1);
        assertThat(dashboard.prioritizedTasks()).first().satisfies(task -> {
            assertThat(task.stepCode()).isEqualTo("SECOND_POLICY_SELECTION");
            assertThat(task.taskCode()).isEqualTo("BANK_CONSULTATION");
            assertThat(task.deadlineType()).isEqualTo(DeadlineType.RECOMMENDED);
            assertThat(task.deadlineLabel()).isEqualTo("은행 상담 시작 권장일");
            assertThat(task.dueDate()).isEqualTo(targetMoveDate.minusDays(21));
        });
    }

    @Test
    @DisplayName("전입신고·확정일자는 되돌릴 수 없는 할 일로 표시된다")
    void should_markMoveInTasksIrreversible() {
        Long memberId = givenMember();
        PlanResponse plan = planService.create(memberId, new CreatePlanRequest(LeaseType.JEONSE, moveDate()));

        CompletePlanStepRequest completeRequest = new CompletePlanStepRequest(Plan.CURRENT_RULE_VERSION);
        planService.completeStep(memberId, plan.id(), "BENCH_ONBOARDING", completeRequest);
        givenDiagnosisInput(memberId, plan.id());
        planService.completeStep(memberId, plan.id(), "FIRST_DIAGNOSIS", completeRequest);
        planService.completeStep(memberId, plan.id(), "SECOND_POLICY_SELECTION", completeRequest);

        DashboardResponse dashboard = dashboardService.get(memberId, plan.id());

        assertThat(dashboard.prioritizedTasks())
                .filteredOn(task -> task.irreversible())
                .extracting(task -> task.taskCode())
                .contains("MOVE_IN_REPORT", "FIXED_DATE", "BALANCE_PAYMENT");
    }

    @Test
    @DisplayName("월세 계획에는 보증금 전제 할 일이 생기지 않는다")
    void should_skipDepositTasks_when_leaseTypeIsWolse() {
        Long memberId = givenMember();
        PlanResponse plan = planService.create(memberId, new CreatePlanRequest(LeaseType.WOLSE, moveDate()));

        CompletePlanStepRequest completeRequest = new CompletePlanStepRequest(Plan.CURRENT_RULE_VERSION);
        planService.completeStep(memberId, plan.id(), "BENCH_ONBOARDING", completeRequest);
        givenDiagnosisInput(memberId, plan.id());
        planService.completeStep(memberId, plan.id(), "FIRST_DIAGNOSIS", completeRequest);

        DashboardResponse dashboard = dashboardService.get(memberId, plan.id());

        assertThat(dashboard.prioritizedTasks())
                .extracting(task -> task.taskCode())
                .contains("MONTHLY_SUPPORT_CHECK")
                .doesNotContain("BANK_CONSULTATION", "LOAN_LIMIT_CHECK");
        // 은행 상담 할 일이 없으니 그 마감도 만들어지지 않는다.
        assertThat(deadlineRepository.findAllByPlanIdAndTaskIdIsNotNull(plan.id()))
                .isEmpty();
    }

    private Long givenMember() {
        return memberRepository
                .save(Member.create(
                        AuthProvider.GOOGLE, UUID.randomUUID().toString(), "dashboard@example.com", "대시보드 사용자"))
                .getId();
    }

    private void givenDiagnosisInput(Long memberId, Long planId) {
        planInputService.save(
                memberId,
                planId,
                new PlanInputRequest(
                        100_000_000L,
                        20_000_000L,
                        500_000L,
                        100_000L,
                        800_000L,
                        null,
                        new BigDecimal("33.25"),
                        HouseType.APARTMENT,
                        true,
                        HouseholderStatus.CURRENT,
                        MaritalStatus.SINGLE,
                        EmploymentType.FULL_TIME,
                        12,
                        CompanySize.SMALL,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Set.of(
                                PlanInputUnknownField.REGION_ID,
                                PlanInputUnknownField.MONTHLY_INCOME,
                                PlanInputUnknownField.NET_ASSETS,
                                PlanInputUnknownField.AVAILABLE_CASH)));
    }

    private LocalDate moveDate() {
        return LocalDate.of(2027, 2, 1);
    }
}
