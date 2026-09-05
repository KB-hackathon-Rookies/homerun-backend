package com.homerun.domain.alternative.controller;

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

/**
 * 대안 재계산이 실제 Postgres 에서 계획을 건드리지 않고 두 결과를 내는지 확인한다(ALT-01-02).
 *
 * <p>계획 입력은 부족자금이 남는 값으로 잡는다 — 이미 충족된 상태면 보증금을 낮춰도 줄어들 것이
 * 없어 델타가 늘 0 이라 아무것도 검증하지 못한다.
 */
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "app.jwt.secret=alternative-recalculation-test-secret-over-32-bytes",
            "app.jwt.access-token-expiration-seconds=3600"
        })
@Transactional
class AlternativeRecalculationIntegrationTest {

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
        Member member = members.save(Member.create(AuthProvider.KAKAO, "alt-recalc-test", null, "tester"));
        bearer = "Bearer " + tokens.createAccessToken(member);
        planId = ((Number) em.createNativeQuery("INSERT INTO plan(user_id,lease_type,target_move_date)"
                                + " VALUES (:uid,'JEONSE',current_date + interval '6 month') RETURNING id")
                        .setParameter("uid", member.getId())
                        .getSingleResult())
                .longValue();
        em.createNativeQuery("INSERT INTO plan_input(plan_id,hope_deposit,current_deposit,monthly_rent,maintenance_fee,"
                        + "monthly_income,available_cash,income_source,financial_data_confirmed)"
                        + " VALUES (:pid,100000000,0,0,100000,3000000,5000000,'MANUAL',true)")
                .setParameter("pid", planId)
                .executeUpdate();
    }

    @Test
    void should_returnBothSidesAndLeavePlanUntouched_whenDepositIsLoweredAgainstRealPostgres() throws Exception {
        long depositBefore = storedHopeDeposit();

        mvc.perform(post(endpoint())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"hopeDeposit\": 80000000,")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current.initialCost.deposit").value(100000000))
                .andExpect(jsonPath("$.data.alternative.initialCost.deposit").value(80000000))
                // 보증금 2천만이 줄면 자기자금도 그만큼 줄어 부족자금이 내려간다.
                .andExpect(jsonPath("$.data.shortfallChange").value(-10275000));

        em.flush();
        em.clear();

        // 대안을 눌러 본 것만으로 계획이나 진단 기록이 바뀌면 안 된다.
        assertThat(storedHopeDeposit()).isEqualTo(depositBefore);
        assertThat(countOf("diagnosis")).isZero();
        assertThat(countOf("cost_estimate")).isZero();
    }

    @Test
    void should_reportNoChange_when_noAxisIsOverriddenAgainstRealPostgres() throws Exception {
        mvc.perform(post(endpoint())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shortfallChange").value(0))
                .andExpect(jsonPath("$.data.possibleDateShiftDays").value(0));
    }

    private String endpoint() {
        return "/api/v1/plans/" + planId + "/alternatives/recalculate";
    }

    private String body(String overrides) {
        return """
                {
                  %s
                  "assumptions": {
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
                }
                """.formatted(overrides);
    }

    private long storedHopeDeposit() {
        return ((Number) em.createNativeQuery("SELECT hope_deposit FROM plan_input WHERE plan_id = :pid")
                        .setParameter("pid", planId)
                        .getSingleResult())
                .longValue();
    }

    private long countOf(String table) {
        return ((Number) em.createNativeQuery("SELECT count(*) FROM " + table + " WHERE plan_id = :pid")
                        .setParameter("pid", planId)
                        .getSingleResult())
                .longValue();
    }
}
