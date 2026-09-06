# 알림(FCM + Redis Stream) & AI 코치(RAG) 기본 아키텍처 설계

- 작성일: 2026-09-06
- 대상 저장소: homerun-backend (Spring Boot 4.1 / Java 17 / PostgreSQL / Redis / Flyway)
- 배경: 본선 2026.09.08~09.10. 이틀 내 데모 가능한, 붙이기 쉬운 골격을 목표로 한다.
- 레퍼런스 패턴
  - 알림: `BellongBellong/Jaedaero_backend` — Redis Streams 아웃박스 + FCM (MyBatis → **JPA로 각색**)
  - AI 코치: `kb-private-diary/military-finance-roadmap` — 독립 FastAPI(8000) + ChromaDB, Spring과 JWT(HS256) 공유

## 결정 사항 (사용자 확정)

1. 두 서브시스템 모두 견고하게 구현한다(둘 다 데모).
2. LLM/임베딩 제공자: **OpenAI API** (생성 `gpt-4o-mini`, 임베딩 `text-embedding-3-small`).
3. RAG 코퍼스: 실제 문서는 나중에 주입. 지금은 **인제스트 파이프라인 + 샘플 1건 + `data/corpus/README`** 까지.
4. 알림 대표 트리거: **계약/신청 마감 임박**(스케줄러 기반, D-7·D-3·D-1).
5. 코치 사용자 맥락: 코치는 무상태. 요청마다 **사용자의 현재 단계(`stage`)** 와 선택적 컨텍스트 요약을 본문으로 받아 단계에 맞는 답변을 준다(stage-aware). 결합도를 낮게 유지.

## 확정값 (코드 실측 기반, 2026-09-06)

앞서 오픈 이슈였던 항목을 저장소 실측으로 확정한다.

1. **JWT `sub` 실제 값** — `JwtTokenProvider.createAccessToken`은 `sub = String.valueOf(member.getId())`(숫자 member id 문자열) + 커스텀 클레임 `provider`(예: LOCAL/GOOGLE/KAKAO)를 담는다. 서명은 `Keys.hmacShaKeyFor(JWT_SECRET.getBytes(UTF-8))`이고 시크릿은 32바이트 이상 강제 → **HS256**. `exp` 포함.
   - → FastAPI `core/security.py`: 같은 `JWT_SECRET`으로 **HS256** 검증, `sub`를 정수 member id로 파싱. 별도 claim은 불필요(식별만).
2. **계약/신청 마감 필드** — `LeaseContract`에는 실제 날짜가 다수 존재: `balanceDate`(잔금일), `moveInDate`(입주일), `leaseEndDate`(전세 만기), `confirmedDateAt`, `renewalNotifiedAt` 등. **그러나 `PolicyApplication`에는 마감일 필드가 없다**(가진 것은 `status`, `submittedAt`, `resultAt` = Instant). policy 도메인에도 신청 접수 마감 데이터가 없다.
   - **문서 간 충돌 보고**: 사용자 요청("계약/신청 마감 임박")과 실제 데이터가 어긋난다. → v1 스케줄러는 **계약 마감 = 실데이터**로 구현하고(대표: `balanceDate` D-7·D-3·D-1, 확장으로 `leaseEndDate` 갱신창), **신청 "마감"은 데이터가 없어** 접수 마감 대신 **정체 신청 넛지**(status=PREPARING가 오래 머무는 경우, `submittedAt is null`+생성 후 경과일)로 대체한다. 진짜 신청 접수 마감이 필요하면 policy 마스터에 마감일 컬럼을 추가해야 함(오픈 의존성).
   - 마감 소스는 `DeadlineSource` 전략으로 분리해 (a) `ContractDeadlineSource`, (b) `StaleApplicationSource`를 각각 꽂는다. 미래의 정책 접수 마감도 같은 인터페이스로 확장.
3. **Flyway 다음 번호** — 브랜치 작업 중 dev 가 V44 까지 진전(V41~V44 추가)하여 충돌을 피해 **`V45__create_notification_tables.sql`**로 부여했다.
4. **스케줄러 cron** — 프로젝트에 기존 `@Scheduled`/`@EnableScheduling` 없음 → 부트 클래스나 config에 `@EnableScheduling` 추가 필요. 값:
   - 마감 스캔: `@Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")` (매일 09:00 KST).
   - 재처리 리퍼: `@Scheduled(fixedDelayString = "${notification.reaper.fixed-delay:60000}")` (60s 주기), `min-idle` 기본 5분.
   - 모두 프로퍼티로 오버라이드 가능하게 노출.
5. **Redis 버전** — compose가 `redis:7.4-alpine`. 스트림(≥5.0)·`XAUTOCLAIM`(≥6.2) 모두 충족. Spring은 기존 `spring.data.redis`(Lettuce, host/port/password) 재사용. **최소 요구: Redis ≥ 6.2**(XAUTOCLAIM). 신규 Redis 인프라 불필요.
6. **Firebase Admin 고정 버전** — `implementation 'com.google.firebase:firebase-admin:9.4.3'` 로 핀 고정(구현 착수 시 Maven Central 최신 9.4.x patch 재확인). BOM 관리 대상 아님(직접 버전 명시).
7. **compose 네트워크/환경변수 구조** — 명시적 `networks` 없음 → 기본 bridge, **서비스명 DNS**로 통신(`postgres`, `redis`). 환경변수 규약: 필수는 `${VAR:?Set VAR in .env}`, 선택은 `${VAR:-default}`. `app`은 `SPRING_PROFILES_ACTIVE=docker`, `DB_HOST=postgres`, `REDIS_HOST=redis`. → 신규:
   - `app` 블록에 FCM 환경변수 추가: `FCM_ENABLED=${FCM_ENABLED:-false}`, `FIREBASE_CREDENTIALS=${FIREBASE_CREDENTIALS:-}`.
   - `ai-coach` 서비스 추가(같은 기본 bridge, 별도 network 불필요): 포트 `${AI_COACH_PORT:-8000}:8000`, env `OPENAI_API_KEY=${OPENAI_API_KEY:?...}`, `JWT_SECRET=${JWT_SECRET:?...}`(app과 동일 값 공유), `OPENAI_CHAT_MODEL=${OPENAI_CHAT_MODEL:-gpt-4o-mini}`, `OPENAI_EMBEDDING_MODEL=${OPENAI_EMBEDDING_MODEL:-text-embedding-3-small}`, `CHROMA_DIR=${CHROMA_DIR:-/data/chroma}`, chroma 볼륨 마운트.
   - `.env.example`에 위 키 추가.

## 목표 / 비목표

목표

- 도메인 이벤트/스케줄에서 FCM 푸시까지 이어지는 **비동기·재시도 가능한** 알림 파이프라인.
- 정책/계약 문서 근거를 붙여 답하는 **stage-aware RAG 코치** 서비스 골격.
- 두 서브시스템 모두 로컬(compose.yaml) + 배포(compose.prod.yaml)에서 기동.

비목표(이번 범위 밖)

- 실제 코퍼스 적재, 프롬프트 정교화, 대화 히스토리/멀티턴 세션.
- 알림 화면 UI, 사용자별 알림 설정(수신 거부 등)의 정교한 정책.
- 코치의 Spring 콜백 기반 자동 컨텍스트 수집(A안 유지 — 프론트/Spring이 본문에 담아 전달).

---

## 아키텍처 개요

```
homerun-frontend
   │ REST(JWT)                         │ REST(같은 JWT HS256)
   ▼                                    ▼
Spring Boot(8080)                   FastAPI ai-coach(8000)
 ├ domain/notification               app/routers/coach
 │  ├ DeviceToken(JPA)               app/services/rag
 │  ├ Notification(inbox/outbox)     app/services/vectorstore ─▶ ChromaDB(persistent volume)
 │  ├ NotificationEventPublisher ──▶ Redis Stream(notifications:stream)
 │  └ NotificationStreamListener ──▶ FCM(Firebase Admin)          │
 └ scheduler(계약/신청 마감 스캔)                                    ▼
        │                                                     OpenAI API
        ▼                                              (embeddings + gpt-4o-mini)
   Postgres / Redis
```

두 서비스는 **같은 JWT 시크릿(HS256)** 을 공유해 프론트가 8000을 직접 호출한다. Spring↔FastAPI 직접 호출은 이번 범위에서 없음(느슨한 결합).

---

## 서브시스템 1 — 알림 (Spring, `com.homerun.domain.notification`)

### 패키지 구조

```
domain/notification/
 ├ controller/NotificationController
 ├ service/
 │   ├ NotificationEventPublisher      # 프로듀서: DB 저장 + XADD
 │   ├ NotificationStreamListener      # 컨슈머: 스트림 → FCM (수동 ACK)
 │   ├ NotificationRetryReaper         # XAUTOCLAIM 으로 PEL 잔류분 명시적 재처리
 │   ├ NotificationCommandService      # 인박스 읽음 처리 등
 │   └ DeviceTokenService              # 토큰 등록/갱신/조회
 ├ scheduler/DeadlineNotificationScheduler
 ├ entity/{DeviceToken, Notification}
 ├ repository/{DeviceTokenRepository, NotificationRepository}
 ├ dto/request, dto/response
 └ type/{NotificationType, NotificationStatus, DevicePlatform}

global/external/fcm/
 ├ FcmSender                           # Firebase Admin 래퍼(인터페이스)
 ├ FirebaseFcmSender                   # 실제 구현
 └ NoOpFcmSender                       # 키 없을 때/테스트용 폴백
global/config/
 ├ FirebaseConfig                      # FirebaseApp 초기화(서비스계정 JSON)
 └ RedisStreamConfig                   # 시작 시 컨슈머 그룹 생성(ApplicationRunner)
```

### 데이터 모델 (Flyway 마이그레이션 신규)

- `device_token`: `id`, `member_id`(FK), `token`(unique), `platform`(ANDROID/IOS/WEB), `created_at`, `last_seen_at`
- `notification`: `id`, `member_id`(FK), `type`, `title`, `body`, `data`(JSONB), `status`(PENDING/SENT/FAILED/READ), `retry_count`(기본 0, 리퍼 재시도 상한 판정용), `dedup_key`(unique, nullable), `created_at`, `sent_at`, `read_at`
  - `dedup_key` = `{memberId}:{type}:{refId}:{bucket}` — 마감 알림 중복발송 방지. 유니크 제약으로 스케줄러 재실행에도 1회만.

마이그레이션 파일: `src/main/resources/db/migration/V45__create_notification_tables.sql` (dev 가 V44 까지 진전해 V45 로 부여).

### 흐름

1. **프로듀서** (`NotificationEventPublisher.publish(memberId, type, payload)`)
   - `dedup_key`가 있으면 유니크 위반 시 조용히 skip(이미 예약됨).
   - `notification` row 저장(status=PENDING).
   - `XADD notifications:stream * notificationId={id}` (본문은 DB가 원본, 스트림은 트리거만 실어 가볍게).
2. **컨슈머** (`NotificationStreamListener`, 컨슈머 그룹 `notification-workers`, `autoAck=false` 수동 ACK)
   - 구현: `StreamMessageListenerContainer`(푸시) 대신 **스케줄 폴러**(`NotificationStreamConsumer`, `@Scheduled` 1s)로 컨슈머 그룹에서 `XREADGROUP >` 소비. 직렬화·제네릭을 단순화하고 재시도 경로를 명시적으로 통제하기 위함. 마감 알림은 준실시간이면 충분하다.
   - `notificationId`로 row 로드 → member의 `device_token` 조회 → `FcmSender.send(tokens, title, body, data)`.
   - 성공: status=SENT, `sent_at` 기록, `XACK`.
   - 실패: `XACK` 하지 않고 두면 해당 엔트리는 PEL(Pending Entries List)에 남을 뿐 **자동으로 재전달되지 않는다.** 따라서 재시도는 아래 리퍼가 명시적으로 수행한다.
3. **재처리 리퍼** (`NotificationRetryReaper`, `@Scheduled`)
   - 주기적으로 `XAUTOCLAIM notifications:stream notification-workers <consumer> <min-idle> 0` 로 **일정 시간 이상 idle 상태로 PEL에 남은** 엔트리를 회수해 재전송을 시도한다.
   - 엔트리별 재시도 횟수(전송 시도 카운트: `notification.retry_count` 또는 `XPENDING` 의 delivery count)로 상한 관리.
   - 상한 초과 시: `XACK` 로 스트림에서 제거하고 row status=FAILED 로 확정(사실상 DLQ 종료). 무한 적체를 여기서 끊는다.
   - "ACK 안 함 = 자동 재시도" 가 아님을 전제로, PEL은 **명시적 회수 대상**으로만 다룬다.
4. **스케줄러** (`DeadlineNotificationScheduler`, `@Scheduled(cron="0 0 9 * * *", zone="Asia/Seoul")`)
   - 부트 클래스(또는 config)에 `@EnableScheduling` 추가(현재 프로젝트에 스케줄링 없음).
   - `DeadlineSource` 전략들을 순회해 마감/넛지 대상을 수집한다.
     - `ContractDeadlineSource`: `LeaseContract.balanceDate` 기준 D-7·D-3·D-1(확장: `leaseEndDate` 갱신창). 실데이터.
     - `StaleApplicationSource`: `PolicyApplication.status=PREPARING` 이 오래 정체된 건(생성 후 경과일 기준) 넛지 — **신청 접수 마감 데이터가 없어서** 대체 신호. 진짜 접수 마감 컬럼이 생기면 `PolicyDeadlineSource`로 교체/추가.
   - 각 대상마다 `publish(...)` 호출. `bucket`=기준일+오프셋으로 dedup(중복발송 방지).
   - contract/application 도메인에 마감/정체 대상을 조회하는 read 메서드를 최소한으로 추가.

### API

- `POST /api/v1/notifications/tokens` — 디바이스 토큰 등록/갱신(현재 로그인 member). body: `{token, platform}`
- `GET /api/v1/notifications?unreadOnly=&limit=` — 내 알림 목록
- `PATCH /api/v1/notifications/{id}/read` — 읽음 처리
- (참고) member 테이블명은 실제로 `app_user` 다. FK 는 `app_user(id)` 를 참조한다.
- 응답은 기존 `global/response` 공통 포맷을 따른다.

### 설정 / 의존성

- `build.gradle`: `implementation 'com.google.firebase:firebase-admin:9.4.3'` (착수 시 Maven Central 최신 9.4.x patch 확인).
- 환경변수: `FCM_ENABLED`(기본 false), `FIREBASE_CREDENTIALS`(서비스계정 JSON 경로 또는 base64). 키 없으면 `NoOpFcmSender`로 부팅 성공(데모/로컬 안전).
- Redis: 기존 `spring-boot-starter-data-redis`(Lettuce) 재사용, `spring.data.redis` 설정 그대로. 최소 Redis ≥ 6.2(XAUTOCLAIM). 스트림 키/그룹명/리퍼 주기·min-idle을 `application.yaml` 프로퍼티(`notification.*`)로 노출.

### 테스트 (프로젝트 규약: 진짜 Postgres + Redis Testcontainer)

- `DeviceTokenService`: 토큰 upsert(같은 토큰 재등록 시 last_seen 갱신).
- `NotificationEventPublisher`: dedup_key 유니크 위반 시 중복 저장/발행 안 함.
- `NotificationStreamListener`: `FcmSender`는 Mockito mock. 성공→SENT+XACK. 실패→XACK 안 함, status는 PENDING 유지(즉시 FAILED로 바꾸지 않음), 엔트리는 PEL에 남는다.
- `NotificationRetryReaper`: XAUTOCLAIM으로 회수한 엔트리 재전송 성공→SENT+XACK. delivery count 상한 초과→XACK+FAILED로 종료(재적체 안 됨).
- `DeadlineNotificationScheduler`: D-7/D-3/D-1 경계값(`<`/`<=`) 테스트. 같은 날 두 번 실행해도 1건만.

---

## 서브시스템 2 — AI 코치 (독립 FastAPI, 저장소 루트 `ai-coach/`)

### 디렉터리 구조

```
ai-coach/
 ├ app/
 │   ├ main.py                     # FastAPI 앱, 라우터 등록, CORS
 │   ├ core/
 │   │   ├ config.py               # pydantic-settings: OPENAI_API_KEY, JWT_SECRET, CHROMA_DIR ...
 │   │   └ security.py             # Spring 발급 JWT(HS256) 검증 의존성
 │   ├ schemas/coach.py            # AskRequest / AskResponse / Source
 │   ├ routers/
 │   │   ├ coach.py                # POST /coach/ask
 │   │   └ health.py               # GET /health
 │   └ services/
 │       ├ embeddings.py           # OpenAI text-embedding-3-small
 │       ├ vectorstore.py          # Chroma PersistentClient, stage IN {현재,ALL} 메타 필터 검색
 │       └ rag.py                  # retrieve → 프롬프트 → gpt-4o-mini → 답변+근거
 ├ scripts/ingest.py              # 코퍼스 → 청킹 → 임베딩 → Chroma upsert (CLI)
 ├ data/corpus/                   # 실제 문서 주입 위치(지금은 README + 샘플 1건)
 │   ├ README.md
 │   └ sample_youth_jeonse.md
 ├ data/chroma/                   # persistent 벡터 저장(볼륨, .gitignore)
 ├ requirements.txt
 ├ Dockerfile
 └ .env.example
```

### stage-aware RAG

- 단계 어휘는 Spring `PlanStage` 와 일치: `BENCH / FIRST / SECOND / THIRD / HOME`. 여기에 단계 무관 공통 문서용 값 `ALL` 을 둔다.
- 코퍼스 문서는 인제스트 시 메타데이터 `stage`(해당 단계 하나 또는 `ALL`), `title`, `source` 태깅.
- `/coach/ask` 처리:
  1. JWT 검증(member 식별만, 데이터는 본문 신뢰).
  2. 질문 임베딩 → Chroma에서 **`stage IN {현재 stage, "ALL"}`** 메타 필터 + 유사도 top-k 검색. (현재 단계 전용 문서 + 전 단계 공통 문서를 함께 후보로. 필터를 완화/제거하는 폴백은 두지 않는다.)
  3. 단계별 시스템 프롬프트(페르소나) 선택 + 검색 청크로 컨텍스트 구성.
  4. gpt-4o-mini 호출 → **답변 텍스트만** 생성.
  5. **`sources`는 LLM 출력이 아니라 2단계 retriever가 반환한 히트의 메타데이터(title/source/snippet)로 코드가 직접 구성**한다. LLM에게 출처를 물어보지 않는다(출처 환각 방지). 응답의 answer와 sources는 서로 다른 출처를 갖는다.

### API

- `POST /coach/ask`
  - 요청: `{ "question": str, "stage": "FIRST"|..., "context": {optional 요약 필드들} }`
  - 응답: `{ "answer": str, "stage": str, "sources": [{title, source, snippet}] }`
- `GET /health` — 라이브니스(키/Chroma 상태 요약).

### 인증

- Spring과 **같은 `JWT_SECRET`(HS256)**. FastAPI는 서명·만료만 검증하고 `sub`(memberId)만 사용.
- CORS: 프론트 오리진 허용.

### 배포 / 설정

- `compose.prod.yaml`에 `ai-coach` 서비스 추가: 포트 8000, `data/chroma`를 볼륨으로, env `OPENAI_API_KEY`/`JWT_SECRET`/`CHROMA_DIR`.
- 로컬은 선택적으로 compose에 포함하거나 `uvicorn`으로 직접 실행(README에 명시).
- `.env.example` / `compose.prod.yaml` 환경변수에 `OPENAI_API_KEY` 등 추가.

### 인제스트 파이프라인

- `python -m scripts.ingest --path data/corpus` 로 실행.
- md/pdf 로드 → 문자 기준 청킹(오버랩) → 임베딩 → Chroma upsert(문서 해시로 idempotent).
- 코퍼스가 비어도 샘플 1건으로 end-to-end 검증 가능.

### 테스트(경량)

- `rag.py`: OpenAI/Chroma를 mock/fake로 주입. (a) 검색 결과가 프롬프트에 포함되는지, (b) `sources`가 **retriever 히트 메타데이터에서** 그대로 만들어지는지(LLM 출력과 무관하게 — LLM이 다른 출처를 말해도 sources는 retriever 히트로 고정), (c) stage 필터가 `{현재, ALL}` 로 걸리는지 검증.
- `security.py`: Spring이 만든 것과 같은 방식의 HS256 토큰 검증 성공/만료 실패.
- 인제스트: 샘플 1건 → 컬렉션 count 증가, stage 메타 존재.

---

## 크로스커팅 / 리스크

- **JWT 시크릿 공유**: Spring `JWT_SECRET`을 FastAPI도 읽는다. 실측 완료 — HS256, `sub`=member id 문자열, `exp` 포함(확정값 1번). FastAPI는 같은 시크릿·HS256으로 검증하고 `sub`만 사용.
- **FCM 자격증명 부재 데모**: `FCM_ENABLED=false` + `NoOpFcmSender`로 파이프라인(스트림·인박스)만으로도 데모 가능하게.
- **OpenAI 비용/지연**: 코치는 동기 호출. 타임아웃·에러 시 사용자 친화 메시지.
- **재시도는 자동이 아니다**: ACK 안 함 = PEL 잔류일 뿐 자동 재전달 없음. 재시도는 `NotificationRetryReaper`(XAUTOCLAIM)로 명시적으로 수행하며, delivery count 상한 초과분은 XACK+FAILED로 종료해 무한 적체를 끊는다.
- **마이그레이션 번호 충돌**: 기존 최신 V 번호 확인 후 부여.

## 오픈 이슈

앞선 4개 오픈 이슈는 **확정값** 절에서 코드 실측으로 해소했다. 남은 항목:

1. **(데이터 의존성) 정책 신청 접수 마감**: `PolicyApplication`/policy에 접수 마감일이 없어 v1은 정체 넛지로 대체. 진짜 접수 마감 알림이 제품 요구면 policy 마스터에 마감일 컬럼 + 시드를 추가해야 함(별도 마이그레이션·별도 작업).
2. **로컬 compose에 ai-coach 포함 여부**: 배포(compose.prod.yaml)에는 포함 확정. 로컬(compose.yaml)은 무거워질 수 있어 기본 제외 + README에 `uvicorn` 직접 실행 안내를 기본으로 하되, 원하면 로컬에도 추가 가능(구현 시 사용자 확인).
3. **`StaleApplicationSource` 정체 기준일수**(예: PREPARING 14일)와 마감 오프셋(D-7·D-3·D-1) 최종 수치 — 구현 시 확정.
