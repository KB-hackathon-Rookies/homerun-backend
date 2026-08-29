# ── build ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# 의존성만 먼저 받아 레이어로 굳힌다. src 만 바뀐 빌드는 이 레이어를 재사용한다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src src
# 이미지 빌드에서 테스트까지 돌리지 않는다. 테스트는 Testcontainers 를 쓰는데
# 빌드 컨테이너 안에는 도커 데몬이 없다. 테스트는 CI 가 책임진다.
RUN ./gradlew bootJar --no-daemon -x test

# 레이어 분리: 라이브러리(거의 안 변함)와 애플리케이션 클래스(매번 변함)를 나눈다.
# 배포할 때마다 수십 MB 짜리 의존성 레이어를 다시 올리지 않게 된다.
RUN java -Djarmode=tools -jar build/libs/*.jar extract --layers --launcher --destination extracted

# ── runtime ────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre
WORKDIR /app

# root 로 돌리지 않는다. 컨테이너가 뚫렸을 때 할 수 있는 일을 줄인다.
RUN useradd --system --create-home --uid 10001 spring

COPY --from=build --chown=spring:spring /workspace/extracted/dependencies/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/application/ ./

USER spring
EXPOSE 8080

# 레이어로 풀어 둔 상태라 실행 가능한 jar 파일이 없다. Boot 의 런처를 직접 띄운다.
# 컨테이너 메모리 제한을 JVM 이 인식하게 둔다. -Xmx 를 박아 두면 인스턴스 크기를
# 바꿀 때마다 이미지를 다시 만들어야 한다.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-cp", ".", "org.springframework.boot.loader.launch.JarLauncher"]
