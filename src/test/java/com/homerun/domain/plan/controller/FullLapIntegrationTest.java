package com.homerun.domain.plan.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.auth.type.AuthProvider;
import com.homerun.domain.house.dto.request.HouseAnalysisRequest;
import com.homerun.domain.house.dto.response.HouseAnalysisResponse;
import com.homerun.domain.house.dto.response.RentTransactions;
import com.homerun.domain.house.service.HouseAnalysisService;
import com.homerun.domain.member.entity.Member;
import com.homerun.domain.member.repository.MemberRepository;
import com.homerun.domain.plan.type.HouseType;
import com.homerun.domain.region.repository.RegionRepository;
import com.homerun.domain.terms.service.TermsService;
import com.homerun.global.external.building.BuildingLedgerResponse;
import com.homerun.global.external.building.BuildingRegisterResponse;
import com.homerun.global.security.jwt.JwtTokenProvider;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 벤치→1루→2루→3루→홈 한 바퀴를 <b>전부 HTTP 로</b> 돈다.
 *
 * <p>{@code ServiceFlowE2eIntegrationTest} 는 단계 <i>전이</i>를 보려고 준비 상태를 리포지토리로
 * 세팅한다. 이 테스트는 그 준비까지 화면이 실제로 쏘는 API 로만 만든다 — FE 가 보내는 값만으로
 * 홈까지 갈 수 있는지가 질문이기 때문이다. 리포지토리로 우회해 채우는 값은 하나도 없다.
 *
 * <p>공공 API(건축물대장·전월세 실거래가)만 스텁한다. 서버 밖의 의존이라 테스트에서 부를 수 없고,
 * 판정 입력은 스텁 응답이 아니라 사용자가 STEP 3·4 에서 직접 저장하는 값이 결정한다.
 */
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "app.jwt.secret=full-lap-integration-test-secret-over-32-bytes",
            "app.jwt.access-token-expiration-seconds=3600"
        })
class FullLapIntegrationTest {

    private static final long DEPOSIT = 100_000_000L;
    private static final long HOPE_DEPOSIT = 180_000_000L;

    @Autowired
    MockMvc mvc;

    @Autowired
    MemberRepository members;

    @Autowired
    RegionRepository regions;

    @Autowired
    JwtTokenProvider tokens;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    TermsService terms;

    @MockitoBean
    HouseAnalysisService houseAnalysis;

    private String bearer;

    @BeforeEach
    void setUp() {
        when(terms.hasAgreedAllRequired(anyLong())).thenReturn(true);
        when(houseAnalysis.analyze(any(HouseAnalysisRequest.class))).thenAnswer(invocation -> {
            HouseAnalysisRequest request = invocation.getArgument(0);
            return new HouseAnalysisResponse(
                    request,
                    HouseType.APARTMENT,
                    new BuildingLedgerResponse(
                            new BuildingRegisterResponse(
                                    "00",
                                    "NORMAL SERVICE.",
                                    1,
                                    List.of(Map.of("mainPurpsCdNm", "공동주택", "etcPurps", "아파트", "bldNm", "홈런아파트"))),
                            new BuildingRegisterResponse("00", "NORMAL SERVICE.", 0, List.of())),
                    new RentTransactions(true, 0, 0, null, List.of()),
                    List.of());
        });
        Member member = members.save(Member.create(AuthProvider.KAKAO, "full-lap-" + System.nanoTime(), null, "타자"));
        bearer = "Bearer " + tokens.createAccessToken(member);
    }

    @Test
    @DisplayName("회원이 화면이 쏘는 API 만으로 벤치에서 홈까지 한 바퀴를 돈다")
    void runs_full_lap_from_bench_to_home_over_http() throws Exception {
        // ── 벤치: 계획 생성 + 온보딩 게이트
        Long planId = createPlan();
        String ruleVersion = json(ok(get("/api/v1/plans/" + planId)))
                .path("data")
                .path("ruleVersion")
                .asString();

        perform(post("/api/v1/plans/" + planId + "/steps/BENCH_ONBOARDING/complete")
                        .content("{\"ruleVersion\":\"%s\"}".formatted(ruleVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStage").value("FIRST"));

        // ── 1루: 진단 입력 STEP 을 서버가 알려주는 nextStep 순서대로만 저장
        int revision = saveDiagnosisInputs(planId);

        perform(post("/api/v1/plans/" + planId + "/first-base/complete").content(firstBaseBody(revision, ruleVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.progress.currentStage").value("SECOND"));

        // ── 2루: 매물 등록 → STEP 3·4 → 은행 상담 → 확정 → 제출
        long propertyId = registerProperty(planId);
        int workflowRevision = diagnoseProperty(planId, propertyId);
        long consultationId = addConsultation(planId, propertyId);
        int decisionRevision = decide(planId, propertyId, consultationId);

        perform(post("/api/v1/plans/" + planId + "/second-base/complete")
                        .content("{\"expectedDecisionRevision\":%d,\"ruleVersion\":\"%s\"}"
                                .formatted(decisionRevision, ruleVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progress.currentStage").value("THIRD"));

        // ── 3루: 계약 초안 → 잔금일 → 등기부 2회 대조 → 실행 사실 → 완료
        perform(post("/api/v1/plans/" + planId + "/contract/prefill")).andExpect(status().isOk());

        LocalDate balanceDate = LocalDate.now().minusDays(1);
        perform(patch("/api/v1/plans/" + planId + "/contract/balance-date")
                        .content("{\"balanceDate\":\"%s\"}".formatted(balanceDate)))
                .andExpect(status().isOk());

        perform(post("/api/v1/plans/" + planId + "/contract/registry-snapshots")
                        .content(
                                registryBody("CONTRACT_SIGNING", LocalDate.now().minusDays(30))))
                .andExpect(status().isOk());
        perform(post("/api/v1/plans/" + planId + "/contract/registry-snapshots")
                        .content(registryBody("SETTLEMENT_DAY", balanceDate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SAFE"));

        perform(patch("/api/v1/plans/" + planId + "/contract/execution-facts")
                        .content("{\"balancePaidAt\":\"%s\",\"moveInReportAt\":\"%s\"}"
                                .formatted(balanceDate, balanceDate)))
                .andExpect(status().isOk());

        perform(post("/api/v1/plans/" + planId + "/contract/complete")
                        .content("{\"ruleVersion\":\"%s\"}".formatted(ruleVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progress.currentStage").value("HOME"));

        // ── 홈 도착
        ok(get("/api/v1/plans/" + planId)).andExpect(jsonPath("$.data.stage").value("HOME"));
        ok(get("/api/v1/plans/" + planId + "/progress"))
                .andExpect(jsonPath("$.data.currentStage").value("HOME"))
                .andExpect(jsonPath("$.data.planStatus").value("ACTIVE"));

        // 워크플로 revision 은 상담 등록까지 반영돼 있어야 한다(뒤에서 되돌아왔을 때의 기준값).
        ok(get("/api/v1/plans/" + planId + "/properties/" + propertyId + "/resume"))
                .andExpect(jsonPath("$.data.revision").value(workflowRevision + 1))
                .andExpect(jsonPath("$.data.status").value("CONSULTED"));
    }

    @Test
    @DisplayName("3루는 등기부 대조와 실행 사실이 둘 다 있어야 홈으로 넘어간다")
    void third_base_requires_both_registry_comparison_and_execution_facts() throws Exception {
        Long planId = createPlan();
        String ruleVersion = json(ok(get("/api/v1/plans/" + planId)))
                .path("data")
                .path("ruleVersion")
                .asString();
        ok(post("/api/v1/plans/" + planId + "/steps/BENCH_ONBOARDING/complete")
                .content("{\"ruleVersion\":\"%s\"}".formatted(ruleVersion)));
        ok(post("/api/v1/plans/" + planId + "/first-base/complete")
                .content(firstBaseBody(saveDiagnosisInputs(planId), ruleVersion)));
        long propertyId = registerProperty(planId);
        diagnoseProperty(planId, propertyId);
        long consultationId = addConsultation(planId, propertyId);
        ok(post("/api/v1/plans/" + planId + "/second-base/complete")
                .content("{\"expectedDecisionRevision\":%d,\"ruleVersion\":\"%s\"}"
                        .formatted(decide(planId, propertyId, consultationId), ruleVersion)));

        // 계약 초안조차 없으면 계약 자체를 못 찾는다.
        perform(post("/api/v1/plans/" + planId + "/contract/complete")
                        .content("{\"ruleVersion\":\"%s\"}".formatted(ruleVersion)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRP_001"));

        ok(post("/api/v1/plans/" + planId + "/contract/prefill"));
        perform(post("/api/v1/plans/" + planId + "/contract/complete")
                        .content("{\"ruleVersion\":\"%s\"}".formatted(ruleVersion)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRP_017"));

        LocalDate today = LocalDate.now();
        ok(patch("/api/v1/plans/" + planId + "/contract/execution-facts")
                .content("{\"balancePaidAt\":\"%s\",\"moveInReportAt\":\"%s\"}".formatted(today, today)));
        perform(post("/api/v1/plans/" + planId + "/contract/complete")
                        .content("{\"ruleVersion\":\"%s\"}".formatted(ruleVersion)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRP_018"));

        // 계약 시점만 기록하면 아직 NEED_INFO 다 — 잔금일 등기부까지 있어야 대조가 성립한다.
        ok(post("/api/v1/plans/" + planId + "/contract/registry-snapshots")
                        .content(registryBody("CONTRACT_SIGNING", today.minusDays(30))))
                .andExpect(jsonPath("$.data.status").value("NEED_INFO"));
        perform(post("/api/v1/plans/" + planId + "/contract/complete")
                        .content("{\"ruleVersion\":\"%s\"}".formatted(ruleVersion)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRP_018"));

        // 잔금일 등기부에 모르는 항목이 남으면 SAFE 가 아니라 NEED_INFO 다.
        ok(post("/api/v1/plans/" + planId + "/contract/registry-snapshots").content("""
                                {"stage":"SETTLEMENT_DAY","ownerMatchesContractParty":true,"seniorDebt":0,
                                 "mortgageCount":0,"issuedAt":"%s"}
                                """.formatted(today)))
                .andExpect(jsonPath("$.data.status").value("NEED_INFO"));
        perform(post("/api/v1/plans/" + planId + "/contract/complete")
                        .content("{\"ruleVersion\":\"%s\"}".formatted(ruleVersion)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRP_018"));

        ok(post("/api/v1/plans/" + planId + "/contract/registry-snapshots")
                        .content(registryBody("SETTLEMENT_DAY", today)))
                .andExpect(jsonPath("$.data.status").value("SAFE"));
        ok(post("/api/v1/plans/" + planId + "/contract/complete")
                        .content("{\"ruleVersion\":\"%s\"}".formatted(ruleVersion)))
                .andExpect(jsonPath("$.data.progress.currentStage").value("HOME"));
    }

    private Long createPlan() throws Exception {
        MvcResult result = perform(post("/api/v1/plans")
                        .content("{\"leaseType\":\"JEONSE\",\"targetMoveDate\":\"%s\"}"
                                .formatted(LocalDate.now().plusMonths(6))))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).path("data").path("id").asLong();
    }

    /** 서버가 돌려주는 nextStep 만 따라간다. 프리랜서 분기라 회사 STEP 은 서버가 건너뛴다. */
    private int saveDiagnosisInputs(Long planId) throws Exception {
        Long regionId = regions.findAll().get(0).getId();
        int revision = 0;
        revision = saveStep(planId, "HOUSEHOLDER", revision, "\"householderStatus\":\"EXPECTED\"", "HOMELESS");
        revision = saveStep(planId, "HOMELESS", revision, "\"isHomeless\":true", "MARITAL_STATUS");
        revision = saveStep(planId, "MARITAL_STATUS", revision, "\"maritalStatus\":\"SINGLE\"", "EMPLOYMENT_TYPE");
        revision = saveStep(planId, "EMPLOYMENT_TYPE", revision, "\"employmentType\":\"FREELANCER\"", "FINANCIAL");
        revision = saveStep(
                planId,
                "FINANCIAL",
                revision,
                "\"monthlyIncome\":3000000,\"netAssets\":30000000,\"availableCash\":20000000,"
                        + "\"existingJeonseLoan\":false,\"incomeSource\":\"MANUAL\",\"assetSource\":\"MANUAL\","
                        + "\"financialDataConfirmed\":true",
                "HOPE_DEPOSIT");
        revision = saveStep(planId, "HOPE_DEPOSIT", revision, "\"hopeDeposit\":" + HOPE_DEPOSIT, "REGION");
        revision = saveStep(planId, "REGION", revision, "\"regionId\":" + regionId, "REVIEW");

        MvcResult review = perform(put("/api/v1/plans/" + planId + "/input/steps/REVIEW")
                        .content("{\"expectedRevision\":%d}".formatted(revision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextStep").isEmpty())
                .andExpect(jsonPath("$.data.progressPercent").value(100))
                .andReturn();
        return json(review).path("data").path("revision").asInt();
    }

    private int saveStep(Long planId, String step, int expectedRevision, String fields, String expectedNext)
            throws Exception {
        MvcResult result = perform(put("/api/v1/plans/" + planId + "/input/steps/" + step)
                        .content("{\"expectedRevision\":%d,%s}".formatted(expectedRevision, fields)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextStep").value(expectedNext))
                .andReturn();
        return json(result).path("data").path("revision").asInt();
    }

    private long registerProperty(Long planId) throws Exception {
        MvcResult result = perform(
                        post("/api/v1/plans/" + planId + "/properties/analysis").content("""
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
                                  "deposit": %d,
                                  "marketPrice": 200000000,
                                  "detailAddress": "101동 1004호",
                                  "exclusiveArea": 59.92
                                }
                                """.formatted(DEPOSIT)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).path("data").path("propertyId").asLong();
    }

    /** STEP 3(위반건축물)·STEP 4(등기부)를 사람이 확인한 값으로 저장해 GREEN 으로 만든다. */
    private int diagnoseProperty(Long planId, long propertyId) throws Exception {
        String base = "/api/v1/plans/" + planId + "/properties/" + propertyId;
        int revision =
                json(ok(get(base + "/resume"))).path("data").path("revision").asInt();

        MvcResult violation = perform(put(base + "/steps/violation")
                        .content("{\"expectedRevision\":%d,\"violationBuilding\":false}".formatted(revision)))
                .andExpect(status().isOk())
                .andReturn();
        revision =
                json(violation).path("data").path("workflow").path("revision").asInt();

        MvcResult registry = perform(put(base + "/steps/registry").content("""
                                {
                                  "expectedRevision": %d,
                                  "officialPrice": 150000000,
                                  "officialPriceYear": 2026,
                                  "officialPriceSource": "REALTY_PRICE_APARTMENT",
                                  "seniorDebt": 0,
                                  "ownerMatches": true,
                                  "trustRegistered": false,
                                  "leaseholdRegistered": false,
                                  "seizureOrDispositionRestricted": false,
                                  "auctionInProgress": false,
                                  "landlordTaxUnpaid": false
                                }
                                """.formatted(revision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workflow.status").value("READY_FOR_CONSULTATION"))
                .andExpect(jsonPath("$.data.verification.trafficLight").value("GREEN"))
                .andReturn();
        return json(registry).path("data").path("workflow").path("revision").asInt();
    }

    private long addConsultation(Long planId, long propertyId) throws Exception {
        MvcResult result = perform(post("/api/v1/plans/" + planId + "/properties/" + propertyId + "/consultations")
                        .content("""
                                        {
                                          "bankName": "국민은행",
                                          "branchName": "역삼점",
                                          "resultStatus": "POSSIBLE",
                                          "loanProduct": "BANK_LOAN",
                                          "collateralMethod": "HF",
                                          "approvedLimit": 80000000,
                                          "quotedRate": 3.200,
                                          "consultedAt": "%s"
                                        }
                                        """.formatted(LocalDate.now())))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).path("data").path("consultationId").asLong();
    }

    private int decide(Long planId, long propertyId, long consultationId) throws Exception {
        MvcResult result = perform(put("/api/v1/plans/" + planId + "/properties/decision")
                        .content("{\"propertyId\":%d,\"consultationId\":%d}".formatted(propertyId, consultationId)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).path("data").path("decisionRevision").asInt();
    }

    /** 계약 당시와 잔금일의 등기부가 같다 — 불리해진 권리가 없으므로 대조는 SAFE 여야 한다. */
    private String registryBody(String stage, LocalDate issuedAt) {
        return """
                {
                  "stage": "%s",
                  "ownerMatchesContractParty": true,
                  "seniorDebt": 0,
                  "mortgageCount": 0,
                  "leaseholdRegistered": false,
                  "seizureOrDispositionRestricted": false,
                  "auctionInProgress": false,
                  "trustRegistered": false,
                  "issuedAt": "%s"
                }
                """.formatted(stage, issuedAt);
    }

    private String firstBaseBody(int revision, String ruleVersion) {
        return """
                {
                  "expectedRevision": %d,
                  "ruleVersion": "%s",
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
                """.formatted(revision, ruleVersion);
    }

    private ResultActions perform(MockHttpServletRequestBuilder builder) throws Exception {
        return mvc.perform(builder.header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON));
    }

    private ResultActions ok(MockHttpServletRequestBuilder builder) throws Exception {
        return perform(builder).andExpect(status().isOk());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return json(actions.andReturn());
    }
}
