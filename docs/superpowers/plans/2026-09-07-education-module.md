# 교육 모듈(교육과 예방) 구현 계획 — v1

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 홈 4-7 화면의 6개 교육 모듈을 콘텐츠(레슨)+퀴즈 채점+진행률로 제공하는 `education` 도메인을 만든다.

**Architecture:** 새 `education` 도메인(도메인 표준 레이어: entity/repository/service/controller/dto/type). 콘텐츠는 DB 시드, 진행률은 member 스코프. 퀴즈 채점·진행률 전이는 서비스에서, 콘텐츠 전달은 컨트롤러에서.

**Tech Stack:** Spring Boot 3(웹MVC), Spring Data JPA, PostgreSQL(Flyway), JUnit5 + Testcontainers + MockMvc, Jackson.

**Spec:** `docs/superpowers/specs/2026-09-07-education-module-design.md`

## Global Constraints

- 마이그레이션 번호: 착수 직전 `git fetch origin && git ls-tree --name-only origin/dev src/main/resources/db/migration/ | sort -V | tail -1` 로 최신 확인. 이 계획은 **V69** 를 가정하되 더 큰 번호가 있으면 그다음으로 올린다.
- 회원 테이블명은 `app_user`, 표시명 컬럼은 `name`(닉네임 아님).
- 응답은 `com.homerun.global.response.ApiResponse<T>` 로 감싼다(`ApiResponse.success(data)`).
- 인증 회원은 `@AuthenticationPrincipal MemberPrincipal principal` → `principal.memberId()`.
- 퀴즈 정답(`answer_index`)은 어떤 응답 DTO에도 넣지 않는다.
- 에러는 `throw new BusinessException(ErrorCode.XXX)`. 새 코드는 `ErrorCode` 에 추가.
- 포맷: 커밋 전 `./gradlew spotlessApply`.
- 패키지 루트: `com.homerun.domain.education`.

---

### Task 1: 스키마·시드 마이그레이션 + MigrationTest

**Files:**
- Create: `src/main/resources/db/migration/V69__add_education_module.sql`
- Modify: `src/test/java/com/homerun/MigrationTest.java` (테이블·시드 검증 추가)

**Interfaces:**
- Produces 테이블: `education_module`, `education_lesson`, `education_quiz_question`, `education_lesson_completion`, `education_progress`.

- [ ] **Step 1: 마이그레이션 작성** — `V69__add_education_module.sql`

```sql
CREATE TABLE education_module (
    id          BIGSERIAL    PRIMARY KEY,
    code        VARCHAR(40)  NOT NULL UNIQUE,
    title       VARCHAR(100) NOT NULL,
    subtitle    VARCHAR(200),
    sort_order  INT          NOT NULL
);
CREATE INDEX idx_education_module_order ON education_module (sort_order);

CREATE TABLE education_lesson (
    id          BIGSERIAL PRIMARY KEY,
    module_id   BIGINT    NOT NULL REFERENCES education_module (id) ON DELETE RESTRICT,
    title       VARCHAR(150) NOT NULL,
    body        TEXT      NOT NULL,
    sort_order  INT       NOT NULL
);
CREATE INDEX idx_education_lesson_module ON education_lesson (module_id, sort_order);

CREATE TABLE education_quiz_question (
    id           BIGSERIAL PRIMARY KEY,
    module_id    BIGINT    NOT NULL REFERENCES education_module (id) ON DELETE RESTRICT,
    question     TEXT      NOT NULL,
    options      JSONB     NOT NULL,
    answer_index INT       NOT NULL CHECK (answer_index >= 0),
    explanation  TEXT,
    sort_order   INT       NOT NULL,
    CHECK (jsonb_typeof(options) = 'array' AND jsonb_array_length(options) >= 2),
    CHECK (answer_index < jsonb_array_length(options))
);
CREATE INDEX idx_education_quiz_module ON education_quiz_question (module_id, sort_order);

CREATE TABLE education_lesson_completion (
    id           BIGSERIAL PRIMARY KEY,
    member_id    BIGINT NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    lesson_id    BIGINT NOT NULL REFERENCES education_lesson (id) ON DELETE RESTRICT,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (member_id, lesson_id)
);

CREATE TABLE education_progress (
    id         BIGSERIAL PRIMARY KEY,
    member_id  BIGINT NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    module_id  BIGINT NOT NULL REFERENCES education_module (id) ON DELETE RESTRICT,
    status     VARCHAR(20) NOT NULL CHECK (status IN ('NOT_STARTED','IN_PROGRESS','DONE')),
    quiz_score INT CHECK (quiz_score >= 0),
    quiz_total INT CHECK (quiz_total > 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (member_id, module_id),
    CHECK (quiz_score IS NULL OR (quiz_total IS NOT NULL AND quiz_score <= quiz_total))
);

-- 시드: 6개 모듈 (콘텐츠는 프론트 부제·홈 노션 근거. 신용·부채류는 교육용 고지·출처 포함)
INSERT INTO education_module (code, title, subtitle, sort_order) VALUES
  ('DELINQUENCY',     '연체 예방과 위험 관리', '매달 자동이체·연체율·이자 부담 이해', 1),
  ('CREDIT',          '신용관리 진단',         'CB사 점수·신용카드 관리·신용점수 개선', 2),
  ('DEBT',            '부채 관리',             'DSR·DTI 개념과 상환계획', 3),
  ('FINANCIAL_FRAUD', '금융 사기 예방',        '보이스피싱·투자사기·스미싱 사례', 4),
  ('JEONSE_FRAUD',    '전세 사기 예방',        '깡통전세·불법건축물·이중계약 사례', 5),
  ('EXPERIENTIAL',    '체험형 금융 교육',      '퀴즈로 점검하는 금융 상식', 6);

-- 레슨: 모듈당 1~2개. body 는 마크다운. (예시 2개, 나머지는 같은 패턴으로 작성)
INSERT INTO education_lesson (module_id, title, body, sort_order)
SELECT id, '연체가 왜 위험한가',
       '# 연체가 왜 위험한가\n\n대출 이자를 제때 못 내면 연체이자가 붙습니다. 연체 3개월 이내는 대출금리 + 4%p, 3개월 초과는 + 5%p 입니다. 기한이익 상실 후에는 원금 전체에 붙습니다.\n\n> 교육용 안내이며 실제 금융 자문이 아닙니다. 출처: KB Think(2026-08-31).\n\n**자동이체를 걸어 두면** 대부분의 연체를 막을 수 있습니다.',
       1
FROM education_module WHERE code = 'DELINQUENCY';
INSERT INTO education_lesson (module_id, title, body, sort_order)
SELECT id, '전세 사기 3대 유형',
       '# 전세 사기 3대 유형\n\n1. 깡통전세 — 보증금이 집값에 육박해 못 돌려받는 경우\n2. 불법건축물 — 근생빌라 등 대출·보증이 안 되는 매물\n3. 이중계약 — 한 집을 여러 세입자와 계약\n\n> 교육용 안내입니다. 실제 매물 판정은 2루 검증을 따르세요.',
       1
FROM education_module WHERE code = 'JEONSE_FRAUD';
-- (CREDIT/DEBT/FINANCIAL_FRAUD/EXPERIENTIAL 레슨도 위 패턴으로 각 1개 이상 INSERT ... SELECT 로 추가)

-- 퀴즈: 모듈당 3문항 예시(정답은 answer_index). 나머지 모듈도 같은 패턴.
INSERT INTO education_quiz_question (module_id, question, options, answer_index, explanation, sort_order)
SELECT id, '대출 이자를 3개월 넘게 연체하면 가산되는 연체이율은?',
       '["대출금리 + 2%p","대출금리 + 4%p","대출금리 + 5%p","가산 없음"]'::jsonb, 2,
       '3개월 초과 연체는 대출금리 + 5%p 입니다.', 1
FROM education_module WHERE code = 'DELINQUENCY';
INSERT INTO education_quiz_question (module_id, question, options, answer_index, explanation, sort_order)
SELECT id, '연체를 예방하는 가장 확실한 방법은?',
       '["매달 수동 이체","자동이체 등록","만기 일시 상환","신용카드 결제"]'::jsonb, 1,
       '자동이체를 걸어 두면 실수로 인한 연체를 막을 수 있습니다.', 2
FROM education_module WHERE code = 'DELINQUENCY';
INSERT INTO education_quiz_question (module_id, question, options, answer_index, explanation, sort_order)
SELECT id, '보증금이 집값에 육박해 못 돌려받을 위험이 큰 전세는?',
       '["깡통전세","안심전세","반환보증","보증부월세"]'::jsonb, 0,
       '깡통전세는 보증금 회수가 어려운 대표적 위험 유형입니다.', 1
FROM education_module WHERE code = 'JEONSE_FRAUD';
-- (모듈별 3문항 이상이 되도록 나머지 INSERT 추가. answer_index 는 반드시 options 범위 안)
```

- [ ] **Step 2: MigrationTest 에 검증 추가** — `MigrationTest.java` 에 메서드 추가

```java
@Test
@DisplayName("V69가 교육 모듈 테이블과 6개 모듈 시드를 추가한다")
void should_add_education_tables_when_v69IsApplied() {
    assertThat(tableNames())
            .contains("education_module", "education_lesson", "education_quiz_question",
                    "education_lesson_completion", "education_progress");
    Integer modules = jdbc.queryForObject("SELECT count(*) FROM education_module", Integer.class);
    assertThat(modules).isEqualTo(6);
    // 모든 퀴즈 정답 index 가 보기 범위 안(시드 무결성)
    Integer invalid = jdbc.queryForObject(
            "SELECT count(*) FROM education_quiz_question WHERE answer_index >= jsonb_array_length(options)",
            Integer.class);
    assertThat(invalid).isZero();
    // 모듈마다 퀴즈가 최소 1문항
    Integer emptyModules = jdbc.queryForObject(
            "SELECT count(*) FROM education_module m WHERE NOT EXISTS "
                    + "(SELECT 1 FROM education_quiz_question q WHERE q.module_id = m.id)",
            Integer.class);
    assertThat(emptyModules).isZero();
}
```

- [ ] **Step 3: 실행** — `./gradlew test --tests "com.homerun.MigrationTest"` → PASS (컨텍스트 로드 = Flyway 적용 성공, 시드 검증 통과)
- [ ] **Step 4: 커밋** — `git add ... && git commit -m "✨ Feat: 교육 모듈 스키마·시드 마이그레이션 (V69)"`

---

### Task 2: JPA 엔티티

**Files:**
- Create: `.../education/entity/EducationModule.java`, `EducationLesson.java`, `EducationQuizQuestion.java`, `EducationLessonCompletion.java`, `EducationProgress.java`
- Create: `.../education/type/EducationProgressStatus.java`

**Interfaces:**
- Produces: `EducationModule{Long getId(); String getCode(); String getTitle(); String getSubtitle(); int getSortOrder();}`
- `EducationLesson{Long getId(); Long getModuleId(); String getTitle(); String getBody(); int getSortOrder();}`
- `EducationQuizQuestion{Long getId(); Long getModuleId(); String getQuestion(); List<String> getOptions(); int getAnswerIndex(); String getExplanation(); int getSortOrder();}`
- `EducationProgress{Long getMemberId(); Long getModuleId(); EducationProgressStatus getStatus(); Integer getQuizScore(); Integer getQuizTotal(); void applyQuiz(int score,int total); void markDone(); void ensureInProgress();}`
- `EducationProgressStatus{NOT_STARTED, IN_PROGRESS, DONE}`

- [ ] **Step 1: 엔티티 작성** — 표준 `@Entity`/`@Table`/`@Column` 매핑. `options` 는 JSONB → `@JdbcTypeCode(SqlTypes.JSON) private List<String> options;` (Hibernate 6). `EducationProgress` 에 도메인 메서드:

```java
public void ensureInProgress() {
    if (status == EducationProgressStatus.NOT_STARTED) {
        status = EducationProgressStatus.IN_PROGRESS;
        updatedAt = Instant.now();
    }
}
public void applyQuiz(int score, int total) {
    this.quizScore = score; this.quizTotal = total; this.updatedAt = Instant.now();
}
public void markDone() { this.status = EducationProgressStatus.DONE; this.updatedAt = Instant.now(); }
public static EducationProgress start(Long memberId, Long moduleId) { /* NOT_STARTED */ }
```

- [ ] **Step 2: 컴파일** — `./gradlew compileJava` → PASS
- [ ] **Step 3: 커밋** — `"✨ Feat: 교육 도메인 엔티티"`

---

### Task 3: 리포지토리

**Files:**
- Create: `.../education/repository/EducationModuleRepository.java`, `EducationLessonRepository.java`, `EducationQuizQuestionRepository.java`, `EducationLessonCompletionRepository.java`, `EducationProgressRepository.java`

**Interfaces (Produces):**
- `EducationModuleRepository extends JpaRepository<EducationModule,Long>{ List<EducationModule> findAllByOrderBySortOrderAsc(); Optional<EducationModule> findByCode(String code); }`
- `EducationLessonRepository{ List<EducationLesson> findByModuleIdOrderBySortOrderAsc(Long moduleId); long countByModuleId(Long moduleId); Optional<EducationLesson> findByIdAndModuleId(Long id, Long moduleId); }`
- `EducationQuizQuestionRepository{ List<EducationQuizQuestion> findByModuleIdOrderBySortOrderAsc(Long moduleId); }`
- `EducationLessonCompletionRepository{ boolean existsByMemberIdAndLessonId(Long m, Long l); long countByMemberIdAndLessonModuleId(...) 또는 memberId+lessonId in 목록; List<Long> ... }`
  - 학습률 계산용: `@Query("select lc.lessonId from EducationLessonCompletion lc where lc.memberId=?1") Set<Long> findCompletedLessonIds(Long memberId)` (혹은 모듈 스코프).
- `EducationProgressRepository{ Optional<EducationProgress> findByMemberIdAndModuleId(Long m, Long mod); List<EducationProgress> findByMemberId(Long m); }`

- [ ] **Step 1: 작성** → **Step 2:** `./gradlew compileJava` → **Step 3: 커밋** `"✨ Feat: 교육 도메인 리포지토리"`

---

### Task 4: 퀴즈 채점기(순수 로직) — TDD

**Files:**
- Create: `.../education/service/QuizGrader.java`
- Test: `src/test/java/com/homerun/domain/education/service/QuizGraderTest.java`

**Interfaces:**
- Consumes: `EducationQuizQuestion` (getId, getOptions, getAnswerIndex, getExplanation)
- Produces: `record GradedAnswer(Long questionId, int choiceIndex, boolean correct, String explanation)` ; `record QuizResult(int score, int total, List<GradedAnswer> answers){ boolean passed(){ return total>0 && score*100 >= total*60; } }` ; `QuizResult grade(List<EducationQuizQuestion> questions, List<SubmittedAnswer> answers)` (throws BusinessException on invalid submission). `record SubmittedAnswer(Long questionId, int choiceIndex)`.

- [ ] **Step 1: 실패 테스트** — `QuizGraderTest`

```java
class QuizGraderTest {
    private final QuizGrader grader = new QuizGrader();

    private EducationQuizQuestion q(long id, int answer) {
        return EducationQuizQuestion.of(id, 1L, "q" + id, List.of("a", "b", "c"), answer, "why", (int) id);
    }

    @Test
    @DisplayName("정답 수와 통과 여부를 계산한다(60% 임계값)")
    void grades_and_marks_pass() {
        var questions = List.of(q(1, 0), q(2, 1), q(3, 2));
        var result = grader.grade(questions, List.of(
                new SubmittedAnswer(1L, 0), new SubmittedAnswer(2L, 1), new SubmittedAnswer(3L, 0)));
        assertThat(result.score()).isEqualTo(2);
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.passed()).isTrue(); // 2/3 = 66% >= 60
    }

    @Test
    @DisplayName("문항 누락·중복·타 문항 제출은 거부한다")
    void rejects_incomplete_submission() {
        var questions = List.of(q(1, 0), q(2, 1));
        assertThatThrownBy(() -> grader.grade(questions, List.of(new SubmittedAnswer(1L, 0))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.EDUCATION_QUIZ_SUBMISSION_INVALID));
    }

    @Test
    @DisplayName("보기 범위를 벗어난 choiceIndex 는 거부한다")
    void rejects_out_of_range_choice() {
        var questions = List.of(q(1, 0));
        assertThatThrownBy(() -> grader.grade(questions, List.of(new SubmittedAnswer(1L, 5))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.EDUCATION_QUIZ_SUBMISSION_INVALID));
    }
}
```

- [ ] **Step 2: 실패 확인** — `./gradlew test --tests "*QuizGraderTest"` → FAIL(컴파일 에러: QuizGrader 없음). `ErrorCode.EDUCATION_QUIZ_SUBMISSION_INVALID`(400) 도 추가.
- [ ] **Step 3: 구현** — `QuizGrader.grade`: 제출 questionId 집합이 문항 집합과 정확히 일치(크기+원소)해야 하고, 각 choiceIndex 가 `0 <= i < options.size()`. 아니면 `EDUCATION_QUIZ_SUBMISSION_INVALID`. 일치하면 각 문항 `answerIndex == choiceIndex` 로 채점.
- [ ] **Step 4: 통과 확인** → **Step 5: 커밋** `"✨ Feat: 교육 퀴즈 채점기 + 제출 검증"`

---

### Task 5: 교육 서비스(진행률·완료 전이) — TDD

**Files:**
- Create: `.../education/service/EducationService.java`
- Create: DTO records (`.../education/dto/response/…`, `.../education/dto/request/QuizSubmitRequest.java`)
- Test: `.../education/service/EducationServiceTest.java`

**Interfaces:**
- `QuizSubmitRequest(List<AnswerItem> answers)`, `AnswerItem(@NotNull Long questionId, @NotNull @PositiveOrZero Integer choiceIndex)`
- `EducationService`:
  - `List<ModuleSummaryResponse> listModules(Long memberId)`
  - `ModuleDetailResponse getModule(Long memberId, String code)` (정답 미포함)
  - `void completeLesson(Long memberId, String code, Long lessonId)`
  - `QuizSubmitResponse submitQuiz(Long memberId, String code, QuizSubmitRequest req)`
- Produces DONE 규칙: 모듈의 모든 레슨이 `education_lesson_completion` 에 있고 AND 최신 퀴즈 `passed()` 이면 `markDone()`, 아니면 `ensureInProgress()`.

- [ ] **Step 1: 실패 테스트** — `EducationServiceTest`(Testcontainers 통합; 리포지토리 실주입). 시드 모듈 사용.

```java
@Test
@DisplayName("모든 레슨 완료 + 퀴즈 60%↑ 이면 모듈이 DONE 된다")
void module_done_requires_lessons_and_quiz() {
    Long memberId = savedMember();
    // 레슨 미완료 상태에서 퀴즈만 통과 → IN_PROGRESS
    var passingAnswers = allCorrect(memberId, "DELINQUENCY");
    service.submitQuiz(memberId, "DELINQUENCY", passingAnswers);
    assertThat(status(memberId, "DELINQUENCY")).isEqualTo(EducationProgressStatus.IN_PROGRESS);
    // 레슨 모두 완료 후 다시 통과 → DONE
    completeAllLessons(memberId, "DELINQUENCY");
    service.submitQuiz(memberId, "DELINQUENCY", passingAnswers);
    assertThat(status(memberId, "DELINQUENCY")).isEqualTo(EducationProgressStatus.DONE);
}

@Test
@DisplayName("모듈 상세 응답에는 퀴즈 정답이 포함되지 않는다")
void module_detail_hides_answers() {
    var detail = service.getModule(savedMember(), "DELINQUENCY");
    // QuizQuestionResponse 에 answerIndex 필드 자체가 없어야 한다(컴파일·직렬화로 보장)
    assertThat(detail.quiz()).isNotEmpty();
}
```

- [ ] **Step 2: 실패 확인** → **Step 3: 구현** — 응답 DTO는 `answerIndex` 를 갖지 않는다. `submitQuiz`: 모듈 조회(없으면 `EDUCATION_MODULE_NOT_FOUND` 404) → 문항 로드 → `QuizGrader.grade` → progress upsert(`applyQuiz`) → DONE 판정(모든 레슨 완료 && passed) → 응답(점수·문항별 정오·해설). `completeLesson`: 레슨이 모듈 소속인지 확인(아니면 404), `education_lesson_completion` upsert(중복 무시), `progress.ensureInProgress()`.
- [ ] **Step 4: 통과 확인** → **Step 5: 커밋** `"✨ Feat: 교육 서비스(진행률·완료 전이·정답 비노출)"`

---

### Task 6: 컨트롤러 + 통합테스트 — TDD

**Files:**
- Create: `.../education/controller/EducationController.java`
- Test: `.../education/controller/EducationIntegrationTest.java`

**Interfaces (Produces endpoints):** `@RequestMapping("/api/v1/education")`
- `GET /modules` → `ApiResponse<List<ModuleSummaryResponse>>`
- `GET /modules/{code}` → `ApiResponse<ModuleDetailResponse>`
- `POST /modules/{code}/lessons/{lessonId}/complete` → `ApiResponse<Void>`
- `POST /modules/{code}/quiz/submit` (@Valid QuizSubmitRequest) → `ApiResponse<QuizSubmitResponse>`

- [ ] **Step 1: 실패 통합테스트** — `EducationIntegrationTest`(패턴은 `FirstBaseCompletionIntegrationTest` 참고: `@SpringBootTest` + `@AutoConfigureMockMvc` + `TestcontainersConfiguration` + `JwtTokenProvider` 로 Bearer 발급). 검증: 목록 6개, 상세에 `answerIndex` **미노출**(`jsonPath("$.data.quiz[0].answerIndex").doesNotExist()`), 레슨 완료 200, 퀴즈 채점 200(점수 필드), 잘못된 제출 400, 없는 code 404, **타 회원이 내 진행률에 영향 없음**(각자 progress 독립).
- [ ] **Step 2: 실패 확인** → **Step 3: 컨트롤러 구현**(서비스 위임) + `SecurityConfig` 확인: `/api/v1/education/**` 는 인증 필요(기본 `.anyRequest().authenticated()` 에 걸리므로 별도 permitAll 불필요).
- [ ] **Step 4: 통과 확인** → **Step 5: 커밋** `"✨ Feat: 교육 API 컨트롤러 + 통합테스트"`

---

### Task 7: 홈 대시보드 교육 요약(선택)

**Files:**
- Modify: `.../settlement/service/SettlementDashboardService.java` (교육 요약 필드 추가)
- Modify: 대시보드 응답 DTO
- Test: 기존 대시보드 테스트에 교육 요약 단정 1개 추가

**Interfaces:** 대시보드 응답에 `EducationSummary(int completed, int total)` 추가. `EducationProgressRepository.findByMemberId` 로 DONE 수 집계, total=6.

- [ ] **Step 1: 실패 테스트**(대시보드 응답에 `education.completed`/`total`) → **Step 2: 구현** → **Step 3: 통과** → **Step 4: 커밋** `"✨ Feat: 홈 대시보드에 교육 진행 요약 추가"`

> 이 태스크는 선택이다. 대시보드 결합을 나중으로 미루려면 스킵하고 PR 범위에서 제외한다.

---

### Task 8: 전체 검증 + PR

- [ ] **Step 1:** `./gradlew clean test` → 전체 PASS
- [ ] **Step 2:** 이슈 생성(`gh issue create`) — 교육 모듈 v1
- [ ] **Step 3:** PR 생성(base dev, PR 템플릿) — 스펙·계획 링크, 정답 비노출·DONE 기준·member 스코프 리뷰 요청, v1 제외 항목 명시

---

## Self-Review

**Spec coverage:**
- 6개 모듈/레슨/퀴즈/진행률 → Task 1(시드)·2·3·5. ✅
- 레슨 완료 이력 → `education_lesson_completion`(Task 1) + `completeLesson`(Task 5). ✅
- DONE = 모든 레슨 + 퀴즈 60% → Task 5 Step 3 + 테스트. ✅
- 퀴즈 제출 배열 DTO + 전 문항 1회 규칙 → Task 4(QuizGrader 검증) + Task 5 DTO. ✅
- 정답 비노출 → Task 5/6(응답 DTO에 answerIndex 없음 + 통합테스트 doesNotExist). ✅
- DB 제약(CHECK·FK RESTRICT·UNIQUE·DEFAULT·index) → Task 1 DDL. ✅
- answer_index 범위 시드 검증 → Task 1 DDL CHECK + MigrationTest. ✅
- member 스코프·인증·권한 → Task 6. ✅
- 홈 대시보드 요약(선택) → Task 7. ✅
- 콘텐츠 안전(고지·출처) → Task 1 시드 body 에 문구 포함. ✅

**Placeholder scan:** 시드 SQL·엔티티/리포지토리 보일러플레이트는 "같은 패턴으로 추가" 지시가 있으나, 채워야 할 내용(컬럼·시그니처·규칙)은 전부 명시했다. 로직·테스트·DDL 등 판단이 필요한 부분은 실제 코드로 제공했다.

**Type consistency:** `SubmittedAnswer`/`AnswerItem`(요청 DTO는 AnswerItem, 서비스 내부는 SubmittedAnswer 로 변환), `QuizResult.passed()`(60%), `EducationProgressStatus` 3값 — Task 4·5·6에서 일관.
