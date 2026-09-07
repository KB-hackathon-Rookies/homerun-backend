package com.homerun.domain.education.controller;

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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "app.jwt.secret=education-integration-test-secret-over-32-bytes",
            "app.jwt.access-token-expiration-seconds=3600"
        })
class EducationIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    MemberRepository members;

    @Autowired
    JwtTokenProvider tokens;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    TermsService terms;

    private String bearer;

    @BeforeEach
    void setUp() {
        when(terms.hasAgreedAllRequired(anyLong())).thenReturn(true);
        Member member = members.save(Member.create(AuthProvider.KAKAO, "edu-" + System.nanoTime(), null, "tester"));
        bearer = "Bearer " + tokens.createAccessToken(member);
    }

    @Test
    @DisplayName("모듈 목록은 6개이고 처음엔 모두 NOT_STARTED 다")
    void lists_six_modules() throws Exception {
        mvc.perform(get("/api/v1/education/modules").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(6))
                .andExpect(jsonPath("$.data[0].status").value("NOT_STARTED"));
    }

    @Test
    @DisplayName("모듈 상세는 본문과 퀴즈를 주되 정답(answerIndex)은 노출하지 않는다")
    void detail_hides_answer_index() throws Exception {
        mvc.perform(get("/api/v1/education/modules/DELINQUENCY").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.body").isNotEmpty())
                .andExpect(jsonPath("$.data.quiz").isNotEmpty())
                .andExpect(jsonPath("$.data.quiz[0].options").isNotEmpty())
                .andExpect(jsonPath("$.data.quiz[0].answerIndex").doesNotExist());
    }

    @Test
    @DisplayName("본문을 읽고 퀴즈를 60% 이상 맞히면 모듈이 DONE 된다")
    void read_then_pass_marks_done() throws Exception {
        mvc.perform(post("/api/v1/education/modules/DELINQUENCY/read").header("Authorization", bearer))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/education/modules/DELINQUENCY/quiz/submit")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passingSubmission("DELINQUENCY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.status").value("DONE"));

        mvc.perform(get("/api/v1/education/modules/DELINQUENCY").header("Authorization", bearer))
                .andExpect(jsonPath("$.data.status").value("DONE"))
                .andExpect(jsonPath("$.data.progressPct").value(100));
    }

    @Test
    @DisplayName("본문을 안 읽고 퀴즈만 통과하면 DONE 이 아니라 IN_PROGRESS 다")
    void pass_without_reading_stays_in_progress() throws Exception {
        mvc.perform(post("/api/v1/education/modules/CREDIT/quiz/submit")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passingSubmission("CREDIT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("문항을 누락한 제출은 400 이다")
    void incomplete_submission_is_rejected() throws Exception {
        Long anyQuestionId = jdbc.queryForObject(
                "SELECT q.id FROM education_quiz_question q JOIN education_content c ON q.content_id = c.id"
                        + " WHERE c.code = 'DEBT' ORDER BY q.sort_order LIMIT 1",
                Long.class);

        mvc.perform(post("/api/v1/education/modules/DEBT/quiz/submit")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[{\"questionId\":" + anyQuestionId + ",\"choiceIndex\":0}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EDU_002"));
    }

    @Test
    @DisplayName("없는 모듈 코드는 404 다")
    void unknown_module_is_not_found() throws Exception {
        mvc.perform(get("/api/v1/education/modules/NOPE").header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EDU_001"));
    }

    @Test
    @DisplayName("진행률은 회원별로 독립이다")
    void progress_is_per_member() throws Exception {
        mvc.perform(post("/api/v1/education/modules/DELINQUENCY/read").header("Authorization", bearer))
                .andExpect(status().isOk());

        Member other = members.save(Member.create(AuthProvider.KAKAO, "edu-other-" + System.nanoTime(), null, "other"));
        String otherBearer = "Bearer " + tokens.createAccessToken(other);

        // 다른 회원에게는 여전히 NOT_STARTED.
        mvc.perform(get("/api/v1/education/modules/DELINQUENCY").header("Authorization", otherBearer))
                .andExpect(jsonPath("$.data.status").value("NOT_STARTED"));
    }

    /** 정답은 응답에 없으므로 시드 DB에서 직접 읽어 통과 제출을 만든다(테스트 설정). */
    private String passingSubmission(String code) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT q.id AS id, q.answer_index AS answer_index"
                        + " FROM education_quiz_question q JOIN education_content c ON q.content_id = c.id"
                        + " WHERE c.code = ? ORDER BY q.sort_order",
                code);
        String items = rows.stream()
                .map(r -> "{\"questionId\":" + r.get("id") + ",\"choiceIndex\":" + r.get("answer_index") + "}")
                .collect(Collectors.joining(","));
        return "{\"answers\":[" + items + "]}";
    }
}
