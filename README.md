# homerun-backend

Spring Boot 4.1 / Java 17 / PostgreSQL 17 / Flyway 기반 백엔드.

## 빠르게 시작하기

```bash
cp .env.example .env      # POSTGRES_PASSWORD 는 반드시 채운다
npm install               # husky 훅 설치
./gradlew build           # 컴파일 + 포맷 검사 + 테스트
./gradlew bootRun         # 앱 실행 (PostgreSQL 은 알아서 뜬다)
```

`bootRun` 하면 `spring-boot-docker-compose` 가 `compose.yaml` 의 PostgreSQL 을 자동으로
띄우고 접속 정보를 앱에 꽂아 준다. 따로 `docker compose up` 할 필요가 없다.

자세한 건 [SETUP.md](SETUP.md).

## 확인

| | |
|---|---|
| API 문서 | http://localhost:8080/swagger-ui.html |
| 헬스체크 | http://localhost:8080/actuator/health |

## 구조

```
src/main/java/com/homerun/          애플리케이션 코드
src/main/resources/
  application.yaml                  공통 설정
  application-docker.yaml           컨테이너로 띄울 때만 덮어쓰는 설정
  db/migration/                     Flyway 마이그레이션 (README 참고)
src/test/java/com/homerun/
  TestcontainersConfiguration.java  테스트용 PostgreSQL 컨테이너
compose.yaml                        로컬 개발용 의존 서비스 (앱 없음)
compose.prod.yaml                   앱까지 묶어서 띄울 때
```

## 자주 쓰는 명령

| 명령 | 하는 일 |
|---|---|
| `./gradlew build` | 컴파일 + 포맷 검사 + 테스트. 푸시 전에 도는 것과 같다 |
| `./gradlew test` | 테스트만. Docker 가 떠 있어야 한다 |
| `./gradlew spotlessApply` | 포맷 검사에 걸렸을 때 고치는 명령 |
| `npm run commit` | 커밋 타입을 골라서 컨벤션에 맞는 메시지로 커밋 |
| `docker compose -f compose.prod.yaml up -d --build` | 앱까지 컨테이너로 띄우기 |

## 규칙

- **스키마 변경은 전부 Flyway 마이그레이션으로.** `ddl-auto: validate` 라서 엔티티만
  추가하면 앱이 부팅되지 않는다. → [db/migration/README.md](src/main/resources/db/migration/README.md)
- **테스트는 진짜 PostgreSQL 에서 돈다.** Testcontainers 가 빈 DB 를 띄우고 마이그레이션을
  처음부터 적용한다. 그래서 테스트를 돌리려면 Docker 가 실행 중이어야 한다.
- **커밋 메시지는 commitlint 가 검사한다.** 형식은 `✨ Feat: 내용`. `npm run commit` 이 편하다.
- **포맷은 spotless 가 강제한다.** 커밋할 때 검사하고, 어긋나면 `./gradlew spotlessApply`.

## 브랜치와 배포

- 기본 브랜치는 `dev`. 기능 브랜치 → `dev` PR → 리뷰 후 머지.
- `main`, `dev` 로의 push 와 모든 PR 에서 CI(`.github/workflows/ci.yml`)가 돈다.
- `main` 머지 시 CD(`.github/workflows/cd.yml`)가 이미지를 만들어 GHCR 에 올린다.
  이미지 태그는 커밋 SHA 앞 12자리다.
  > 배포 대상 서버는 아직 정해지지 않았다. 정해지면 `cd.yml` 의 `deploy` 잡을 채운다.
