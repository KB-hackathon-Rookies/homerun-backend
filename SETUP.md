# 개발 환경 세팅

## 필요한 것

| | 버전 | 비고 |
|---|---|---|
| JDK | 17 | 없어도 된다. Gradle 이 자동으로 받아온다 |
| Docker | Desktop 또는 Engine | **필수.** DB 와 테스트가 컨테이너로 돈다 |
| Node.js | 20+ | 커밋 훅(husky/commitlint)용 |

JDK 는 로컬에 17이 없어도 `settings.gradle` 의 foojay resolver 가 자동으로 받아온다.
첫 빌드가 조금 오래 걸리는 이유다.

## 1. 클론과 환경변수

```bash
git clone https://github.com/KB-hackathon-Rookies/homerun-backend.git
cd homerun-backend
cp .env.example .env
```

`.env` 의 `POSTGRES_PASSWORD` 는 비워 두면 안 된다. compose 가 바로 에러를 낸다.
로컬에서는 아무 값이나 넣으면 된다.

로컬에 이미 PostgreSQL 이 5432 를 쓰고 있으면 `.env` 의 `POSTGRES_PORT` 를 5433 등으로 바꾼다.

## 2. 커밋 훅 설치

```bash
npm install
```

이걸 안 하면 커밋 메시지 검사와 포맷 검사가 걸리지 않는다. 클론 직후 한 번만 하면 된다.

설치되는 훅:

| 훅 | 하는 일 | 걸리는 시간 |
|---|---|---|
| pre-commit | `./gradlew spotlessCheck` — 포맷 검사 | 초 단위 |
| commit-msg | commitlint — 커밋 메시지 형식 검사 | 즉시 |
| pre-push | `./gradlew build` — 컴파일 + 포맷 + 테스트 | 수십 초 |

## 3. 빌드와 실행

```bash
./gradlew build     # 전부 통과하는지 먼저 확인
./gradlew bootRun   # 앱 실행
```

`bootRun` 은 `compose.yaml` 의 PostgreSQL 을 자동으로 띄우고 종료 시 같이 내린다.
컨테이너를 계속 살려 두고 싶으면 `application.yaml` 에
`spring.docker.compose.lifecycle-management: start-only` 를 준다.

확인:

```bash
curl http://localhost:8080/actuator/health
open http://localhost:8080/swagger-ui.html
```

## 4. 앱까지 컨테이너로 띄우기

```bash
docker compose -f compose.prod.yaml up -d --build
docker compose -f compose.prod.yaml logs -f app
docker compose -f compose.prod.yaml down
```

CD 가 올린 이미지로 띄우려면 태그를 지정한다:

```bash
IMAGE=ghcr.io/kb-hackathon-rookies/homerun-backend:<커밋SHA12> \
  docker compose -f compose.prod.yaml up -d
```

## 자주 막히는 곳

**`Cannot find a Java installation ... languageVersion=17`**
`settings.gradle` 의 foojay resolver 플러그인이 빠졌을 때 난다. 지금 설정에는 들어 있으니
이 에러가 보이면 `settings.gradle` 을 먼저 확인한다.

**테스트가 `Could not find a valid Docker environment` 로 실패**
Docker 가 꺼져 있다. 테스트는 Testcontainers 로 실제 PostgreSQL 을 띄운다.

**`pre-commit` 에서 spotless 에 걸림**
```bash
./gradlew spotlessApply && git add -A
```

**커밋 메시지가 commitlint 에 걸림**
형식은 `<이모지> <타입>: <내용>` 이고 콜론 뒤 공백이 필요하다. 예: `✨ Feat: 회원 가입 API`.
타입 목록은 `commitlint.config.js` 에 있다. `npm run commit` 으로 고르는 게 안전하다.

**포트 충돌**
`.env` 의 `POSTGRES_PORT`, `APP_PORT` 를 바꾼다.

**마이그레이션 체크섬 에러 (`Migration checksum mismatch`)**
이미 적용된 마이그레이션 파일을 수정했을 때 난다. 파일을 원래대로 되돌리고 새 버전을 추가한다.
로컬 DB 를 통째로 밀어도 된다면:
```bash
docker compose down -v && docker compose up -d
```
