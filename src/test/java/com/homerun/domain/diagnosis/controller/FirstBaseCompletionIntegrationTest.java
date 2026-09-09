package com.homerun.domain.diagnosis.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.homerun.domain.plan.dto.request.UpdatePlanLocationRequest;
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
import com.homerun.domain.plan.type.PlanInputUnknownField;
import com.homerun.domain.plan.type.PlanStage;
import com.homerun.domain.region.repository.RegionRepository;
import com.homerun.domain.terms.service.TermsService;
import com.homerun.global.security.jwt.JwtTokenProvider;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "app.jwt.secret=first-base-integration-test-secret-over-32-bytes",
            "app.jwt.access-token-expiration-seconds=3600"
        })
class FirstBaseCompletionIntegrationTest {

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
    PlanService planService;

    @Autowired
    JwtTokenProvider tokens;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    TermsService terms;

    private Long memberId;
    private Long planId;
    private String bearer;

    @BeforeEach
    void setUp() {
        when(terms.hasAgreedAllRequired(anyLong())).thenReturn(true);
        String providerId = "first-base-" + System.nanoTime();
        Member member = members.save(Member.create(AuthProvider.KAKAO, providerId, null, "tester"));
        memberId = member.getId();
        bearer = "Bearer " + tokens.createAccessToken(member);

        Plan plan = plans.save(
                Plan.create(memberId, LeaseType.JEONSE, LocalDate.now().plusMonths(6)));
        planId = plan.getId();
        planSteps.saveAll(PlanStep.defaultSteps(planId));
        planService.completeStep(
                memberId,
                planId,
                PlanGate.BENCH_ONBOARDING.code(),
                new CompletePlanStepRequest(Plan.CURRENT_RULE_VERSION));
        inputs.save(PlanInput.create(planId, validInput(Set.of())));
        inputSteps.save(new PlanInputStep(planId, DiagnosisInputStep.REVIEW));
    }

    @Test
    void completes_firstBase_and_replays_sameRevision_withoutDuplicates() throws Exception {
        MvcResult completed = mvc.perform(post(endpoint())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.replayed").value(false))
                .andExpect(jsonPath("$.data.diagnosis.verdict").value("CAUTION"))
                .andExpect(jsonPath("$.data.diagnosis.policyComparison.expectedLoanAmount")
                        .value(0))
                .andExpect(jsonPath("$.data.policies.results.length()").value(3))
                .andExpect(jsonPath("$.data.loanScenarios").isNotEmpty())
                .andExpect(jsonPath("$.data.progress.currentStage").value("SECOND"))
                .andExpect(jsonPath("$.data.progress.lastVisitedStage").value("FIRST"))
                .andExpect(jsonPath("$.data.progress.lastLocationCode").value("DIAGNOSIS_RESULT"))
                .andExpect(jsonPath("$.data.progress.steps[1].status").value("DONE"))
                .andExpect(jsonPath("$.data.progress.steps[2].status").value("READY"))
                .andReturn();

        JsonNode completedData = objectMapper
                .readTree(completed.getResponse().getContentAsString())
                .path("data");

        Long diagnosisId = jdbc.queryForObject("SELECT id FROM diagnosis WHERE plan_id=?", Long.class, planId);

        mvc.perform(post(endpoint())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.replayed").value(true))
                .andExpect(jsonPath("$.data.diagnosis.diagnosisId").value(diagnosisId))
                .andExpect(jsonPath("$.data.completedAt")
                        .value(completedData.path("completedAt").asString()));

        MvcResult restored = mvc.perform(get(resultEndpoint()).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inputRevision").value(1))
                .andExpect(jsonPath("$.data.diagnosis.diagnosisId").value(diagnosisId))
                .andExpect(jsonPath("$.data.progress.currentStage").value("SECOND"))
                .andReturn();
        JsonNode restoredData = objectMapper
                .readTree(restored.getResponse().getContentAsString())
                .path("data");
        assertThat(restoredData.path("policies")).isEqualTo(completedData.path("policies"));
        assertThat(restoredData.path("loanScenarios")).isEqualTo(completedData.path("loanScenarios"));

        assertThat(count("diagnosis")).isEqualTo(1);
        assertThat(count("cost_estimate")).isEqualTo(1);
        assertThat(count("first_base_submission")).isEqualTo(1);
    }

    @Test
    void reopens_firstBaseGate_when_sameAnswersAreSubmitted_afterReset() throws Exception {
        mvc.perform(post(endpoint())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replayed").value(false));

        // 되감기는 관문을 벤치로 되돌리지만 제출 기록은 남긴다. 사용자는 준비 문진부터 다시 밟는다.
        planService.reset(memberId, planId);
        planService.completeStep(
                memberId,
                planId,
                PlanGate.BENCH_ONBOARDING.code(),
                new CompletePlanStepRequest(Plan.CURRENT_RULE_VERSION));

        // 답을 바꾸지 않았으니 회차가 그대로다 — 재생 분기로 들어오지만 관문은 다시 열려야 한다.
        mvc.perform(post(endpoint())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replayed").value(true))
                .andExpect(jsonPath("$.data.progress.currentStage").value("SECOND"))
                .andExpect(jsonPath("$.data.progress.lastVisitedStage").value("FIRST"))
                .andExpect(jsonPath("$.data.progress.lastLocationCode").value("DIAGNOSIS_RESULT"))
                .andExpect(jsonPath("$.data.progress.steps[1].status").value("DONE"))
                .andExpect(jsonPath("$.data.progress.steps[2].status").value("READY"));

        assertThat(count("diagnosis")).isEqualTo(1);
        assertThat(count("first_base_submission")).isEqualTo(1);
    }

    @Test
    void keeps_resumeLocation_when_replayArrivesAfterUserMovedOn() throws Exception {
        mvc.perform(post(endpoint())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(1)))
                .andExpect(status().isOk());

        // 사용자가 이미 2루로 넘어갔다. 뒤늦게 도착한 재제출이 이어하기를 1루로 끌어내리면 안 된다.
        planService.enterStage(memberId, planId, PlanStage.SECOND, new UpdatePlanLocationRequest("PROPERTY_LIST"));

        mvc.perform(post(endpoint())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replayed").value(true))
                .andExpect(jsonPath("$.data.progress.lastVisitedStage").value("SECOND"))
                .andExpect(jsonPath("$.data.progress.lastLocationCode").value("PROPERTY_LIST"));

        assertThat(count("first_base_submission")).isEqualTo(1);
    }

    @Test
    void rejects_result_access_from_anotherMember() throws Exception {
        mvc.perform(post(endpoint())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(1)))
                .andExpect(status().isOk());

        Member other = members.save(Member.create(AuthProvider.KAKAO, "other-" + System.nanoTime(), null, "other"));
        String otherBearer = "Bearer " + tokens.createAccessToken(other);

        mvc.perform(get(resultEndpoint()).header("Authorization", otherBearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_003"));
    }

    @Test
    void returns_notFound_when_firstBaseWasNotCompleted() throws Exception {
        mvc.perform(get(resultEndpoint()).header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DIA_005"));
    }

    @Test
    void rejects_submission_when_reviewWasNotCompleted() throws Exception {
        inputSteps.deleteAll(inputSteps.findAllByPlanId(planId));

        mvc.perform(post(endpoint())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_021"));

        assertThat(count("diagnosis")).isZero();
    }

    @Test
    void rejects_submission_when_requiredInputIsMissing() throws Exception {
        PlanInput input = inputs.findByPlanId(planId).orElseThrow();
        input.update(validInput(null, Set.of()));
        inputs.save(input);

        mvc.perform(post(endpoint())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLAN_013"))
                .andExpect(jsonPath("$.fieldErrors[*].field").value(org.hamcrest.Matchers.hasItem("availableCash")));
    }

    @Test
    void returns_needsConfirmation_withoutInventingDiagnosis() throws Exception {
        PlanInput input = inputs.findByPlanId(planId).orElseThrow();
        input.update(validInput(null, Set.of(PlanInputUnknownField.AVAILABLE_CASH)));
        inputs.save(input);

        mvc.perform(post(endpoint())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NEEDS_CONFIRMATION"))
                .andExpect(jsonPath("$.data.diagnosis").isEmpty())
                .andExpect(jsonPath("$.data.confirmationRequiredFields[0]").value("AVAILABLE_CASH"))
                .andExpect(jsonPath("$.data.progress.currentStage").value("FIRST"));

        assertThat(count("diagnosis")).isZero();
        assertThat(count("first_base_submission")).isZero();
    }

    @Test
    void rejects_staleInputRevision() throws Exception {
        mvc.perform(post(endpoint())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(99)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_018"));
    }

    @Test
    void rolls_back_diagnosis_when_gateCompletionFails() throws Exception {
        jdbc.update("UPDATE plan_step SET status='LOCKED' WHERE plan_id=? AND step_code='FIRST_DIAGNOSIS'", planId);

        mvc.perform(post(endpoint())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_005"));

        assertThat(count("diagnosis")).isZero();
        assertThat(count("cost_estimate")).isZero();
        assertThat(count("first_base_submission")).isZero();
    }

    private PlanInputRequest validInput(Set<PlanInputUnknownField> unknownFields) {
        return validInput(20_000_000L, unknownFields);
    }

    private PlanInputRequest validInput(Long availableCash, Set<PlanInputUnknownField> unknownFields) {
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
                availableCash,
                false,
                FinancialValueSource.MANUAL,
                FinancialValueSource.MANUAL,
                true,
                unknownFields);
    }

    private String endpoint() {
        return "/api/v1/plans/" + planId + "/first-base/complete";
    }

    private String resultEndpoint() {
        return "/api/v1/plans/" + planId + "/first-base/result";
    }

    private String requestBody(int revision) {
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

    private long count(String table) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE plan_id=?", Long.class, planId);
        return count == null ? 0 : count;
    }
}
