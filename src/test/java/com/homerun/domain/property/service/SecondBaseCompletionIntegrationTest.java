package com.homerun.domain.property.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.auth.type.AuthProvider;
import com.homerun.domain.member.entity.Member;
import com.homerun.domain.member.repository.MemberRepository;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanStep;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.repository.PlanStepRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.plan.type.PlanGate;
import com.homerun.domain.plan.type.PlanStage;
import com.homerun.domain.plan.type.PlanStepStatus;
import com.homerun.domain.property.dto.request.BankConsultationRequest;
import com.homerun.domain.property.dto.request.PropertyDecisionRequest;
import com.homerun.domain.property.dto.request.SecondBaseCompleteRequest;
import com.homerun.domain.property.entity.Property;
import com.homerun.domain.property.entity.PropertyCheck;
import com.homerun.domain.property.repository.PropertyCheckRepository;
import com.homerun.domain.property.repository.PropertyRepository;
import com.homerun.domain.property.repository.SecondBaseSubmissionRepository;
import com.homerun.domain.property.type.CheckResult;
import com.homerun.domain.property.type.CollateralMethod;
import com.homerun.domain.property.type.ConsultationResultStatus;
import com.homerun.domain.property.type.ConsultedLoanProduct;
import com.homerun.domain.property.type.PropertyDiagnosisStep;
import com.homerun.domain.property.type.PropertyWorkflowStatus;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.external.building.BuildingLotQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SecondBaseCompletionIntegrationTest {

    @Autowired
    SecondBaseCompletionService completionService;

    @Autowired
    PropertyDecisionService decisionService;

    @Autowired
    MemberRepository members;

    @Autowired
    PlanRepository plans;

    @Autowired
    PlanStepRepository planSteps;

    @Autowired
    PropertyRepository properties;

    @Autowired
    PropertyCheckRepository checks;

    @Autowired
    SecondBaseSubmissionRepository submissions;

    private Long memberId;
    private Long planId;
    private Long propertyId;

    @BeforeEach
    void setUp() {
        Member member =
                members.save(Member.create(AuthProvider.KAKAO, "second-base-" + System.nanoTime(), null, "tester"));
        memberId = member.getId();
        Plan plan = plans.save(Plan.create(memberId, LeaseType.JEONSE, null));
        planId = plan.getId();
        prepareSecondBase(plan);
        propertyId = safeProperty();
    }

    @Test
    void should_completeSecondBase_openThirdBase_andReplaySameRevision() {
        int revision = decide(completeConsultation());
        SecondBaseCompleteRequest request = new SecondBaseCompleteRequest(revision, Plan.CURRENT_RULE_VERSION);

        var completed = completionService.complete(memberId, planId, request);
        var replayed = completionService.complete(memberId, planId, request);

        assertThat(completed.replayed()).isFalse();
        assertThat(completed.progress().currentStage()).isEqualTo(PlanStage.THIRD);
        assertThat(completed.progress().lastVisitedStage()).isEqualTo(PlanStage.SECOND);
        assertThat(completed.progress().lastLocationCode()).isEqualTo("SECOND_BASE_RESULT");
        assertThat(completed.progress().steps())
                .filteredOn(step -> step.code().equals(PlanGate.THIRD_EXECUTION.code()))
                .extracting("status")
                .containsExactly(PlanStepStatus.READY);
        assertThat(replayed.replayed()).isTrue();
        assertThat(replayed.completedAt()).isEqualTo(completed.completedAt());
        assertThat(submissions.countByPlanId(planId)).isEqualTo(1);
        assertThat(completionService.result(memberId, planId).decisionRevision())
                .isEqualTo(revision);
    }

    @Test
    void should_rejectStaleDecisionRevision() {
        int revision = decide(completeConsultation());

        assertThatThrownBy(() -> completionService.complete(
                        memberId, planId, new SecondBaseCompleteRequest(revision + 1, Plan.CURRENT_RULE_VERSION)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.SECOND_BASE_DECISION_REVISION_MISMATCH));
    }

    @Test
    void should_rejectFinalSubmission_whenPossibleConsultationStillHasUnknownTerms() {
        int revision = decide(new BankConsultationRequest(
                "국민은행",
                "역삼점",
                null,
                null,
                ConsultationResultStatus.POSSIBLE,
                ConsultedLoanProduct.UNKNOWN,
                CollateralMethod.UNKNOWN,
                null,
                null,
                LocalDate.now(),
                null));

        assertThatThrownBy(() -> completionService.complete(
                        memberId, planId, new SecondBaseCompleteRequest(revision, Plan.CURRENT_RULE_VERSION)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.SECOND_BASE_FINAL_TERMS_INCOMPLETE));
        assertThat(plans.findById(planId).orElseThrow().getStage()).isEqualTo(PlanStage.SECOND);
        assertThat(submissions.countByPlanId(planId)).isZero();
    }

    private void prepareSecondBase(Plan plan) {
        List<PlanStep> steps = PlanStep.defaultSteps(planId);
        PlanStep bench = steps.get(0);
        PlanStep first = steps.get(1);
        PlanStep second = steps.get(2);
        bench.complete();
        first.unlockWhenDependenciesCompleted(List.of(PlanGate.BENCH_ONBOARDING.code()));
        first.complete();
        second.unlockWhenDependenciesCompleted(
                List.of(PlanGate.BENCH_ONBOARDING.code(), PlanGate.FIRST_DIAGNOSIS.code()));
        planSteps.saveAll(steps);
        plan.advance();
        plan.advance();
        plans.save(plan);
    }

    private Long safeProperty() {
        Property property = Property.candidate(
                planId,
                new BuildingLotQuery("1168010100", false, "123", "4"),
                "서울특별시 강남구 역삼동 123-4",
                "서울특별시 강남구 테헤란로 123",
                "홈런아파트",
                "APARTMENT",
                100_000_000L,
                200_000_000L,
                150_000_000L,
                0L,
                true,
                false,
                false,
                false,
                false,
                Instant.now());
        ReflectionTestUtils.setField(property, "workflowStep", PropertyDiagnosisStep.COMPLETE);
        ReflectionTestUtils.setField(property, "workflowStatus", PropertyWorkflowStatus.READY_FOR_CONSULTATION);
        Long id = properties.save(property).getId();
        checks.saveAll(List.of(
                pass(id, "VIOLATION_BUILDING"),
                pass(id, "NON_RESIDENTIAL"),
                pass(id, "OWNER_MATCH"),
                pass(id, "TRUST_REGISTRATION"),
                pass(id, "REGISTRY_RESTRICTION")));
        return id;
    }

    private int decide(BankConsultationRequest request) {
        var consultation = decisionService.addConsultation(memberId, planId, propertyId, request);
        return decisionService
                .decide(memberId, planId, new PropertyDecisionRequest(propertyId, consultation.consultationId()))
                .decisionRevision();
    }

    private BankConsultationRequest completeConsultation() {
        return new BankConsultationRequest(
                "국민은행",
                "역삼점",
                null,
                null,
                ConsultationResultStatus.POSSIBLE,
                ConsultedLoanProduct.BANK_LOAN,
                CollateralMethod.HF,
                80_000_000L,
                new BigDecimal("3.200"),
                LocalDate.now(),
                null);
    }

    private PropertyCheck pass(Long id, String code) {
        return new PropertyCheck(id, code, code, CheckResult.PASS, null, null, Instant.now());
    }
}
