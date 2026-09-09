package com.homerun.domain.plan.controller;

import static com.homerun.domain.plan.controller.OpenBankingPlanSyncPaths.MOCK;
import static com.homerun.domain.plan.controller.OpenBankingPlanSyncPaths.REAL_SYNC;
import static com.homerun.domain.plan.controller.OpenBankingPlanSyncPaths.mappedPatterns;
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
import com.homerun.domain.openbanking.type.Persona;
import com.homerun.domain.terms.service.TermsService;
import com.homerun.global.security.jwt.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 데모(플래그 ON)에서는 mock 경로가 <b>분리 전과 같은 주소로</b> 그대로 동작해야 한다.
 *
 * <p>컨트롤러를 떼어내면서 URL 이 바뀌면 데모와 프론트가 그 자리에서 깨진다. 그래서 경로를
 * 문자열 그대로 박아 두고 확인한다. 짝이 되는 {@link MockOpenBankingPlanSyncDisabledEndpointTest}
 * 가 플래그 OFF 에서 같은 주소가 사라짐을 고정한다.
 */
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "app.jwt.secret=mock-open-banking-sync-enabled-test-secret-over-32-bytes",
            "app.jwt.access-token-expiration-seconds=3600",
            "external-api.open-banking.mock-data=true"
        })
@Transactional
class MockOpenBankingPlanSyncEnabledEndpointTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    MemberRepository members;

    @Autowired
    JwtTokenProvider tokens;

    @Autowired
    EntityManager em;

    // actuator 도 같은 타입의 빈을 올린다. MVC 컨트롤러 매핑을 이름으로 집어 온다.
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @MockitoBean
    TermsService terms;

    private Long planId;
    private String bearer;

    @BeforeEach
    void setUp() {
        when(terms.hasAgreedAllRequired(anyLong())).thenReturn(true);
        Member member = members.save(Member.create(AuthProvider.KAKAO, "mock-sync-enabled-test", null, "tester"));
        bearer = "Bearer " + tokens.createAccessToken(member);
        planId = ((Number)
                        em.createNativeQuery("INSERT INTO plan(user_id,lease_type) VALUES (:uid,'JEONSE') RETURNING id")
                                .setParameter("uid", member.getId())
                                .getSingleResult())
                .longValue();
    }

    @Test
    @DisplayName("플래그가 켜진 데모에서는 mock 경로가 예전 주소 그대로 페르소나를 적재한다")
    void should_loadPersona_whenMockDataFlagIsOn() throws Exception {
        mvc.perform(post("/api/v1/plans/" + planId + "/input/open-banking-sync/mock")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"persona\":\"KIM_KUKMIN\"}"))
                .andExpect(status().isOk())
                // 값을 여기 적어 두면 페르소나를 고칠 때마다 이 테스트가 따로 틀린다. 출처를 하나로 둔다.
                .andExpect(jsonPath("$.data.monthlyIncome").value(Persona.KIM_KUKMIN.getMonthlyIncome()))
                .andExpect(jsonPath("$.data.financialAsset").value(Persona.KIM_KUKMIN.getFinancialAsset()));
    }

    @Test
    @DisplayName("플래그가 켜지면 진짜 동기화와 데모 경로가 함께 매핑된다")
    void should_mapBothEndpoints_whenMockDataFlagIsOn() {
        assertThat(mappedPatterns(mappings)).contains(REAL_SYNC).contains(MOCK);
    }
}
