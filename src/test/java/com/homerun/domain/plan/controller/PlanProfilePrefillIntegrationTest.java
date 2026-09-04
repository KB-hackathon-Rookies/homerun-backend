package com.homerun.domain.plan.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.auth.type.AuthProvider;
import com.homerun.domain.member.entity.Member;
import com.homerun.domain.member.repository.MemberRepository;
import com.homerun.domain.terms.service.TermsService;
import com.homerun.global.security.jwt.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
            "app.jwt.secret=profile-prefill-integration-test-secret-over-32-bytes",
            "app.jwt.access-token-expiration-seconds=3600"
        })
@Transactional
class PlanProfilePrefillIntegrationTest {
    private static final String PROFILE = "/api/v1/members/me/diagnosis-profile";

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

    private Long memberId;
    private Long planId;
    private String bearer;

    @BeforeEach
    void setup() {
        when(terms.hasAgreedAllRequired(anyLong())).thenReturn(true);
        Member member = members.save(Member.create(AuthProvider.KAKAO, "prefill-test", null, "tester"));
        memberId = member.getId();
        bearer = "Bearer " + tokens.createAccessToken(member);
        planId = ((Number)
                        em.createNativeQuery("INSERT INTO plan(user_id,lease_type) VALUES (:uid,'JEONSE') RETURNING id")
                                .setParameter("uid", memberId)
                                .getSingleResult())
                .longValue();
    }

    private String prefill() {
        return "/api/v1/plans/" + planId + "/input/profile-prefill";
    }

    private void saveProfile(String body) throws Exception {
        mvc.perform(put(PROFILE)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        em.flush();
        em.clear();
    }

    @Test
    void should_notTreatLegacyDefaultZeroAsConfirmed() throws Exception {
        mvc.perform(get(PROFILE).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.militaryMonths").isEmpty());
        mvc.perform(get(prefill()).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.birthDate.source").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.militaryMonths.source").value("UNAVAILABLE"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 18, 60})
    void should_prefillConfirmedValues_withoutSavingPlan(int months) throws Exception {
        saveProfile("{\"birthDate\":\"1998-05-14\",\"militaryMonths\":" + months + "}");
        for (int i = 0; i < 2; i++) {
            mvc.perform(get(prefill()).header("Authorization", bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.birthDate.value").value("1998-05-14"))
                    .andExpect(jsonPath("$.data.birthDate.source").value("MEMBER_PROFILE"))
                    .andExpect(jsonPath("$.data.militaryMonths.value").value(months))
                    .andExpect(jsonPath("$.data.militaryMonths.source").value("MEMBER_PROFILE"));
        }
        assertThat(((Number) em.createNativeQuery("SELECT count(*) FROM plan_input WHERE plan_id=:pid")
                                .setParameter("pid", planId)
                                .getSingleResult())
                        .longValue())
                .isZero();
    }

    @Test
    void should_preserveSavedInput_when_profileChanges() throws Exception {
        em.createNativeQuery("INSERT INTO plan_input(plan_id,birth_date,military_months) VALUES (:pid,'1997-01-01',0)")
                .setParameter("pid", planId)
                .executeUpdate();
        saveProfile("{\"birthDate\":\"1998-05-14\",\"militaryMonths\":18}");
        mvc.perform(get(prefill()).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.birthDate.value").value("1997-01-01"))
                .andExpect(jsonPath("$.data.birthDate.source").value("SAVED_INPUT"))
                .andExpect(jsonPath("$.data.militaryMonths.value").value(0))
                .andExpect(jsonPath("$.data.militaryMonths.source").value("SAVED_INPUT"));
        assertThat(((Number) em.createNativeQuery("SELECT revision FROM plan_input WHERE plan_id=:pid")
                                .setParameter("pid", planId)
                                .getSingleResult())
                        .intValue())
                .isEqualTo(1);
    }

    @Test
    void should_preserveExplicitUnknown_insteadOfFallingBack() throws Exception {
        em.createNativeQuery(
                        "INSERT INTO plan_input(plan_id,unknown_fields) VALUES (:pid,'[\"BIRTH_DATE\",\"MILITARY_MONTHS\"]'::jsonb)")
                .setParameter("pid", planId)
                .executeUpdate();
        saveProfile("{\"birthDate\":\"1998-05-14\",\"militaryMonths\":18}");
        mvc.perform(get(prefill()).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.birthDate.value").isEmpty())
                .andExpect(jsonPath("$.data.birthDate.source").value("EXPLICIT_UNKNOWN"))
                .andExpect(jsonPath("$.data.militaryMonths.value").isEmpty())
                .andExpect(jsonPath("$.data.militaryMonths.source").value("EXPLICIT_UNKNOWN"));
    }

    @Test
    void should_clearProfile_when_nullSnapshotSubmitted() throws Exception {
        saveProfile("{\"birthDate\":\"1998-05-14\",\"militaryMonths\":18}");
        saveProfile("{}");
        mvc.perform(get(PROFILE).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.birthDate").isEmpty())
                .andExpect(jsonPath("$.data.militaryMonths").isEmpty());
    }

    @Test
    void should_fillOnlyMissingField_when_otherFieldIsSaved() throws Exception {
        em.createNativeQuery("INSERT INTO plan_input(plan_id,birth_date) VALUES (:pid,'1997-01-01')")
                .setParameter("pid", planId)
                .executeUpdate();
        saveProfile("{\"birthDate\":\"1998-05-14\",\"militaryMonths\":18}");
        mvc.perform(get(prefill()).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.birthDate.source").value("SAVED_INPUT"))
                .andExpect(jsonPath("$.data.militaryMonths.value").value(18))
                .andExpect(jsonPath("$.data.militaryMonths.source").value("MEMBER_PROFILE"));
        assertThat(em.createNativeQuery("SELECT military_months FROM plan_input WHERE plan_id=:pid")
                        .setParameter("pid", planId)
                        .getSingleResult())
                .isNull();
    }

    @Test
    void should_notTrustLegacyNonzeroMilitaryValue_untilConfirmed() throws Exception {
        em.createNativeQuery("UPDATE app_user SET military_months=18 WHERE id=:uid")
                .setParameter("uid", memberId)
                .executeUpdate();
        em.clear();
        mvc.perform(get(PROFILE).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.militaryMonths").isEmpty());
    }

    @Test
    void should_rejectWithdrawnMember() throws Exception {
        members.findById(memberId).orElseThrow().withdraw();
        em.flush();
        mvc.perform(get(prefill()).header("Authorization", bearer)).andExpect(status().isUnauthorized());
        mvc.perform(put(PROFILE)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 61})
    void should_rejectInvalidMilitaryMonths(int months) throws Exception {
        mvc.perform(put(PROFILE)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"militaryMonths\":" + months + "}"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void should_rejectTodayAndFutureBirthDate(int days) throws Exception {
        mvc.perform(put(PROFILE)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"birthDate\":\"" + LocalDate.now().plusDays(days) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_rejectOtherMemberPlan() throws Exception {
        Member other = members.save(Member.create(AuthProvider.KAKAO, "prefill-other", null, "other"));
        mvc.perform(get(prefill()).header("Authorization", "Bearer " + tokens.createAccessToken(other)))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_rejectMissingPlan() throws Exception {
        mvc.perform(get("/api/v1/plans/9223372036854775807/input/profile-prefill")
                        .header("Authorization", bearer))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_requireAuthenticationForAllEndpoints() throws Exception {
        mvc.perform(get(prefill())).andExpect(status().isUnauthorized());
        mvc.perform(get(PROFILE)).andExpect(status().isUnauthorized());
        mvc.perform(put(PROFILE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
