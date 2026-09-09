package com.homerun.domain.property.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.auth.type.AuthProvider;
import com.homerun.domain.dashboard.dto.response.DashboardResumeResponse;
import com.homerun.domain.dashboard.service.DashboardService;
import com.homerun.domain.member.entity.Member;
import com.homerun.domain.member.repository.MemberRepository;
import com.homerun.domain.plan.dto.request.PlanInputRequest;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.EmploymentType;
import com.homerun.domain.plan.type.FinancialValueSource;
import com.homerun.domain.plan.type.HouseType;
import com.homerun.domain.plan.type.HouseholderStatus;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.plan.type.PlanStage;
import com.homerun.domain.property.dto.request.PropertyBuildingStepRequest;
import com.homerun.domain.property.dto.request.PropertyRegistryStepRequest;
import com.homerun.domain.property.dto.request.PropertyViolationStepRequest;
import com.homerun.domain.property.entity.Property;
import com.homerun.domain.property.repository.PropertyRepository;
import com.homerun.domain.property.type.DataSource;
import com.homerun.domain.property.type.OfficialPriceSource;
import com.homerun.domain.property.type.PropertyDiagnosisStep;
import com.homerun.domain.property.type.PropertyWorkflowStatus;
import com.homerun.domain.property.type.TrafficLight;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.external.building.BuildingLotQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PropertyWorkflowIntegrationTest {

    @Autowired
    PropertyWorkflowService service;

    @Autowired
    MemberRepository members;

    @Autowired
    PlanRepository plans;

    @Autowired
    PlanInputRepository inputs;

    @Autowired
    PropertyRepository properties;

    @Autowired
    DashboardService dashboard;

    private Long memberId;
    private Long planId;

    @BeforeEach
    void setUp() {
        Member member = members.save(
                Member.create(AuthProvider.KAKAO, "property-workflow-" + System.nanoTime(), null, "tester"));
        memberId = member.getId();
        planId = plans.save(Plan.create(memberId, LeaseType.JEONSE, null)).getId();
        inputs.save(PlanInput.create(planId, planInput()));
    }

    @Test
    void should_moveResumeToSecondBase_whenPropertyStepIsSaved() {
        enterSecondBase();
        Property property = candidate(false);

        // 1루 완료가 남긴 자리 그대로다. 매물 검증을 시작하기 전이라 아직 1루를 가리킨다.
        assertResume(PlanStage.FIRST, "DIAGNOSIS_RESULT");

        service.saveBuilding(
                memberId,
                planId,
                property.getId(),
                new PropertyBuildingStepRequest(1, HouseType.APARTMENT, new BigDecimal("42.35")));
        assertResume(PlanStage.SECOND, "VIOLATION");

        service.saveViolation(memberId, planId, property.getId(), new PropertyViolationStepRequest(2, false));
        assertResume(PlanStage.SECOND, "REGISTRY");

        service.saveRegistry(
                memberId,
                planId,
                property.getId(),
                new PropertyRegistryStepRequest(
                        3,
                        200_000_000L,
                        2026,
                        OfficialPriceSource.REALTY_PRICE_APARTMENT,
                        0L,
                        true,
                        false,
                        false,
                        false,
                        false,
                        null,
                        false));
        assertResume(PlanStage.SECOND, "COMPLETE");
    }

    @Test
    void should_keepResumeAtBlockedStep_whenViolationIsConfirmed() {
        enterSecondBase();
        Property property = candidate();

        service.saveViolation(memberId, planId, property.getId(), new PropertyViolationStepRequest(1, true));

        // 막힌 매물이라도 사용자가 있는 곳은 2루다. 막힌 자리를 그대로 가리켜야 한다.
        assertResume(PlanStage.SECOND, "VIOLATION");
    }

    @Test
    void should_resumeAtRegistry_andRestoreUnifiedCard_whenEachStepIsSaved() {
        Property property = candidate();

        var violation =
                service.saveViolation(memberId, planId, property.getId(), new PropertyViolationStepRequest(1, false));

        assertThat(violation.workflow().currentStep()).isEqualTo(PropertyDiagnosisStep.REGISTRY);
        assertThat(violation.workflow().revision()).isEqualTo(2);
        assertThat(service.resume(memberId, planId, property.getId())).isEqualTo(violation.workflow());

        var registry = service.saveRegistry(
                memberId,
                planId,
                property.getId(),
                new PropertyRegistryStepRequest(
                        2,
                        200_000_000L,
                        2026,
                        OfficialPriceSource.REALTY_PRICE_APARTMENT,
                        0L,
                        true,
                        false,
                        false,
                        false,
                        false,
                        null,
                        false));

        assertThat(registry.workflow().currentStep()).isEqualTo(PropertyDiagnosisStep.COMPLETE);
        assertThat(registry.workflow().status()).isEqualTo(PropertyWorkflowStatus.READY_FOR_CONSULTATION);
        assertThat(registry.verification().trafficLight()).isEqualTo(TrafficLight.GREEN);

        var card = service.card(memberId, planId, property.getId());
        assertThat(card.property().trafficLight()).isEqualTo(TrafficLight.GREEN);
        assertThat(card.officialPrice()).isEqualTo(200_000_000L);
        assertThat(card.officialPriceYear()).isEqualTo(2026);
        assertThat(card.officialPriceSource()).isEqualTo(OfficialPriceSource.REALTY_PRICE_APARTMENT);
        assertThat(card.checks()).isNotEmpty();
        assertThat(card.loanProducts().results()).hasSize(3);
        service.requireLoanProductsReady(memberId, planId, property.getId());
    }

    @Test
    void should_resumeAtBuilding_whenAreaLookupFailed_andSaveManualValue() {
        Property property = candidate(false);

        var result = service.saveBuilding(
                memberId,
                planId,
                property.getId(),
                new PropertyBuildingStepRequest(1, HouseType.APARTMENT, new BigDecimal("42.35")));

        assertThat(result.workflow().currentStep()).isEqualTo(PropertyDiagnosisStep.VIOLATION);
        Property saved = properties.findById(property.getId()).orElseThrow();
        assertThat(saved.getExclusiveArea()).isEqualByComparingTo("42.35");
        assertThat(saved.getAreaSource()).isEqualTo(DataSource.MANUAL);
    }

    @Test
    void should_keepRegistryStep_whenUnknownAnswersRemain() {
        Property property = candidate();
        service.saveViolation(memberId, planId, property.getId(), new PropertyViolationStepRequest(1, false));

        var result = service.saveRegistry(
                memberId,
                planId,
                property.getId(),
                new PropertyRegistryStepRequest(2, null, null, null, null, null, null, null, null, null, null, null));

        assertThat(result.workflow().currentStep()).isEqualTo(PropertyDiagnosisStep.REGISTRY);
        assertThat(result.workflow().status()).isEqualTo(PropertyWorkflowStatus.NEEDS_CONFIRMATION);
        assertThat(result.verification().trafficLight()).isEqualTo(TrafficLight.YELLOW);
        assertThatThrownBy(() -> service.requireLoanProductsReady(memberId, planId, property.getId()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.PROPERTY_LOAN_PRODUCTS_NOT_READY));
    }

    @Test
    void should_blockAndHideProducts_whenViolationBuildingIsConfirmed() {
        Property property = candidate();

        var result =
                service.saveViolation(memberId, planId, property.getId(), new PropertyViolationStepRequest(1, true));

        assertThat(result.workflow().status()).isEqualTo(PropertyWorkflowStatus.BLOCKED);
        assertThat(result.verification().trafficLight()).isEqualTo(TrafficLight.RED);
        assertThat(service.card(memberId, planId, property.getId())
                        .loanProducts()
                        .results())
                .isEmpty();
    }

    @Test
    void should_rejectStaleRevision_withoutOverwritingProgress() {
        Property property = candidate();
        service.saveViolation(memberId, planId, property.getId(), new PropertyViolationStepRequest(1, false));

        assertThatThrownBy(() -> service.saveRegistry(
                        memberId,
                        planId,
                        property.getId(),
                        new PropertyRegistryStepRequest(
                                1, null, null, null, null, null, null, null, null, null, null, null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.PROPERTY_WORKFLOW_REVISION_MISMATCH));
    }

    /** 1루를 마치고 2루에 들어선 계획. 위치는 1루 완료가 남기는 값 그대로다. */
    private void enterSecondBase() {
        Plan plan = plans.findById(planId).orElseThrow();
        plan.advance();
        plan.advance();
        plan.enterStage(PlanStage.FIRST, "DIAGNOSIS_RESULT");
        plans.save(plan);
    }

    private void assertResume(PlanStage stage, String locationCode) {
        DashboardResumeResponse resume = dashboard.get(memberId, planId).resume();
        assertThat(resume.stage()).isEqualTo(stage);
        assertThat(resume.locationCode()).isEqualTo(locationCode);
    }

    private Property candidate() {
        return candidate(true);
    }

    private Property candidate(boolean areaReady) {
        Property property = Property.candidate(
                planId,
                new BuildingLotQuery("1168010100", false, "123", "4"),
                "서울특별시 강남구 역삼동 123-4",
                "서울특별시 강남구 테헤란로 123",
                "홈런아파트",
                HouseType.APARTMENT.name(),
                100_000_000L,
                200_000_000L,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                Instant.now());
        property.recordSourcedFacts(
                "123-4",
                "401호",
                areaReady ? new BigDecimal("42.35") : null,
                areaReady ? DataSource.AUTO : null,
                DataSource.AUTO,
                areaReady);
        property.recordAutomaticSafety(false, false);
        property.startWorkflow(areaReady, false);
        return properties.save(property);
    }

    private PlanInputRequest planInput() {
        return new PlanInputRequest(
                100_000_000L,
                5_000_000L,
                0L,
                100_000L,
                null,
                null,
                null,
                null,
                true,
                HouseholderStatus.EXPECTED,
                null,
                EmploymentType.FREELANCER,
                null,
                null,
                null,
                null,
                null,
                3_000_000L,
                30_000_000L,
                20_000_000L,
                false,
                FinancialValueSource.MANUAL,
                FinancialValueSource.MANUAL,
                true,
                Set.of());
    }
}
