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
    @DisplayName("핵심 스키마와 후속 기능 테이블을 합쳐 최소 52개 테이블이 존재한다")
    void should_create_all_tables_when_migrated() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables"
                        + " WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'",
                Integer.class);

        assertThat(count).isGreaterThanOrEqualTo(52);
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
                        "open_banking_connection",
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
    @DisplayName("V6 가 계획 입력 버전과 스냅샷 이력 테이블을 추가한다")
    void should_add_planInputHistory_when_v6IsApplied() {
        assertThat(columnNames("plan_input")).contains("updated_at", "revision", "version");
        assertThat(tableNames()).contains("plan_input_history");
        assertThat(columnNames("plan_input_history"))
                .contains("plan_input_id", "plan_id", "revision", "snapshot", "saved_at");
        assertThat(constraintDefinition("uq_plan_input_plan")).contains("plan_id");
    }

    @Test
    @DisplayName("V8이 로컬 회원의 비밀번호 해시와 이메일 인증 시각을 추가한다")
    void should_add_localEmailAuth_when_v8IsApplied() {
        assertThat(columnNames("app_user")).contains("password_hash", "email_verified_at");
        assertThat(indexNames("app_user")).contains("uq_app_user_local_email");
    }

    @Test
    @DisplayName("V14가 오픈뱅킹 토큰 원문이 아닌 암호문 저장소를 추가한다")
    void should_add_encryptedOpenBankingConnection_whenV14IsApplied() {
        assertThat(columnNames("open_banking_connection"))
                .contains(
                        "member_id",
                        "user_seq_no",
                        "access_token_ciphertext",
                        "refresh_token_ciphertext",
                        "access_token_expires_at")
                .doesNotContain("access_token", "refresh_token");
        assertThat(constraintDefinition("uk_open_banking_connection_member")).contains("member_id");
    }

    @Test
    @DisplayName("V32가 사용자별 최근 금융 스냅샷 조회 인덱스를 추가한다")
    void should_indexLatestFinancialSnapshot_whenV32IsApplied() {
        assertThat(indexNames("financial_snapshot")).contains("idx_financial_snapshot_user_created");
    }

    @Test
    @DisplayName("V20이 전세대출 진단 입력과 금융정보 출처를 추가한다")
    void should_add_jeonseDiagnosisInput_whenV20IsApplied() {
        assertThat(columnNames("plan_input"))
                .contains(
                        "household_homeless",
                        "birth_date",
                        "military_months",
                        "monthly_income",
                        "net_assets",
                        "available_cash",
                        "has_existing_jeonse_loan",
                        "income_source",
                        "asset_source",
                        "financial_data_confirmed");
    }

    @Test
    @DisplayName("V40이 STEP별 진단 입력 체크포인트를 추가한다")
    void should_add_planInputStepCheckpoint_whenV40IsApplied() {
        assertThat(tableNames()).contains("plan_input_step");
        assertThat(columnNames("plan_input_step"))
                .contains("plan_id", "step_code", "status", "completed_at", "updated_at", "version");
        assertThat(constraintDefinition("uq_plan_input_step")).contains("plan_id", "step_code");
    }

    @Test
    @DisplayName("V41이 revision 단위 1루 제출 기록을 추가한다")
    void should_add_firstBaseSubmission_whenV41IsApplied() {
        assertThat(tableNames()).contains("first_base_submission");
        assertThat(columnNames("first_base_submission"))
                .contains("plan_id", "input_revision", "diagnosis_id", "created_at");
        assertThat(constraintDefinition("uq_first_base_submission_revision")).contains("plan_id", "input_revision");
    }

    @Test
    @DisplayName("V21이 매물 후보 분석과 최종 선택 컬럼을 추가한다")
    void should_add_propertyCandidateColumns_whenV21IsApplied() {
        assertThat(columnNames("property"))
                .contains(
                        "legal_district_code",
                        "building_name",
                        "deposit",
                        "is_multi_household",
                        "landlord_tax_unpaid",
                        "is_selected",
                        "analyzed_at");
        assertThat(indexNames("property")).contains("uq_property_selected_per_plan");
    }

    @Test
    @DisplayName("V52가 임대인 협조 컬럼과 CHECK 제약을 추가한다")
    void should_add_landlordConsentColumn_whenV52IsApplied() {
        assertThat(columnNames("property")).contains("landlord_consent");
        assertThat(constraintDefinition("ck_property_landlord_consent")).contains("CONFIRMED", "NOT_ASKED", "REFUSED");
    }

    @Test
    @DisplayName("팩트 레지스트리에 전세대출 진단 기준까지 적용된다")
    void should_seed_config_effective_when_migrated() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM config_effective", Integer.class);

        // V33 이 은행별 금리 8건(FCT-201~208), V36 이 분리지급 연령 2건(FCT-209·210)을 더했다.
        // V51 이 대출보증 한도·비율 4건(FCT-211~214)을 더했다(BR-19).
        // V54 가 부대비용·중개보수·인지세·보증료율 24건(FCT-215~238)을 더했다(BR-08a·BR-21·BR-27).
        assertThat(count).isEqualTo(238);
    }

    @Test
    @DisplayName("V54가 부대비용 계산 수치를 계산 가능한 형태로 심는다")
    void should_seed_ancillaryCostFacts_whenV54IsApplied() {
        // 텍스트만 있던 FCT-097·098 과 달리 value_num 이 있어야 계산에 쓸 수 있다.
        assertThat(numberOf("FCT-222")).isEqualByComparingTo("0.3"); // 중개보수 3구간 요율
        assertThat(numberOf("FCT-229")).isEqualByComparingTo("75000"); // 인지세 3구간 고객부담
        assertThat(numberOf("FCT-237")).isEqualByComparingTo("500000"); // 이사비 기본값
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

    @Test
    @DisplayName("V31이 검수를 마친 최신 버전만 ACTIVE로 승격한다")
    void should_activateOnlyReviewedVersions_whenV31IsApplied() {
        Integer activeCount =
                jdbc.queryForObject("SELECT count(*) FROM policy_rule WHERE status = 'ACTIVE'", Integer.class);
        // V42가 청년미래적금을 DRAFT로 되돌려 8 → 7이 됐다(#175).
        assertThat(activeCount).isEqualTo(7);

        // 승격 대상이 아닌 버전은 여전히 DRAFT다 — 승격이 정확히 지정한 버전에만 적용됐는지 확인한다.
        assertThat(ruleStatus("JEONSE-YOUTH-BEOTIMMOK", 1)).isEqualTo("DRAFT");
        assertThat(ruleStatus("JEONSE-YOUTH-BEOTIMMOK", 3)).isEqualTo("ACTIVE");
        assertThat(ruleStatus("JEONSE-GENERAL-BEOTIMMOK", 2)).isEqualTo("DRAFT");
        assertThat(ruleStatus("JEONSE-GENERAL-BEOTIMMOK", 3)).isEqualTo("ACTIVE");
        assertThat(ruleStatus("JEONSE-SEOUL-INTEREST-SUPPORT", 2)).isEqualTo("DRAFT");
        assertThat(ruleStatus("JEONSE-SEOUL-INTEREST-SUPPORT", 3)).isEqualTo("ACTIVE");
        assertThat(ruleStatus("RETURN-GUARANTEE-HUG", 1)).isEqualTo("ACTIVE");
        assertThat(ruleStatus("RETURN-GUARANTEE-HF", 1)).isEqualTo("ACTIVE");
        assertThat(ruleStatus("RETURN-GUARANTEE-SGI", 1)).isEqualTo("ACTIVE");
        assertThat(ruleStatus("RETURN-GUARANTEE-FEE-SUPPORT", 1)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("V42가 범위에서 뺀 청년미래적금 규칙을 DRAFT로 되돌린다")
    void should_demoteYouthSavingsRule_whenV42IsApplied() {
        // 폐지가 아니라 범위 제외다. policy 행과 팩트는 그대로 남아 되살릴 수 있다(#175).
        assertThat(ruleStatus("YOUTH-FUTURE-SAVINGS", 1)).isEqualTo("DRAFT");

        Integer policyRow =
                jdbc.queryForObject("SELECT count(*) FROM policy WHERE code = 'YOUTH-FUTURE-SAVINGS'", Integer.class);
        assertThat(policyRow).isEqualTo(1);

        // 폐지 상품으로 표시하면 "이제 없는 상품"이라는 거짓 안내가 된다.
        String status =
                jdbc.queryForObject("SELECT status FROM policy WHERE code = 'YOUTH-FUTURE-SAVINGS'", String.class);
        assertThat(status).isNotEqualTo("DISCONTINUED");
    }

    @Test
    @DisplayName("V45가 1루 완료 결과 스냅샷 컬럼을 추가한다")
    void should_addFirstBaseResultSnapshot_whenV45IsApplied() {
        assertThat(columnNames("first_base_submission")).contains("result_snapshot");
    }

    @Test
    @DisplayName("V49가 매물 STEP과 공시가격 출처를 추가한다")
    void should_addPropertyWorkflow_whenV49IsApplied() {
        assertThat(columnNames("property"))
                .contains(
                        "workflow_step",
                        "workflow_status",
                        "workflow_revision",
                        "official_price_year",
                        "official_price_source",
                        "is_non_residential");
    }

    @Test
    @DisplayName("V50이 은행 상담 답변 상태와 상품 분류를 추가한다")
    void should_extendBankConsultationResult_whenV50IsApplied() {
        assertThat(columnNames("bank_consultation")).contains("result_status", "loan_product");
        assertThat(constraintDefinition("ck_bank_consultation_result_status"))
                .contains("POSSIBLE", "DOCUMENT_REVIEW_REQUIRED", "NOT_HEARD");
        assertThat(constraintDefinition("ck_bank_consultation_method")).contains("UNKNOWN");
    }

    @Test
    @DisplayName("V55가 은행 상담 거절 사유 분류 컬럼과 CHECK 제약을 추가한다")
    void should_addBankConsultationRejection_whenV55IsApplied() {
        assertThat(columnNames("bank_consultation"))
                .contains("rejection_stage", "rejection_category", "rejection_note");
        assertThat(constraintDefinition("ck_bank_consultation_rejection_stage"))
                .contains("BANK", "GUARANTEE", "NOT_TOLD");
        assertThat(constraintDefinition("ck_bank_consultation_rejection_category"))
                .contains("SUBJECT_ISSUE", "PROPERTY_ISSUE", "GUARANTEE_ISSUE", "DOCUMENT_ISSUE", "LANDLORD_ISSUE");
    }

    @Test
    @DisplayName("V53이 최종 결정 revision과 2루 제출 스냅샷을 추가한다")
    void should_addSecondBaseSubmission_whenV53IsApplied() {
        assertThat(columnNames("property_decision")).contains("decision_revision");
        assertThat(tableNames()).contains("second_base_submission");
        assertThat(columnNames("second_base_submission"))
                .contains("plan_id", "decision_revision", "result_snapshot", "created_at");
        assertThat(constraintDefinition("uq_second_base_submission_revision")).contains("plan_id", "decision_revision");
    }

    private String ruleStatus(String policyCode, int version) {
        return jdbc.queryForObject(
                "SELECT status FROM policy_rule WHERE version = ?"
                        + " AND policy_id = (SELECT id FROM policy WHERE code = ?)",
                String.class,
                version,
                policyCode);
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

    private java.math.BigDecimal numberOf(String factCode) {
        return jdbc.queryForObject(
                "SELECT value_num FROM config_effective WHERE fact_code = ?", java.math.BigDecimal.class, factCode);
    }

    private String constraintDefinition(String constraintName) {
        return jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?", String.class, constraintName);
    }

    private java.util.List<String> indexNames(String table) {
        return jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND tablename = ?", String.class, table);
    }
}
