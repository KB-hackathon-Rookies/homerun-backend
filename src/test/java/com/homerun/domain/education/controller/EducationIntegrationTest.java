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
import org.junit.jupiter.api.AfterEach;
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
        // 테스트마다 퀴즈 시드가 새지 않도록 정리(콘텐츠 모듈은 기본적으로 퀴즈가 없다).
        jdbc.update("DELETE FROM education_quiz_question");
        Member member = members.save(Member.create(AuthProvider.KAKAO, "edu-" + System.nanoTime(), null, "tester"));
        bearer = "Bearer " + tokens.createAccessToken(member);
    }

    @AfterEach
    void tearDown() {
        // 컨테이너 DB 를 다른 테스트 클래스(MigrationTest 등)와 공유하므로 테스트가 만든 퀴즈를 되돌린다.
        jdbc.update("DELETE FROM education_quiz_question");
    }

    @Test
    @DisplayName("모듈 목록은 시드된 M0~M12(13개)이고 처음엔 모두 NOT_STARTED, 본문 있는 모듈은 hasContent=true 다")
    void lists_seeded_modules() throws Exception {
        mvc.perform(get("/api/v1/education/modules").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(13))
                .andExpect(jsonPath("$.data[0].code").value("M0"))
                .andExpect(jsonPath("$.data[0].status").value("NOT_STARTED"))
                // 시드된 M0~M12 는 본문이 채워져 있어 프론트가 '준비 중'으로 표시하지 않는다.
                .andExpect(jsonPath("$.data[0].hasContent").value(true));
    }

    @Test
    @DisplayName("모듈 상세는 본문을 주고, 퀴즈 없는 콘텐츠 모듈은 빈 퀴즈 배열을 준다")
    void detail_returns_body_and_empty_quiz() throws Exception {
        mvc.perform(get("/api/v1/education/modules/M0").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.body").isNotEmpty())
                .andExpect(jsonPath("$.data.quiz").isEmpty());
    }

    @Test
    @DisplayName("퀴즈 없는 콘텐츠 모듈은 본문을 읽으면 바로 DONE 이 된다")
    void reading_content_module_marks_done() throws Exception {
        mvc.perform(post("/api/v1/education/modules/M1/read").header("Authorization", bearer))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/education/modules/M1").header("Authorization", bearer))
                .andExpect(jsonPath("$.data.status").value("DONE"))
                .andExpect(jsonPath("$.data.progressPct").value(100));
    }

    @Test
    @DisplayName("퀴즈가 붙은 모듈은 읽어도 IN_PROGRESS, 60% 이상 맞혀야 DONE 이 된다")
    void quiz_module_needs_pass_for_done() throws Exception {
        seedQuizFor("M0");

        // 상세에 퀴즈가 나오되 정답(answerIndex)은 노출하지 않는다.
        mvc.perform(get("/api/v1/education/modules/M0").header("Authorization", bearer))
                .andExpect(jsonPath("$.data.quiz").isNotEmpty())
                .andExpect(jsonPath("$.data.quiz[0].answerIndex").doesNotExist());

        // 읽기만 하면 퀴즈가 남아 IN_PROGRESS.
        mvc.perform(post("/api/v1/education/modules/M0/read").header("Authorization", bearer))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/education/modules/M0").header("Authorization", bearer))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        // 통과 제출이면 DONE.
        mvc.perform(post("/api/v1/education/modules/M0/quiz/submit")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passingSubmission("M0")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.status").value("DONE"));
    }

    @Test
    @DisplayName("문항을 누락한 제출은 400 이다")
    void incomplete_submission_is_rejected() throws Exception {
        seedQuizFor("M0");
        Long anyQuestionId = jdbc.queryForObject(
                "SELECT q.id FROM education_quiz_question q JOIN education_content c ON q.content_id = c.id"
                        + " WHERE c.code = 'M0' ORDER BY q.sort_order LIMIT 1",
                Long.class);

        mvc.perform(post("/api/v1/education/modules/M0/quiz/submit")
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
        mvc.perform(post("/api/v1/education/modules/M0/read").header("Authorization", bearer))
                .andExpect(status().isOk());

        Member other = members.save(Member.create(AuthProvider.KAKAO, "edu-other-" + System.nanoTime(), null, "other"));
        String otherBearer = "Bearer " + tokens.createAccessToken(other);

        // 다른 회원에게는 여전히 NOT_STARTED.
        mvc.perform(get("/api/v1/education/modules/M0").header("Authorization", otherBearer))
                .andExpect(jsonPath("$.data.status").value("NOT_STARTED"));
    }

    /** 콘텐츠 모듈에 2문항짜리 퀴즈를 붙여 퀴즈 경로를 검증한다(테스트 설정). */
    private void seedQuizFor(String code) {
        Long contentId = jdbc.queryForObject("SELECT id FROM education_content WHERE code = ?", Long.class, code);
        jdbc.update(
                "INSERT INTO education_quiz_question(content_id, question, options, answer_index, explanation,"
                        + " sort_order) VALUES (?, 'q1', '[\"a\",\"b\"]'::jsonb, 0, 'e1', 1),"
                        + " (?, 'q2', '[\"a\",\"b\"]'::jsonb, 1, 'e2', 2)",
                contentId,
                contentId);
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
