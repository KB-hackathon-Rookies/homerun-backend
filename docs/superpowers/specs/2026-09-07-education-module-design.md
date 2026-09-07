# 교육 모듈(교육과 예방) 설계 — v1

작성일: 2026-09-07

## 배경

홈(정착) 단계 화면 **4-7 교육과 예방**은 사회초년생이 입주 후 꾸준히 학습할 6개 교육 모듈을 보여준다. 지금까지 백엔드에 대응 기능이 없어(콘텐츠·진행률·퀴즈) 이번에 신설한다.

프론트 화면의 6개 모듈:

| code | 제목 | 부제 |
|------|------|------|
| `DELINQUENCY` | 연체 예방과 위험 관리 | 매달 자동이체·연체율·이자 시뮬레이션 |
| `CREDIT` | 신용관리 진단 | CB사 점수·신용카드 관리·신용점수 개선 |
| `DEBT` | 부채 관리 | DSR·DTI·상환계획 |
| `FINANCIAL_FRAUD` | 금융 사기 예방 | 보이스피싱·투자사기·스미싱 사례 |
| `JEONSE_FRAUD` | 전세 사기 예방 | 깡통전세·불법건축물·이중계약 사례 |
| `EXPERIENTIAL` | 체험형 금융 교육 | 퀴즈·시뮬레이션·상황극 |

## 범위 (v1)

**v1 = 콘텐츠(레슨) + 퀴즈 채점 + 진행률.**

- 포함: 모듈 목록·상세(레슨 본문), 퀴즈 채점, 모듈별 진행률(NOT_STARTED/IN_PROGRESS/DONE), 홈 대시보드 요약.
- 제외(후속): DSR/DTI 계산기, 이자 시뮬레이션, 상황극/시뮬레이션 인터랙션. 이런 데이터·계산 모듈은 v1에서 **기존 `settlement` 도구로 링크만** 한다(재구현하지 않는다).
- `EXPERIENTIAL` 모듈은 v1에서 퀴즈 중심으로만 구성한다(시뮬레이션·상황극은 후속).

## 결정 사항

- **콘텐츠 저장**: DB 시드(마이그레이션). 정책 팩트·정책 판정처럼 데이터는 DB에 둔다는 레포 철학과 일치하고, 리뷰어가 콘텐츠 원문을 한곳에서 검수한다.
- **스코프**: **member 스코프**. 교육 콘텐츠는 특정 계획과 무관한 개인 학습이라 `planId` 를 요구하지 않는다. 홈 대시보드(plan 스코프)는 회원의 교육 진행률을 요약으로만 가져온다.
- **정답 비노출**: 퀴즈 정답(`answer_index`)은 클라이언트에 내려보내지 않는다. 채점은 서버가 한다.

## 데이터 모델 (V69 마이그레이션)

```
education_module
  id            BIGSERIAL PK
  code          VARCHAR UNIQUE      -- DELINQUENCY 등
  title         VARCHAR
  subtitle      VARCHAR
  sort_order    INT

education_lesson
  id            BIGSERIAL PK
  module_id     BIGINT FK -> education_module
  title         VARCHAR
  body          TEXT                -- 마크다운
  sort_order    INT

education_quiz_question
  id            BIGSERIAL PK
  module_id     BIGINT FK -> education_module
  question      TEXT
  options       JSONB               -- ["보기1","보기2",...]
  answer_index  INT                 -- 0-based 정답. 서버 전용
  explanation   TEXT
  sort_order    INT

education_progress
  id            BIGSERIAL PK
  member_id     BIGINT FK -> app_user
  module_id     BIGINT FK -> education_module
  status        VARCHAR             -- NOT_STARTED | IN_PROGRESS | DONE
  quiz_score    INT                 -- 마지막 퀴즈 정답 수(없으면 NULL)
  quiz_total    INT
  updated_at    TIMESTAMPTZ
  UNIQUE(member_id, module_id)
```

시드: 6개 모듈 + 모듈당 레슨 1~2개 + 퀴즈 3~4문항. 콘텐츠 원문은 프론트 부제·홈 노션(4-7, 4-5, 4-6, 4-8) 및 코퍼스(`ai-coach/data/corpus/04_home`)를 근거로 작성한다.

## API (`/api/v1/education`, 인증 필요)

| 메서드·경로 | 설명 |
|-------------|------|
| `GET /modules` | 6개 모듈 + 회원 진행률(status·score) |
| `GET /modules/{code}` | 모듈 상세: 레슨 본문 + 퀴즈 문항(정답 제외) |
| `POST /modules/{code}/lessons/{lessonId}/complete` | 레슨 완료 → 진행률 IN_PROGRESS |
| `POST /modules/{code}/quiz/submit` | 답안 `{questionId: choiceIndex}` 채점 → 점수·문항별 정오·해설. 통과 시 DONE |

- `progress` 는 첫 접근(모듈 상세 조회 또는 레슨 완료) 시 NOT_STARTED→IN_PROGRESS 로 생성.
- 통과 기준: 정답률 60% 이상이면 모듈 `DONE`. 미달이면 IN_PROGRESS 유지(재응시 가능, 최신 점수로 덮어씀).

## 홈 대시보드 연동

`SettlementDashboardService`(plan 스코프)에 교육 요약(`완료 N / 전체 6`)을 추가한다. 회원 교육 진행률을 읽어 `⑤ 독립 도구` 영역에 노출. (선택 항목 — 대시보드 응답에 `education` 요약 필드 추가.)

## 에러/권한

- 존재하지 않는 module code / lessonId / questionId → 404.
- 다른 회원의 진행률에는 접근 불가(모든 경로가 `@AuthenticationPrincipal` 회원 기준).
- 퀴즈 submit 에 없는 questionId 또는 범위 밖 choiceIndex → 400.

## 테스트

- `EducationServiceTest`(단위): 퀴즈 채점(정답/오답/부분정답·통과 임계값), 진행률 전이(NOT_STARTED→IN_PROGRESS→DONE), 재응시 시 최신 점수 반영.
- `EducationIntegrationTest`(MockMvc + Testcontainers): 모듈 목록·상세(정답 미노출 확인)·레슨 완료·퀴즈 채점·진행률 저장, 타 회원 격리, 404/400.
- `MigrationTest`: `education_*` 테이블 존재 + 시드 개수.

## v1에서 의도적으로 제외

- DSR/DTI 계산기, 이자 시뮬레이션, 상황극/시뮬레이션 엔진 → 후속. v1은 링크/안내로 대체.
- 교육 콘텐츠 CMS(관리자 편집) → 시드로 충분.
