package com.homerun;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 마이그레이션이 빈 PostgreSQL 에 처음부터 적용되는지 검증한다.
 *
 * <p>contextLoads 만으로도 Flyway 실패는 잡히지만, 테이블이 몇 개 생겼는지까지는 보지 않는다.
 * 마이그레이션을 추가하면서 CREATE TABLE 을 빠뜨리거나 시드가 일부만 들어가는 경우를
 * 여기서 잡는다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MigrationTest {

    private final JdbcTemplate jdbc;

    MigrationTest(@Autowired DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    @DisplayName("V1 이 47개 테이블을, V3 가 refresh_token 을 더해 48개가 된다")
    void should_create_all_tables_when_migrated() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables"
                        + " WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'",
                Integer.class);

        assertThat(count).isEqualTo(48);
    }

    @Test
    @DisplayName("도메인 핵심 테이블이 존재한다")
    void should_create_core_tables_when_migrated() {
        assertThat(tableNames())
                .contains(
                        "app_user",
                        "plan",
                        "diagnosis",
                        "policy",
                        "policy_rule",
                        "policy_verdict",
                        "verdict_basis",
                        "property",
                        "lease_contract",
                        "consent_token",
                        "plan_step",
                        "deadline",
                        "knowledge_card",
                        "config_effective");
    }

    @Test
    @DisplayName("verdict_basis 는 충족 여부만 저장하고 금액 컬럼을 갖지 않는다")
    void should_not_have_amount_column_in_verdict_basis() {
        // SEC-01-01 Zero-retention. 소득 상세값을 저장하는 순간 원칙이 깨진다.
        assertThat(columnNames("verdict_basis")).contains("is_met").doesNotContain("amount", "income", "value_num");
    }

    @Test
    @DisplayName("consent_token 은 토큰 원문 컬럼을 갖지 않는다")
    void should_store_only_hash_in_consent_token() {
        // SEC-01-03 동의 토큰은 해시로만 저장한다.
        assertThat(columnNames("consent_token")).doesNotContain("token", "raw_token");
    }

    @Test
    @DisplayName("V4 가 계획 진행 상태의 버전과 마지막 위치 컬럼을 추가한다")
    void should_add_plan_progress_columns_when_v4IsApplied() {
        assertThat(columnNames("plan")).contains("last_location_code", "rule_version", "version");
        assertThat(columnNames("plan_step")).contains("updated_at", "version");
        assertThat(constraintDefinition("ck_step_status")).contains("RECALC_REQUIRED");
    }

    @Test
    @DisplayName("V5 가 필수 약관 두 버전과 사용자별 동의 유일 제약을 추가한다")
    void should_add_requiredTerms_when_v5IsApplied() {
        Integer requiredTerms = jdbc.queryForObject(
                "SELECT count(*) FROM terms WHERE is_required = true AND code IN ('SERVICE_TERMS', 'PRIVACY_POLICY')",
                Integer.class);

        assertThat(requiredTerms).isEqualTo(2);
        assertThat(constraintDefinition("uq_user_agreement_user_terms")).contains("user_id", "terms_id");
    }

    @Test
    @DisplayName("V2 가 팩트 레지스트리 141행을 넣는다")
    void should_seed_config_effective_when_migrated() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM config_effective", Integer.class);

        assertThat(count).isEqualTo(141);
    }

    @Test
    @DisplayName("판정에 쓸 수 없는 수치가 확정 상태로 들어가 있지 않다")
    void should_not_mark_unresolved_facts_as_confirmed() {
        // FCT-004 순자산 기준, FCT-009 버팀목 한도. 값이 정해지기 전까지 CONFLICT 여야 한다.
        assertThat(confidenceOf("FCT-004")).isEqualTo("CONFLICT");
        assertThat(confidenceOf("FCT-009")).isEqualTo("CONFLICT");
    }

    @Test
    @DisplayName("한도·기준 금액은 원 단위로 정규화돼 있다")
    void should_normalize_money_to_won() {
        // 3억원을 3 으로, 5,000만원을 5000 으로 넣으면 판정이 통째로 어긋난다.
        // 발급 수수료(분류 '비용')는 실제로 수백 원이라 대상에서 뺀다.
        Integer suspicious = jdbc.queryForObject(
                "SELECT count(*) FROM config_effective"
                        + " WHERE unit = '원' AND category <> '비용'"
                        + " AND value_num IS NOT NULL AND value_num < 10000",
                Integer.class);

        assertThat(suspicious).isZero();
    }

    private java.util.List<String> tableNames() {
        return jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);
    }

    private java.util.List<String> columnNames(String table) {
        return jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns"
                        + " WHERE table_schema = 'public' AND table_name = ?",
                String.class,
                table);
    }

    private String confidenceOf(String factCode) {
        return jdbc.queryForObject(
                "SELECT confidence FROM config_effective WHERE fact_code = ?", String.class, factCode);
    }

    private String constraintDefinition(String constraintName) {
        return jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?", String.class, constraintName);
    }
}
