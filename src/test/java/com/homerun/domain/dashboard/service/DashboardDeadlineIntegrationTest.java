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
import com.homerun.domain.plan.dto.response.PlanResponse;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.service.PlanService;
import com.homerun.domain.plan.type.LeaseType;
import java.time.LocalDate;
import java.util.UUID;
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
    private DashboardService dashboardService;

    @Autowired
    private DeadlineRepository deadlineRepository;

    @Test
    void should_exposePersistedDeadlineAndIrreversibility_onDashboard() {
        Member member = memberRepository.save(
                Member.create(AuthProvider.GOOGLE, UUID.randomUUID().toString(), "dashboard@example.com", "대시보드 사용자"));
        LocalDate targetMoveDate = LocalDate.of(2027, 2, 1);
        PlanResponse plan = planService.create(member.getId(), new CreatePlanRequest(LeaseType.JEONSE, targetMoveDate));

        CompletePlanStepRequest completeRequest = new CompletePlanStepRequest(Plan.CURRENT_RULE_VERSION);
        planService.completeStep(member.getId(), plan.id(), "BENCH_ONBOARDING", completeRequest);
        planService.completeStep(member.getId(), plan.id(), "FIRST_DIAGNOSIS", completeRequest);
        planService.completeStep(member.getId(), plan.id(), "SECOND_POLICY_SELECTION", completeRequest);

        DashboardResponse dashboard = dashboardService.get(member.getId(), plan.id());

        assertThat(deadlineRepository.findAllByPlanIdAndStepIdIsNotNull(plan.id()))
                .hasSize(1);
        assertThat(dashboard.prioritizedTasks()).singleElement().satisfies(task -> {
            assertThat(task.stepCode()).isEqualTo("THIRD_EXECUTION");
            assertThat(task.irreversible()).isTrue();
            assertThat(task.deadlineType()).isEqualTo(DeadlineType.RECOMMENDED);
            assertThat(task.deadlineLabel()).isEqualTo("은행 상담 시작 권장일");
            assertThat(task.dueDate()).isEqualTo(targetMoveDate.minusDays(21));
        });
    }
}
