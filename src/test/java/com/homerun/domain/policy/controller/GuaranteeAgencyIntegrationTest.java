package com.homerun.domain.policy.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.auth.type.AuthProvider;
import com.homerun.domain.member.entity.Member;
import com.homerun.domain.member.repository.MemberRepository;
import com.homerun.domain.terms.service.TermsService;
import com.homerun.global.security.jwt.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * V22(#85)가 심은 실제 guarantee_agency 3행을 대상으로 한다. 응답값을 컨트롤러가 만든 값끼리
 * 비교하지 않고 DB에서 직접 읽어 비교한다 — 그래야 매핑이 실제로 맞는지 검증이 된다.
 *
 * <p>{@code anyRequest().authenticated()}(SecurityConfig)라 이 참조 데이터 조회도 로그인이
 * 필요하다 — documents 카탈로그도 마찬가지라 이 저장소의 실제 정책이고, 이 테스트가 그걸
 * 검증하는 유일한 곳이다(DocumentControllerTest는 Security 필터 없이 도는 단위테스트라
 * 이 401을 못 잡는다).
 */
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "app.jwt.secret=guarantee-agency-integration-test-secret-over-32-bytes",
            "app.jwt.access-token-expiration-seconds=3600"
        })
class GuaranteeAgencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager em;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TermsService termsService;

    @BeforeEach
    void allowRequiredTerms() {
        // 이 테스트의 관심사가 아니다 — SecurityIntegrationTest 와 같은 패턴으로 통과시킨다.
        Mockito.when(termsService.hasAgreedAllRequired(ArgumentMatchers.anyLong()))
                .thenReturn(true);
    }

    @Test
    void should_returnThreeAgenciesOrderedByCode_whenListedAgainstRealPostgres() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken(
                memberRepository.save(Member.create(AuthProvider.KAKAO, "guarantee-agency-test", null, "tester")));
        BigDecimalRow hf = feeRateOf("HF");
        BigDecimalRow hug = feeRateOf("HUG");
        BigDecimalRow sgi = feeRateOf("SGI");

        mockMvc.perform(get("/api/v1/guarantee-agencies").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].code").value("HF"))
                .andExpect(jsonPath("$.data[0].feeRateMin").value(hf.min()))
                .andExpect(jsonPath("$.data[0].feeRateMax").value(hf.max()))
                .andExpect(jsonPath("$.data[0].note").value("자사(HF) 전세대출 이용자만 가입 가능. 대출 상환보증이지 반환보증은 아니다"))
                .andExpect(jsonPath("$.data[1].code").value("HUG"))
                .andExpect(jsonPath("$.data[1].feeRateMin").value(hug.min()))
                .andExpect(jsonPath("$.data[1].feeRateMax").value(hug.max()))
                .andExpect(jsonPath("$.data[1].note").doesNotExist())
                .andExpect(jsonPath("$.data[2].code").value("SGI"))
                .andExpect(jsonPath("$.data[2].feeRateMin").value(sgi.min()))
                .andExpect(jsonPath("$.data[2].feeRateMax").value(sgi.max()));
    }

    @Test
    void should_returnUnauthorized_when_noAccessToken() throws Exception {
        mockMvc.perform(get("/api/v1/guarantee-agencies")).andExpect(status().isUnauthorized());
    }

    private BigDecimalRow feeRateOf(String code) {
        Object[] row = (Object[])
                em.createNativeQuery("SELECT fee_rate_min, fee_rate_max FROM guarantee_agency WHERE code = :code")
                        .setParameter("code", code)
                        .getSingleResult();
        return new BigDecimalRow(((Number) row[0]).doubleValue(), ((Number) row[1]).doubleValue());
    }

    private record BigDecimalRow(double min, double max) {}
}
