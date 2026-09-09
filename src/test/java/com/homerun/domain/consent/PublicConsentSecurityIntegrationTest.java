package com.homerun.domain.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.auth.type.AuthProvider;
import com.homerun.domain.consent.dto.ConsentDtos.IssueRequest;
import com.homerun.domain.consent.entity.HouseholdMember;
import com.homerun.domain.consent.repository.HouseholdMemberRepository;
import com.homerun.domain.consent.service.ConsentService;
import com.homerun.domain.member.entity.Member;
import com.homerun.domain.member.repository.MemberRepository;
import com.homerun.domain.terms.service.TermsService;
import com.homerun.global.security.jwt.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가구원 동의 링크가 <b>실제 보안 필터를 통과해</b> 열리는지 본다.
 *
 * <p>기존 {@code ConsentControllerTest} 는 컨트롤러만 세워 도는 슬라이스라 필터를 한 번도
 * 타지 않는다. 그래서 링크가 401 로 막혀 있는데도 초록불이었다. 여기서는 전체 컨텍스트를
 * 띄우고 {@code Authorization} 헤더 없이 부른다 — 가구원은 회원이 아니라 액세스 토큰을
 * 얻을 방법 자체가 없기 때문이다.
 *
 * <p>여는 만큼 닫혀 있어야 할 것도 함께 고정한다. 현황 조회·링크 발급은
 * {@code /api/v1/plans/{planId}/consents} 라 계획 소유자만 부를 수 있어야 한다.
 */
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "app.jwt.secret=public-consent-security-integration-test-secret-over-32-bytes",
            "app.jwt.access-token-expiration-seconds=3600"
        })
@Transactional
class PublicConsentSecurityIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    MemberRepository members;

    @Autowired
    HouseholdMemberRepository householdMembers;

    @Autowired
    ConsentService consents;

    @Autowired
    JwtTokenProvider tokens;

    @Autowired
    EntityManager em;

    @MockitoBean
    TermsService terms;

    private Long planId;
    private String consentToken;
    private String bearer;

    @BeforeEach
    void setUp() {
        when(terms.hasAgreedAllRequired(anyLong())).thenReturn(true);
        Member owner = members.save(Member.create(AuthProvider.KAKAO, "public-consent-security-test", null, "tester"));
        bearer = "Bearer " + tokens.createAccessToken(owner);
        planId = ((Number)
                        em.createNativeQuery("INSERT INTO plan(user_id,lease_type) VALUES (:uid,'JEONSE') RETURNING id")
                                .setParameter("uid", owner.getId())
                                .getSingleResult())
                .longValue();
        HouseholdMember household = householdMembers.save(new HouseholdMember(planId, "배우자", true));
        consentToken = consents.issue(owner.getId(), planId, new IssueRequest(household.id(), null, "전세자금대출 자격 확인"))
                .token();
    }

    @Test
    @DisplayName("가구원은 토큰 없이 동의 화면을 연다")
    void should_permitConsentView_withoutAccessToken() throws Exception {
        mvc.perform(get("/api/v1/consents/" + consentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.relation").value("배우자"))
                .andExpect(jsonPath("$.data.purpose").value("전세자금대출 자격 확인"));
    }

    @Test
    @DisplayName("가구원은 토큰 없이 동의 응답을 제출한다")
    void should_permitConsentResponse_withoutAccessToken() throws Exception {
        mvc.perform(post("/api/v1/consents/" + consentToken + "/response")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 실제로 확인 처리까지 갔는지 본다. 필터만 지나고 아무 일도 안 했으면 의미가 없다.
        assertThat(householdMembers.findByPlanIdOrderById(planId))
                .singleElement()
                .matches(HouseholdMember::verified);
    }

    @Test
    @DisplayName("필수 약관에 동의하지 않은 회원이 링크를 열어도 약관 필터가 막지 않는다")
    void should_notBlockConsentView_whenRequiredTermsAreNotAgreed() throws Exception {
        // 가구원 동의는 우리 약관과 무관한 화면이다. TERMS_005 로 막히면 링크가 죽는다.
        when(terms.hasAgreedAllRequired(anyLong())).thenReturn(false);

        mvc.perform(get("/api/v1/consents/" + consentToken).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.relation").value("배우자"));
    }

    @Test
    @DisplayName("존재하지 않는 동의 토큰은 인증이 아니라 동의 토큰 오류로 답한다")
    void should_answerWithConsentTokenError_forUnknownToken() throws Exception {
        // 둘 다 401 이라 상태 코드로는 구분되지 않는다. AUTH_002 면 필터에 막힌 것이고
        // TERMS_001 이면 컨트롤러까지 닿았다는 뜻이다.
        mvc.perform(get("/api/v1/consents/unknown-token"))
                .andExpect(jsonPath("$.code").value("TERMS_001"));
    }

    @Test
    @DisplayName("계획 하위 동의 API 는 그대로 인증을 요구한다")
    void should_stillRequireAuthentication_forPlanScopedConsentApis() throws Exception {
        mvc.perform(get("/api/v1/plans/" + planId + "/consents"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_002"));

        mvc.perform(post("/api/v1/plans/" + planId + "/consents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":1,\"purpose\":\"전세자금대출 자격 확인\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
    }
}
