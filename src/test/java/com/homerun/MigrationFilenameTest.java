package com.homerun;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 마이그레이션 파일 이름만 본다. DB 를 띄우지 않는다.
 *
 * <p>버전이 겹치면 Flyway 는 기동에서 막지만, 그때는 이미 dev 가 서지 않는 상태다. 2026-09-03
 * 에 실제로 그렇게 됐다 — 브랜치에서 V12 를 V14 로 옮기면서 원본을 지우지 않아 V12 가 두
 * 개가 됐다. 컨테이너 없이 즉시 도는 검사로 앞에서 잡는다.
 */
class MigrationFilenameTest {

    private static final Pattern VERSIONED = Pattern.compile("^V(\\d+)__[a-z0-9_]+\\.sql$");

    private static List<String> migrationFileNames() throws IOException, URISyntaxException {
        Path dir = Path.of(Objects.requireNonNull(
                        MigrationFilenameTest.class.getClassLoader().getResource("db/migration"),
                        "db/migration 을 클래스패스에서 찾지 못했다")
                .toURI());
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".sql"))
                    .sorted()
                    .toList();
        }
    }

    @Test
    @DisplayName("같은 버전 번호를 쓰는 마이그레이션이 없다")
    void should_not_reuse_version_number() throws Exception {
        Map<String, List<String>> byVersion = migrationFileNames().stream().collect(Collectors.groupingBy(name -> {
            Matcher matcher = VERSIONED.matcher(name);
            return matcher.matches() ? matcher.group(1) : "";
        }));

        List<String> duplicated = byVersion.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> "V%s → %s".formatted(entry.getKey(), entry.getValue()))
                .toList();

        assertThat(duplicated).isEmpty();
    }

    @Test
    @DisplayName("마이그레이션 이름이 V<번호>__<소문자_이름>.sql 형식이다")
    void should_follow_naming_convention() throws Exception {
        assertThat(migrationFileNames())
                .allMatch(name -> VERSIONED.matcher(name).matches());
    }
}
