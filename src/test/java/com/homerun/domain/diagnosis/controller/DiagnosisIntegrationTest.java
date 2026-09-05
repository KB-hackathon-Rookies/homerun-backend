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
import com.homerun.domain.terms.service.TermsService;
import com.homerun.global.security.jwt.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "app.jwt.secret=diagnosis-integration-test-secret-over-32-bytes",
            "app.jwt.access-token-expiration-seconds=3600"
        })
@Transactional
class DiagnosisIntegrationTest {

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

    private Long planId;
    private String bearer;

    @BeforeEach
    void setup() {
        when(terms.hasAgreedAllRequired(anyLong())).thenReturn(true);
        Member member = members.save(Member.create(AuthProvider.KAKAO, "diagnosis-test", null, "tester"));
        bearer = "Bearer " + tokens.createAccessToken(member);
        planId = ((Number) em.createNativeQuery("INSERT INTO plan(user_id,lease_type,target_move_date)"
                                + " VALUES (:uid,'JEONSE',current_date + interval '6 month') RETURNING id")
                        .setParameter("uid", member.getId())
                        .getSingleResult())
                .longValue();
        em.createNativeQuery("INSERT INTO plan_input(plan_id,hope_deposit,current_deposit,monthly_rent,maintenance_fee,"
                        + "monthly_income,available_cash,income_source,financial_data_confirmed)"
                        + " VALUES (:pid,100000000,5000000,0,100000,3000000,20000000,'MANUAL',true)")
                .setParameter("pid", planId)
                .executeUpdate();
    }

    @Test
    void should_saveAndReadDiagnosisWithoutRir() throws Exception {
        mvc.perform(post(endpoint())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.initialCost.totalRequired").value(104875000))
                .andExpect(jsonPath("$.data.policyComparison.requiredCashAfterPolicy")
                        .value(24875000))
                .andExpect(jsonPath("$.data.monthlyDisposable").value(1600000))
                .andExpect(jsonPath("$.data.shortfall").value(0))
                .andExpect(jsonPath("$.data.verdict").value("POSSIBLE"))
                .andExpect(jsonPath("$.data.engineVersion").value("DIA-02-1.0"));

        em.flush();
        em.clear();

        mvc.perform(get(endpoint()).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.initialCost.emergencyReserve").value(3000000))
                .andExpect(
                        jsonPath("$.data.policyComparison.expectedLoanAmount").value(80000000))
                .andExpect(jsonPath("$.data.warnings").isEmpty());

        Object rir = em.createNativeQuery("SELECT rir FROM diagnosis WHERE plan_id=:pid")
                .setParameter("pid", planId)
                .getSingleResult();
        assertThat(rir).isNull();
    }

    @Test
    void should_notPersistSimulation() throws Exception {
        mvc.perform(post(endpoint() + "/simulate")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.diagnosisId").isEmpty());

        assertThat(count("diagnosis")).isZero();
        assertThat(count("cost_estimate")).isZero();
    }

    @Test
    void should_requireAuthentication() throws Exception {
        mvc.perform(post(endpoint()).contentType(MediaType.APPLICATION_JSON).content(requestBody()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(endpoint())).andExpect(status().isUnauthorized());
    }

    private String endpoint() {
        return "/api/v1/plans/" + planId + "/diagnosis";
    }

    private String requestBody() {
        return """
                {
                  "movingCost": 1000000,
                  "brokerageFee": 500000,
                  "guaranteeFee": 300000,
                  "stampTax": 75000,
                  "emergencyReserve": 3000000,
                  "monthlyLivingExpense": 900000,
                  "monthlyDebtPayment": 200000,
                  "expectedLoanAmount": 80000000,
                  "expectedMonthlyInterest": 200000
                }
                """;
    }

    private long count(String table) {
        return ((Number) em.createNativeQuery("SELECT count(*) FROM " + table).getSingleResult()).longValue();
    }
}
