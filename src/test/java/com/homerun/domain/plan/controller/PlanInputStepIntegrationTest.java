package com.homerun.domain.plan.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.auth.type.AuthProvider;
import com.homerun.domain.member.entity.Member;
import com.homerun.domain.member.repository.MemberRepository;
import com.homerun.domain.plan.type.DiagnosisInputStep;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "app.jwt.secret=plan-input-step-integration-test-secret-over-32-bytes",
            "app.jwt.access-token-expiration-seconds=3600"
        })
@Transactional
class PlanInputStepIntegrationTest {

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
    void setUp() {
        when(terms.hasAgreedAllRequired(anyLong())).thenReturn(true);
        Member member = members.save(Member.create(AuthProvider.KAKAO, "step-input-test", null, "tester"));
        bearer = "Bearer " + tokens.createAccessToken(member);
        planId = ((Number)
                        em.createNativeQuery("INSERT INTO plan(user_id,lease_type) VALUES (:uid,'JEONSE') RETURNING id")
                                .setParameter("uid", member.getId())
                                .getSingleResult())
                .longValue();
    }

    @Test
    @DisplayName("다음 버튼마다 현재 STEP만 저장하고 다음 STEP에서 이어간다")
    void saves_each_step_and_resumes_from_next_step() throws Exception {
        save("HOUSEHOLDER", "{\"expectedRevision\":0,\"householderStatus\":\"EXPECTED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextStep").value("HOMELESS"))
                .andExpect(jsonPath("$.data.revision").value(1));
        save("HOMELESS", "{\"expectedRevision\":1,\"isHomeless\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextStep").value("MARITAL_STATUS"))
                .andExpect(jsonPath("$.data.input.householderStatus").value("EXPECTED"))
                .andExpect(jsonPath("$.data.input.isHomeless").value(true));

        mvc.perform(get(inputUrl() + "/resume").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resumeStep").value("MARITAL_STATUS"))
                .andExpect(jsonPath("$.data.completedSteps[0]").value("HOUSEHOLDER"))
                .andExpect(jsonPath("$.data.completedSteps[1]").value("HOMELESS"))
                .andExpect(jsonPath("$.data.progressPercent").value(20))
                .andExpect(jsonPath("$.data.input.householderStatus").value("EXPECTED"));
    }

    @Test
    @DisplayName("프리랜서는 회사 규모와 재직 기간을 건너뛴다")
    void skips_employee_only_steps_for_freelancer() throws Exception {
        save("EMPLOYMENT_TYPE", "{\"expectedRevision\":0,\"employmentType\":\"FREELANCER\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextStep").value("FINANCIAL"))
                .andExpect(jsonPath("$.data.skippedSteps[0]").value("COMPANY_SIZE"))
                .andExpect(jsonPath("$.data.skippedSteps[1]").value("EMPLOYMENT_PERIOD"));

        assertThat(((Number) em.createNativeQuery(
                                        "SELECT count(*) FROM plan_input_step WHERE plan_id=:pid AND status='SKIPPED'")
                                .setParameter("pid", planId)
                                .getSingleResult())
                        .intValue())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("고용 형태를 모름으로 저장해도 근로자 전용 단계를 건너뛴다")
    void skips_employee_only_steps_for_unknown_employment() throws Exception {
        save("EMPLOYMENT_TYPE", "{\"expectedRevision\":0,\"unknownFields\":[\"EMPLOYMENT_TYPE\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextStep").value("FINANCIAL"))
                .andExpect(jsonPath("$.data.skippedSteps[0]").value("COMPANY_SIZE"))
                .andExpect(jsonPath("$.data.skippedSteps[1]").value("EMPLOYMENT_PERIOD"));

        mvc.perform(get(inputUrl() + "/resume").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resumeStep").value("FINANCIAL"));
    }

    @Test
    @DisplayName("오래된 revision으로 저장하면 최신 입력을 덮어쓰지 않는다")
    void rejects_stale_revision() throws Exception {
        save("HOUSEHOLDER", "{\"expectedRevision\":0,\"householderStatus\":\"CURRENT\"}")
                .andExpect(status().isOk());

        save("HOMELESS", "{\"expectedRevision\":0,\"isHomeless\":true}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_018"));
    }

    @Test
    @DisplayName("현재 STEP 소유가 아닌 필드는 저장하지 않는다")
    void rejects_field_from_other_step() throws Exception {
        save("HOUSEHOLDER", "{\"expectedRevision\":0,\"hopeDeposit\":180000000}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLAN_019"));
    }

    @Test
    @DisplayName("금융 STEP은 진단 계산에 필요한 가용 현금까지 저장한다")
    void requires_availableCash_in_financial_step() throws Exception {
        save("FINANCIAL", "{\"expectedRevision\":0,\"monthlyIncome\":2450000,\"netAssets\":36000000}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLAN_020"));

        save(
                        "FINANCIAL",
                        "{\"expectedRevision\":0,\"monthlyIncome\":2450000,\"netAssets\":36000000,"
                                + "\"unknownFields\":[\"AVAILABLE_CASH\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextStep").value("HOPE_DEPOSIT"));
    }

    @Test
    @DisplayName("답변 없는 STEP 저장은 입력과 위치를 모두 롤백한다")
    void rolls_back_input_and_location_when_step_is_incomplete() throws Exception {
        save("HOUSEHOLDER", "{\"expectedRevision\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLAN_020"));

        assertThat(((Number) em.createNativeQuery("SELECT count(*) FROM plan_input WHERE plan_id=:pid")
                                .setParameter("pid", planId)
                                .getSingleResult())
                        .intValue())
                .isZero();
        assertThat(em.createNativeQuery("SELECT last_location_code FROM plan WHERE id=:pid")
                        .setParameter("pid", planId)
                        .getSingleResult())
                .isNull();
    }

    @Test
    @DisplayName("고용 형태가 근로자로 바뀌면 건너뛴 회사 STEP을 다시 연다")
    void reopens_employee_steps_when_employment_branch_changes() throws Exception {
        save("EMPLOYMENT_TYPE", "{\"expectedRevision\":0,\"employmentType\":\"FULL_TIME\"}")
                .andExpect(status().isOk());
        save("COMPANY_SIZE", "{\"expectedRevision\":1,\"companySize\":\"SMALL\"}")
                .andExpect(status().isOk());
        save("EMPLOYMENT_PERIOD", "{\"expectedRevision\":2,\"employmentMonths\":24}")
                .andExpect(status().isOk());
        save("EMPLOYMENT_TYPE", "{\"expectedRevision\":3,\"employmentType\":\"FREELANCER\"}")
                .andExpect(status().isOk());
        save("EMPLOYMENT_TYPE", "{\"expectedRevision\":4,\"employmentType\":\"FULL_TIME\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextStep").value("COMPANY_SIZE"))
                .andExpect(jsonPath("$.data.input.companySize").isEmpty())
                .andExpect(jsonPath("$.data.input.employmentMonths").isEmpty())
                .andExpect(jsonPath("$.data.skippedSteps").isEmpty());

        assertThat(((Number) em.createNativeQuery(
                                        "SELECT count(*) FROM plan_input_step WHERE plan_id=:pid AND status='SKIPPED'")
                                .setParameter("pid", planId)
                                .getSingleResult())
                        .intValue())
                .isZero();
    }

    @Test
    @DisplayName("기존 전체 저장 데이터도 첫 미완료 STEP부터 이어간다")
    void resumes_legacy_snapshot_without_checkpoints() throws Exception {
        em.createNativeQuery("""
                        INSERT INTO plan_input(
                            plan_id, householder_status, is_homeless, marital_status, employment_type)
                        VALUES (:pid, 'EXPECTED', true, 'SINGLE', 'FREELANCER')
                        """).setParameter("pid", planId).executeUpdate();
        em.flush();
        em.clear();

        mvc.perform(get(inputUrl() + "/resume").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resumeStep").value("FINANCIAL"))
                .andExpect(jsonPath("$.data.revision").value(1));
    }

    @Test
    @DisplayName("마지막 확인 STEP은 필수 입력을 검증하고 진단 결과 위치로 이동한다")
    void completes_review_and_moves_to_diagnosis_result() throws Exception {
        Number regionId =
                (Number) em.createNativeQuery("SELECT min(id) FROM region").getSingleResult();
        em.createNativeQuery("""
                        INSERT INTO plan_input(
                            plan_id, hope_deposit, region_id, is_homeless, householder_status,
                            marital_status, employment_type, monthly_income, net_assets, available_cash)
                        VALUES (:pid, 180000000, :regionId, true, 'EXPECTED',
                            'SINGLE', 'FREELANCER', 2450000, 36000000, 20000000)
                        """)
                .setParameter("pid", planId)
                .setParameter("regionId", regionId.longValue())
                .executeUpdate();
        em.flush();
        em.clear();

        save("REVIEW", "{\"expectedRevision\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextStep").isEmpty())
                .andExpect(jsonPath("$.data.progressPercent").value(100));
        assertThat(em.createNativeQuery("SELECT last_location_code FROM plan WHERE id=:pid")
                        .setParameter("pid", planId)
                        .getSingleResult())
                .isEqualTo("DIAGNOSIS_RESULT");
    }

    @Test
    @DisplayName("최종 확인 후 이전 입력을 바꾸면 REVIEW 완료를 무효화한다")
    void invalidates_review_when_previous_input_changes() throws Exception {
        Number regionId =
                (Number) em.createNativeQuery("SELECT min(id) FROM region").getSingleResult();
        em.createNativeQuery("""
                        INSERT INTO plan_input(
                            plan_id, hope_deposit, region_id, is_homeless, householder_status,
                            employment_type, monthly_income, net_assets, available_cash)
                        VALUES (:pid, 180000000, :regionId, true, 'EXPECTED',
                            'FREELANCER', 2450000, 36000000, 20000000)
                        """)
                .setParameter("pid", planId)
                .setParameter("regionId", regionId.longValue())
                .executeUpdate();
        em.persist(new com.homerun.domain.plan.entity.PlanInputStep(planId, DiagnosisInputStep.REVIEW));
        em.flush();

        save("HOPE_DEPOSIT", "{\"expectedRevision\":1,\"hopeDeposit\":190000000}")
                .andExpect(status().isOk());

        assertThat(((Number) em.createNativeQuery(
                                        "SELECT count(*) FROM plan_input_step WHERE plan_id=:pid AND step_code='REVIEW'")
                                .setParameter("pid", planId)
                                .getSingleResult())
                        .intValue())
                .isZero();
    }

    private org.springframework.test.web.servlet.ResultActions save(String step, String body) throws Exception {
        return mvc.perform(put(inputUrl() + "/steps/" + step)
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String inputUrl() {
        return "/api/v1/plans/" + planId + "/input";
    }
}
