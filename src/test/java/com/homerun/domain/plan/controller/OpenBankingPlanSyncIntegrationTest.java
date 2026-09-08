package com.homerun.domain.plan.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.auth.type.AuthProvider;
import com.homerun.domain.member.entity.Member;
import com.homerun.domain.member.repository.MemberRepository;
import com.homerun.domain.openbanking.dto.response.ExternalDataCoverage;
import com.homerun.domain.openbanking.dto.response.FinancialSummaryStatus;
import com.homerun.domain.openbanking.dto.response.OpenBankingFinancialSummaryResponse;
import com.homerun.domain.openbanking.service.FinancialSnapshotService;
import com.homerun.domain.openbanking.service.OpenBankingService;
import com.homerun.domain.terms.service.TermsService;
import com.homerun.global.security.jwt.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * 오픈뱅킹 소득을 최초 진단 금융 STEP에 반영하는 흐름을 HTTP 계약으로 고정한다(F03).
 *
 * <p>핵심은 두 가지다. 프론트가 서버 동기화 없이 {@code incomeSource=OPEN_BANKING} 을 보내면
 * 소득 출처 불일치(PLAN_017)로 막히고, 이는 revision 충돌(PLAN_018)과 다른 코드라는 점.
 * 그리고 계좌 잔액을 순자산으로 자동 저장하지 않으므로 {@code assetSource=OPEN_BANKING} 도
 * 서버 동기화가 없는 한 PLAN_017 로 막힌다는 점이다. 올바른 흐름은 동기화 → 소득 확인 →
 * STEP 저장 순서로만 성립한다.
 */
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "app.jwt.secret=open-banking-plan-sync-integration-test-secret-over-32-bytes",
            "app.jwt.access-token-expiration-seconds=3600"
        })
@Transactional
class OpenBankingPlanSyncIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    MemberRepository members;

    @Autowired
    JwtTokenProvider tokens;

    @Autowired
    EntityManager em;

    @MockitoBean
    TermsService terms;

    @MockitoBean
    OpenBankingService openBanking;

    @MockitoBean
    FinancialSnapshotService snapshots;

    private Long planId;
    private String bearer;

    @BeforeEach
    void setUp() {
        when(terms.hasAgreedAllRequired(anyLong())).thenReturn(true);
        Member member = members.save(Member.create(AuthProvider.KAKAO, "open-banking-sync-test", null, "tester"));
        bearer = "Bearer " + tokens.createAccessToken(member);
        planId = ((Number)
                        em.createNativeQuery("INSERT INTO plan(user_id,lease_type) VALUES (:uid,'JEONSE') RETURNING id")
                                .setParameter("uid", member.getId())
                                .getSingleResult())
                .longValue();
    }

    @Test
    @DisplayName("서버 동기화 없이 오픈뱅킹 소득을 최초 금융 STEP으로 보내면 소득 출처 불일치로 막힌다")
    void rejects_open_banking_income_on_financial_step_without_prior_sync() throws Exception {
        saveFinancialStep("{\"expectedRevision\":0,\"monthlyIncome\":2900000,\"netAssets\":36000000,"
                        + "\"incomeSource\":\"OPEN_BANKING\",\"assetSource\":\"MANUAL\","
                        + "\"financialDataConfirmed\":true,\"unknownFields\":[\"AVAILABLE_CASH\"]}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_017"));
    }

    @Test
    @DisplayName("계좌 잔액을 순자산으로 자동 저장하지 않으므로 오픈뱅킹 자산 출처도 동기화 없이는 막힌다")
    void rejects_open_banking_asset_source_on_financial_step_without_prior_sync() throws Exception {
        saveFinancialStep("{\"expectedRevision\":0,\"monthlyIncome\":2900000,\"netAssets\":36000000,"
                        + "\"incomeSource\":\"MANUAL\",\"assetSource\":\"OPEN_BANKING\","
                        + "\"financialDataConfirmed\":true,\"unknownFields\":[\"AVAILABLE_CASH\"]}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_017"));
    }

    @Test
    @DisplayName("동기화 → 소득 확인 → STEP 저장 순서면 오픈뱅킹 소득과 수동 순자산이 함께 저장된다")
    void accepts_open_banking_income_after_server_sync_and_confirm() throws Exception {
        when(openBanking.financialSummary(anyLong(), any())).thenReturn(summaryWithVerifiedIncome());

        mvc.perform(post(inputUrl() + "/open-banking-sync").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.suggestedMonthlyIncome").value(2900000))
                .andExpect(jsonPath("$.data.monthlyIncomeSyncStatus").value("APPLIED"))
                .andExpect(jsonPath("$.data.suggestedNetAssets").doesNotExist())
                .andExpect(jsonPath("$.data.input.incomeSource").value("OPEN_BANKING"))
                .andExpect(jsonPath("$.data.input.financialDataConfirmed").value(false))
                .andExpect(jsonPath("$.data.input.revision").value(1));

        mvc.perform(put(inputUrl() + "/financial-income")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"CONFIRM_OPEN_BANKING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.financialDataConfirmed").value(true))
                .andExpect(jsonPath("$.data.revision").value(2));

        saveFinancialStep("{\"expectedRevision\":2,\"monthlyIncome\":2900000,\"netAssets\":36000000,"
                        + "\"incomeSource\":\"OPEN_BANKING\",\"assetSource\":\"MANUAL\","
                        + "\"financialDataConfirmed\":true,\"unknownFields\":[\"AVAILABLE_CASH\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextStep").value("HOPE_DEPOSIT"))
                .andExpect(jsonPath("$.data.input.incomeSource").value("OPEN_BANKING"))
                .andExpect(jsonPath("$.data.input.assetSource").value("MANUAL"))
                .andExpect(jsonPath("$.data.input.monthlyIncome").value(2900000))
                .andExpect(jsonPath("$.data.input.netAssets").value(36000000));
    }

    @Test
    @DisplayName("소득 출처 불일치(PLAN_017)와 revision 충돌(PLAN_018)은 서로 다른 코드로 구분된다")
    void distinguishes_source_mismatch_from_revision_conflict() throws Exception {
        saveFinancialStep("{\"expectedRevision\":9,\"monthlyIncome\":2900000,\"netAssets\":36000000,"
                        + "\"incomeSource\":\"MANUAL\",\"assetSource\":\"MANUAL\","
                        + "\"financialDataConfirmed\":true,\"unknownFields\":[\"AVAILABLE_CASH\"]}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_018"));
    }

    private org.springframework.test.web.servlet.ResultActions saveFinancialStep(String body) throws Exception {
        return mvc.perform(put(inputUrl() + "/steps/FINANCIAL")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String inputUrl() {
        return "/api/v1/plans/" + planId + "/input";
    }

    private OpenBankingFinancialSummaryResponse summaryWithVerifiedIncome() {
        return new OpenBankingFinancialSummaryResponse(
                FinancialSummaryStatus.COMPLETE,
                new ExternalDataCoverage(2, 2),
                new ExternalDataCoverage(2, 2),
                new ExternalDataCoverage(2, 2),
                2,
                new BigDecimal("15000000"),
                new BigDecimal("14000000"),
                new BigDecimal("2900000"),
                3,
                new BigDecimal("300000"),
                2,
                0,
                false,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 8, 31),
                3,
                List.of("004", "020"),
                List.of(),
                List.of(),
                Instant.parse("2026-09-05T00:00:00Z"));
    }
}
