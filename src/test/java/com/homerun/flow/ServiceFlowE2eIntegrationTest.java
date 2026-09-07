package com.homerun.flow;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.auth.type.AuthProvider;
import com.homerun.domain.member.entity.Member;
import com.homerun.domain.member.repository.MemberRepository;
import com.homerun.domain.plan.dto.request.CompletePlanStepRequest;
import com.homerun.domain.plan.dto.request.PlanInputRequest;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.entity.PlanInputStep;
import com.homerun.domain.plan.entity.PlanStep;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanInputStepRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.repository.PlanStepRepository;
import com.homerun.domain.plan.service.PlanService;
import com.homerun.domain.plan.type.DiagnosisInputStep;
import com.homerun.domain.plan.type.EmploymentType;
import com.homerun.domain.plan.type.FinancialValueSource;
import com.homerun.domain.plan.type.HouseholderStatus;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.plan.type.PlanGate;
import com.homerun.domain.property.dto.request.BankConsultationRequest;
import com.homerun.domain.property.dto.request.PropertyDecisionRequest;
import com.homerun.domain.property.entity.Property;
import com.homerun.domain.property.entity.PropertyCheck;
import com.homerun.domain.property.repository.PropertyCheckRepository;
import com.homerun.domain.property.repository.PropertyRepository;
import com.homerun.domain.property.service.PropertyDecisionService;
import com.homerun.domain.property.type.CheckResult;
import com.homerun.domain.property.type.CollateralMethod;
import com.homerun.domain.property.type.ConsultationResultStatus;
import com.homerun.domain.property.type.ConsultedLoanProduct;
import com.homerun.domain.property.type.PropertyDiagnosisStep;
import com.homerun.domain.property.type.PropertyWorkflowStatus;
import com.homerun.domain.region.repository.RegionRepository;
import com.homerun.domain.terms.service.TermsService;
import com.homerun.global.security.jwt.JwtTokenProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 전체 서비스 플로우(1루→2루→3루→홈)를 실제 API 호출 순서로 검증하는 크로스-스테이지 E2E.
 *
 * <p>단계별 세부 판정은 각 단계의 통합테스트가 이미 검증한다. 이 테스트는 그 사이의 <b>단계 전이</b>와
 * 이어하기·권한을 한 흐름으로 잇는다. 1루 진입 전 준비(계획·기본 스텝·입력)는 프로덕션과 동일하게
 * 리포지토리로 세팅하고, 그 이후의 단계 전이는 HTTP 로 호출한다({@code FirstBaseCompletionIntegrationTest}
 * 와 같은 방식).
 */
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "app.jwt.secret=service-flow-e2e-integration-test-secret-over-32-bytes",
            "app.jwt.access-token-expiration-seconds=3600"
        })
class ServiceFlowE2eIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    MemberRepository members;

    @Autowired
    PlanRepository plans;

    @Autowired
    PlanStepRepository planSteps;

    @Autowired
    PlanInputRepository inputs;

    @Autowired
    PlanInputStepRepository inputSteps;

    @Autowired
    RegionRepository regions;

    @Autowired
    PropertyRepository properties;

    @Autowired
    PropertyCheckRepository checks;

    @Autowired
    PropertyDecisionService decisionService;

    @Autowired
    PlanService planService;

    @Autowired
    JwtTokenProvider tokens;

    @MockitoBean
    TermsService terms;

    private Long memberId;
    private String bearer;

    @BeforeEach
    void setUp() {
        when(terms.hasAgreedAllRequired(anyLong())).thenReturn(true);
        Member member = members.save(Member.create(AuthProvider.KAKAO, "flow-" + System.nanoTime(), null, "tester"));
        memberId = member.getId();
        bearer = "Bearer " + tokens.createAccessToken(member);
    }

    @Test
    @DisplayName("회원이 계획을 만들고 1루 진단을 완료하면 2루로 전이하고 결과를 이어서 볼 수 있다")
    void first_base_completion_advances_to_second_and_is_resumable() throws Exception {
        Long planId = createJeonsePlanReadyForFirstBase();

        // 1루 완료 → 2루 전이
        mvc.perform(post("/api/v1/plans/" + planId + "/first-base/complete")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstBaseBody(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.progress.currentStage").value("SECOND"))
                .andExpect(jsonPath("$.data.progress.lastVisitedStage").value("FIRST"));

        // 이어하기: 진행 중 계획과 1루 결과가 그대로 복원된다
        mvc.perform(get("/api/v1/plans/active").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(planId))
                .andExpect(jsonPath("$.data.stage").value("SECOND"));
        mvc.perform(get("/api/v1/plans/" + planId + "/first-base/result").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progress.currentStage").value("SECOND"));
    }

    @Test
    @DisplayName("다른 회원은 남의 계획 하위 리소스에 접근할 수 없다(403)")
    void other_member_cannot_access_plan_resources() throws Exception {
        Long planId = createJeonsePlanReadyForFirstBase();
        mvc.perform(post("/api/v1/plans/" + planId + "/first-base/complete")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstBaseBody(1)))
                .andExpect(status().isOk());

        Member other = members.save(Member.create(AuthProvider.KAKAO, "other-" + System.nanoTime(), null, "other"));
        String otherBearer = "Bearer " + tokens.createAccessToken(other);

        mvc.perform(get("/api/v1/plans/" + planId + "/progress").header("Authorization", otherBearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_003"));
        mvc.perform(get("/api/v1/plans/" + planId + "/first-base/result").header("Authorization", otherBearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_003"));
    }

    @Test
    @DisplayName("1루→2루→3루: 매물 검증·상담·확정을 마치고 2루를 완료하면 3루로 전이한다")
    void completes_second_base_and_advances_to_third() throws Exception {
        Long planId = createJeonsePlanReadyForFirstBase();
        mvc.perform(post("/api/v1/plans/" + planId + "/first-base/complete")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstBaseBody(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progress.currentStage").value("SECOND"));

        int decisionRevision = verifyPropertyAndDecide(planId);

        mvc.perform(post("/api/v1/plans/" + planId + "/second-base/complete")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondBaseBody(decisionRevision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progress.currentStage").value("THIRD"))
                .andExpect(jsonPath("$.data.progress.lastVisitedStage").value("SECOND"));
    }

    @Test
    @DisplayName("홈에 도달해도 계획은 종료되지 않고 ACTIVE 로 이어진다")
    void plan_stays_active_at_home() throws Exception {
        Plan plan = plans.save(Plan.create(memberId, LeaseType.JEONSE, null));
        Long planId = plan.getId();
        planSteps.saveAll(PlanStep.defaultSteps(planId));
        plan.advance(); // FIRST
        plan.advance(); // SECOND
        plan.advance(); // THIRD
        plan.advance(); // HOME
        plans.save(plan);

        mvc.perform(get("/api/v1/plans/" + planId + "/progress").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStage").value("HOME"))
                .andExpect(jsonPath("$.data.planStatus").value("ACTIVE"));
    }

    /** GREEN 매물 등록 + 상담(가능) + 확정까지. 2루 complete 에 넘길 decisionRevision 을 돌려준다. */
    private int verifyPropertyAndDecide(Long planId) {
        Property property = candidate(planId);
        ReflectionTestUtils.setField(property, "workflowStep", PropertyDiagnosisStep.COMPLETE);
        ReflectionTestUtils.setField(property, "workflowStatus", PropertyWorkflowStatus.READY_FOR_CONSULTATION);
        Long propertyId = properties.save(property).getId();
        checks.saveAll(List.of(
                pass(propertyId, "VIOLATION_BUILDING"),
                pass(propertyId, "NON_RESIDENTIAL"),
                pass(propertyId, "OWNER_MATCH"),
                pass(propertyId, "TRUST_REGISTRATION"),
                pass(propertyId, "REGISTRY_RESTRICTION")));
        var consultation = decisionService.addConsultation(
                memberId,
                planId,
                propertyId,
                new BankConsultationRequest(
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
                        null));
        return decisionService
                .decide(memberId, planId, new PropertyDecisionRequest(propertyId, consultation.consultationId()))
                .decisionRevision();
    }

    private PropertyCheck pass(Long propertyId, String code) {
        return new PropertyCheck(propertyId, code, code, CheckResult.PASS, null, null, Instant.now());
    }

    private String secondBaseBody(int decisionRevision) {
        return """
                {"expectedDecisionRevision": %d, "ruleVersion": "1.0"}
                """.formatted(decisionRevision);
    }

    @Test
    @DisplayName("2루 매물은 계획당 5개까지만 등록되고 6번째는 거부된다(PRP_008)")
    void property_registration_is_capped_at_five_per_plan() throws Exception {
        Long planId = createJeonsePlanReadyForFirstBase();
        mvc.perform(post("/api/v1/plans/" + planId + "/first-base/complete")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstBaseBody(1)))
                .andExpect(status().isOk());

        for (int i = 0; i < 5; i++) {
            properties.save(candidate(planId));
        }

        // 6번째는 외부 조회 이전에 개수 제한으로 막힌다.
        mvc.perform(post("/api/v1/plans/" + planId + "/properties/analysis")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analysisBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRP_008"));
    }

    private Property candidate(Long planId) {
        return Property.candidate(
                planId,
                "1168010100",
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
    }

    private String analysisBody() {
        return """
                {
                  "house": {
                    "legalDistrictCode": "1168010100",
                    "mountain": false,
                    "mainLotNumber": "123",
                    "subLotNumber": "4",
                    "roadAddress": "서울특별시 강남구 테헤란로 123",
                    "jibunAddress": "서울특별시 강남구 역삼동 123-4",
                    "buildingName": "홈런아파트",
                    "dealYearMonth": "202608"
                  },
                  "deposit": 100000000
                }
                """;
    }

    /** 계획 생성 + 온보딩 완료 + 1루 입력 저장까지. 1루 complete 를 호출할 수 있는 상태로 만든다. */
    private Long createJeonsePlanReadyForFirstBase() {
        Plan plan = plans.save(
                Plan.create(memberId, LeaseType.JEONSE, LocalDate.now().plusMonths(6)));
        Long planId = plan.getId();
        planSteps.saveAll(PlanStep.defaultSteps(planId));
        planService.completeStep(
                memberId,
                planId,
                PlanGate.BENCH_ONBOARDING.code(),
                new CompletePlanStepRequest(Plan.CURRENT_RULE_VERSION));
        inputs.save(PlanInput.create(planId, validInput()));
        inputSteps.save(new PlanInputStep(planId, DiagnosisInputStep.REVIEW));
        return planId;
    }

    private PlanInputRequest validInput() {
        Long regionId = regions.findAll().get(0).getId();
        return new PlanInputRequest(
                100_000_000L,
                5_000_000L,
                0L,
                100_000L,
                null,
                regionId,
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

    private String firstBaseBody(int revision) {
        return """
                {
                  "expectedRevision": %d,
                  "ruleVersion": "1.0",
                  "calculation": {
                    "movingCost": 1000000,
                    "brokerageFee": 500000,
                    "guaranteeFee": 300000,
                    "stampTax": 75000,
                    "emergencyReserve": 3000000,
                    "monthlyLivingExpense": 900000,
                    "monthlyDebtPayment": 200000
                  }
                }
                """.formatted(revision);
    }
}
