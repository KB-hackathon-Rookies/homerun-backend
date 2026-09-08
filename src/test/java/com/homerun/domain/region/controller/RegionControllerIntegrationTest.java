package com.homerun.domain.region.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.auth.type.AuthProvider;
import com.homerun.domain.member.entity.Member;
import com.homerun.domain.member.repository.MemberRepository;
import com.homerun.domain.terms.service.TermsService;
import com.homerun.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "app.jwt.secret=regional-controller-integration-test-secret-over-32-bytes",
            "app.jwt.access-token-expiration-seconds=3600"
        })
@Transactional
class RegionControllerIntegrationTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    MemberRepository members;

    @Autowired
    JwtTokenProvider tokens;

    @MockitoBean
    TermsService terms;

    @Test
    void should_returnStoredRegionIds_when_authenticated() throws Exception {
        when(terms.hasAgreedAllRequired(anyLong())).thenReturn(true);
        String token = tokens.createAccessToken(
                members.save(Member.create(AuthProvider.KAKAO, "region-catalog-test", null, "tester")));
        mvc.perform(get("/api/v1/regions/jeonse-options").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].id").isNumber())
                .andExpect(jsonPath("$.data[0].code").value("JEONSE_GYEONGGI"))
                .andExpect(jsonPath("$.data[0].policyArea").value("CAPITAL"))
                .andExpect(jsonPath("$.data[2].policyArea").value("NON_CAPITAL"))
                .andExpect(jsonPath("$.data[3].policyArea").value("SEOUL"));
    }

    /**
     * 회원가입은 로그인 전 단계다. 지역 목록이 401 이면 거주지를 고를 수 없어 가입이 끝나지 않는다
     * (b10df51 에서 permitAll 로 공개).
     */
    @Test
    void should_allowWithoutAuthentication() throws Exception {
        mvc.perform(get("/api/v1/regions/jeonse-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4));
    }
}
