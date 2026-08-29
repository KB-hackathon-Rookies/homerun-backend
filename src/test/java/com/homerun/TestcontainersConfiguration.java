package com.homerun;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 테스트용 PostgreSQL 컨테이너.
 *
 * <p>compose.yaml 과 같은 이미지를 쓴다. 버전을 올릴 때는 두 곳을 같이 올려야 한다.
 *
 * <p>withReuse 는 쓰지 않는다. 재사용을 켜면 이전 테스트가 남긴 스키마 위에 Flyway 가
 * 얹히면서, 마이그레이션을 새로 추가했을 때만 나는 오류를 로컬에서 놓치게 된다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    }
}
