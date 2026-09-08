package com.homerun.domain.property.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.auth.type.AuthProvider;
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
import com.homerun.domain.policy.type.PolicyVerdictResult;
import com.homerun.domain.property.entity.Property;
import com.homerun.domain.property.repository.PropertyRepository;
import com.homerun.domain.property.service.PropertyTrafficLightResolver;
import com.homerun.domain.property.type.DataSource;
import com.homerun.domain.property.type.TrafficLight;
import com.homerun.domain.terms.service.TermsService;
import com.homerun.global.security.jwt.JwtTokenProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 2루 매물 진단 STEP 1 에서 자동조회 판정이 실제로 내려오는지 본다.
 *
 * <p>매물 등록 직후 신호등은 YELLOW 다 — 공개 건축물대장 표제부 API 가 위반건축물 여부를 주지
 * 않아 STEP 3 에서 사람이 확인하기 전까지 null 이기 때문이다. 이 시점에 판정 API 가 막히면
 * 디자인이 요구하는 "진행중 n개 · 불가 n개" 요약을 만들 수 없다. 판정 응답에는 한도·금리가
 * 없으므로 대출 상품 노출 제한(FR-P4-02)의 대상도 아니다.
 */
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "app.jwt.secret=property-policy-verdict-api-test-secret-over-32-bytes",
            "app.jwt.access-token-expiration-seconds=3600"
        })
class PropertyPolicyVerdictApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    MemberRepository members;

    @Autowired
    PlanRepository plans;

    @Autowired
    PlanInputRepository inputs;

    @Autowired
    PropertyRepository properties;

    @Autowired
    PropertyTrafficLightResolver trafficLights;

    @Autowired
    JwtTokenProvider tokens;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    TermsService terms;

    private Long planId;
    private Long propertyId;
    private String bearer;

    @BeforeEach
    void setUp() {
        when(terms.hasAgreedAllRequired(anyLong())).thenReturn(true);
        Member member = members.save(
                Member.create(AuthProvider.KAKAO, "policy-verdict-api-" + System.nanoTime(), null, "tester"));
        bearer = "Bearer " + tokens.createAccessToken(member);
        planId = plans.save(Plan.create(member.getId(), LeaseType.JEONSE, null)).getId();
        inputs.save(PlanInput.create(planId, planInput()));
        propertyId = candidate().getId();
    }

    @Test
    void evaluates_policies_for_yellowProperty_beforeViolationIsConfirmed() throws Exception {
        assertThat(trafficLights.forProperty(planId, propertyId)).isEqualTo(TrafficLight.YELLOW);

        MvcResult result = mvc.perform(
                        post("/api/v1/plans/{planId}/properties/{propertyId}/policy-verdicts", planId, propertyId)
                                .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.propertyId").value(propertyId))
                .andExpect(jsonPath("$.data.results.length()").value(3))
                .andExpect(jsonPath("$.data.evaluatedAt").isNotEmpty())
                .andReturn();

        JsonNode results = objectMapper
                .readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("results");
        List<String> statuses = new ArrayList<>();
        results.forEach(node -> statuses.add(node.path("status").asString()));

        // 디자인 `2루 2 · 매물 진단` 의 "진행중 3개 · 불가 1개" — 이 단계에서는 진행중(NEED_INFO)과
        // 불가(FAIL)만 나온다. 위반건축물을 아직 확인하지 않았으므로 PASS 로 단정하지 않는다.
        assertThat(statuses)
                .isNotEmpty()
                .allMatch(status -> PolicyVerdictResult.NEED_INFO.name().equals(status)
                        || PolicyVerdictResult.FAIL.name().equals(status))
                .contains(PolicyVerdictResult.NEED_INFO.name());
    }

    /** 등록 직후 상태 그대로. 위반건축물은 자동조회로 알 수 없어 null 이고 등기부는 아직 안 봤다. */
    private Property candidate() {
        Property property = Property.candidate(
                planId,
                "1168010100",
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
        property.recordSourcedFacts("123-4", "401호", new BigDecimal("42.35"), DataSource.AUTO, DataSource.AUTO, true);
        property.recordAutomaticSafety(false, false);
        property.startWorkflow(true, false);
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
