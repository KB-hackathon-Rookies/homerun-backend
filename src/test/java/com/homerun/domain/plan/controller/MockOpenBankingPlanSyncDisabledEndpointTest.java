package com.homerun.domain.plan.controller;

import static com.homerun.domain.plan.controller.OpenBankingPlanSyncPaths.MOCK;
import static com.homerun.domain.plan.controller.OpenBankingPlanSyncPaths.REAL_SYNC;
import static com.homerun.domain.plan.controller.OpenBankingPlanSyncPaths.mappedPatterns;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.auth.type.AuthProvider;
import com.homerun.domain.member.entity.Member;
import com.homerun.domain.member.repository.MemberRepository;
import com.homerun.domain.openbanking.repository.FinancialSnapshotRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 플래그 기본값(false)에서는 데모용 mock 경로가 <b>존재하지 않는다.</b>
 *
 * <p>이 경로는 소득·순자산을 {@code financialDataConfirmed=true} 로 적재해 정책 판정에 그대로
 * 들어가게 만든다. 운영에서 열려 있으면 로그인한 누구나 판정 근거를 지어낼 수 있다.
 *
 * <p>인증 실패로 막히는 것으로는 부족하다. 토큰을 실어 보내 <b>매핑 자체가 없음</b>을 확인한다.
 * 진짜 동기화는 데모와 무관하게 항상 필요하므로 같은 컨텍스트에서 살아 있음을 함께 고정한다.
 */
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "app.jwt.secret=mock-open-banking-sync-disabled-test-secret-over-32-bytes",
            "app.jwt.access-token-expiration-seconds=3600"
        })
@Transactional
class MockOpenBankingPlanSyncDisabledEndpointTest {

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

    @Autowired
    FinancialSnapshotRepository snapshots;

    @MockitoBean
    TermsService terms;

    private Long memberId;
    private Long planId;
    private String bearer;

    @BeforeEach
    void setUp() {
        when(terms.hasAgreedAllRequired(anyLong())).thenReturn(true);
        Member member = members.save(Member.create(AuthProvider.KAKAO, "mock-sync-disabled-test", null, "tester"));
        memberId = member.getId();
        bearer = "Bearer " + tokens.createAccessToken(member);
        planId = ((Number)
                        em.createNativeQuery("INSERT INTO plan(user_id,lease_type) VALUES (:uid,'JEONSE') RETURNING id")
                                .setParameter("uid", member.getId())
                                .getSingleResult())
                .longValue();
    }

    @Test
    @DisplayName("플래그가 꺼진 기본값에서는 데모 경로가 매핑되지 않고 진짜 동기화만 남는다")
    void should_notMapMockEndpoint_whenMockDataFlagIsOff() {
        assertThat(mappedPatterns(mappings)).contains(REAL_SYNC).doesNotContain(MOCK);
    }

    @Test
    @DisplayName("인증된 회원이 데모 경로를 불러도 핸들러가 없어 페르소나가 적재되지 않는다")
    void should_notLoadPersona_whenMockDataFlagIsOff() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/plans/" + planId + "/input/open-banking-sync/mock")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"persona\":\"KIM_KUKMIN\"}"))
                .andReturn();

        // 인증은 통과했는데 붙을 컨트롤러가 없다. 매핑이 없으면 정적 리소스 핸들러로 흘러가므로
        // 핸들러가 null 이 아니라 "핸들러 메서드가 아님" 이 매핑이 없다는 뜻이다.
        assertThat(result.getHandler()).isNotInstanceOf(HandlerMethod.class);
        assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(400);
        // 결론은 이것이다 — 응답 코드가 무엇이든 페르소나가 적재되지 않는다.
        assertThat(snapshots.findFirstByUserIdOrderByCreatedAtDescIdDesc(memberId))
                .isEmpty();
    }
}
