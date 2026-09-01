-- 홈런(HomeRun) 초기 스키마 — PostgreSQL 17
-- 근거: 요구사항 정의 시트(243행) · 팩트 레지스트리(141행) · 플로우 정의 · API 명세서
--
-- ERDCloud 사용법: 파일 맨 아래 "-- === 인덱스" 주석 앞까지만 잘라서 Import 에 붙여넣는다.
--   CREATE TABLE 만 있고 인덱스·주석·CHECK 은 뒤로 뺐다(파서가 걸리지 않게).
--
-- 규칙
--   · 금액은 원 단위 BIGINT. 소수 금액이 없어 NUMERIC 을 쓰지 않는다.
--   · 비율·금리는 NUMERIC(6,3) — 3.375% 를 손실 없이 담는다.
--   · 매년 바뀌는 수치는 컬럼 기본값으로 박지 않고 config_effective 로 뺀다.
--   · 판정 근거에는 소득 "금액"이 아니라 충족 여부(boolean)만 남긴다(SEC-01-02).
--     금액은 사용자 본인 대시보드용 financial_snapshot 에만 둔다.
--   · 상태값은 ENUM 타입 대신 VARCHAR + CHECK. 값 추가 시 마이그레이션이 가볍다.

-- ============================================================
-- 1. 공통 · 사용자
-- ============================================================

CREATE TABLE region (
    id           BIGSERIAL   PRIMARY KEY,
    code         VARCHAR(20) NOT NULL UNIQUE,
    name         VARCHAR(50) NOT NULL,
    level        VARCHAR(20) NOT NULL,
    parent_id    BIGINT      REFERENCES region (id),
    rent_grade   SMALLINT,
    is_seoul     BOOLEAN     NOT NULL DEFAULT false,
    is_overcrowd BOOLEAN     NOT NULL DEFAULT false
);

CREATE TABLE terms (
    id             BIGSERIAL    PRIMARY KEY,
    code           VARCHAR(50)  NOT NULL,
    version        VARCHAR(20)  NOT NULL,
    title          VARCHAR(200) NOT NULL,
    is_required    BOOLEAN      NOT NULL,
    content_url    TEXT,
    effective_from DATE         NOT NULL,
    effective_to   DATE
);

CREATE TABLE app_user (
    id                  BIGSERIAL    PRIMARY KEY,
    auth_provider       VARCHAR(20)  NOT NULL,
    provider_user_id    VARCHAR(100) NOT NULL,
    nickname            VARCHAR(50),
    profile_image_url   TEXT,
    email               VARCHAR(255),
    name                VARCHAR(50),
    birth_date          DATE,
    phone               VARCHAR(20),
    phone_verified_at   TIMESTAMPTZ,
    residence_region_id BIGINT       REFERENCES region (id),
    military_months     INT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ
);

CREATE TABLE user_agreement (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES app_user (id),
    terms_id   BIGINT      NOT NULL REFERENCES terms (id),
    agreed     BOOLEAN     NOT NULL,
    agreed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ
);

-- ============================================================
-- 2. 금융 연동 · 온보딩
-- ============================================================

CREATE TABLE bank_connection (
    id              BIGSERIAL   PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES app_user (id),
    org_code        VARCHAR(20) NOT NULL,
    org_name        VARCHAR(50),
    status          VARCHAR(20) NOT NULL,
    connected_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_synced_at  TIMESTAMPTZ,
    disconnected_at TIMESTAMPTZ
);

CREATE TABLE financial_snapshot (
    id                   BIGSERIAL   PRIMARY KEY,
    user_id              BIGINT      NOT NULL REFERENCES app_user (id),
    as_of                DATE        NOT NULL,
    source               VARCHAR(20) NOT NULL,
    confirmed_by_user    BOOLEAN     NOT NULL DEFAULT false,
    financial_asset      BIGINT,
    monthly_income       BIGINT,
    monthly_expense      BIGINT,
    loan_balance         BIGINT,
    monthly_debt_payment BIGINT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- 3. 계획 · 진단 (벤치 · 1루)
-- ============================================================

CREATE TABLE plan (
    id               BIGSERIAL   PRIMARY KEY,
    user_id          BIGINT      NOT NULL REFERENCES app_user (id),
    lease_type       VARCHAR(20) NOT NULL,
    start_situation  VARCHAR(30),
    stage            VARCHAR(20) NOT NULL DEFAULT 'BENCH',
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_favorite      BOOLEAN     NOT NULL DEFAULT false,
    target_move_date DATE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at        TIMESTAMPTZ
);

CREATE TABLE plan_input (
    id                 BIGSERIAL    PRIMARY KEY,
    plan_id            BIGINT       NOT NULL REFERENCES plan (id),
    hope_deposit       BIGINT,
    current_deposit    BIGINT,
    monthly_rent       BIGINT,
    maintenance_fee    BIGINT,
    max_monthly_burden BIGINT,
    region_id          BIGINT       REFERENCES region (id),
    area_m2            NUMERIC(6,2),
    house_type         VARCHAR(30),
    is_homeless        BOOLEAN,
    householder_status VARCHAR(30),
    marital_status     VARCHAR(20),
    employment_type    VARCHAR(30),
    employment_months  INT,
    company_size       VARCHAR(30),
    unknown_fields     JSONB        NOT NULL DEFAULT '[]',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE cost_estimate (
    id             BIGSERIAL   PRIMARY KEY,
    plan_id        BIGINT      NOT NULL REFERENCES plan (id),
    deposit        BIGINT      NOT NULL DEFAULT 0,
    moving_cost    BIGINT      NOT NULL DEFAULT 0,
    brokerage_fee  BIGINT      NOT NULL DEFAULT 0,
    guarantee_fee  BIGINT      NOT NULL DEFAULT 0,
    stamp_tax      BIGINT      NOT NULL DEFAULT 0,
    total_required BIGINT      NOT NULL DEFAULT 0,
    user_edited    JSONB       NOT NULL DEFAULT '{}',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE diagnosis (
    id                   BIGSERIAL    PRIMARY KEY,
    plan_id              BIGINT       NOT NULL REFERENCES plan (id),
    available_cash       BIGINT       NOT NULL DEFAULT 0,
    returnable_deposit   BIGINT       NOT NULL DEFAULT 0,
    monthly_disposable   BIGINT       NOT NULL DEFAULT 0,
    months_to_move       INT          NOT NULL DEFAULT 0,
    savable_amount       BIGINT       NOT NULL DEFAULT 0,
    expected_fund        BIGINT       NOT NULL DEFAULT 0,
    shortfall            BIGINT       NOT NULL DEFAULT 0,
    monthly_housing_cost BIGINT       NOT NULL DEFAULT 0,
    rir                  NUMERIC(6,3),
    rir_level            VARCHAR(20),
    verdict              VARCHAR(20)  NOT NULL,
    engine_version       VARCHAR(20)  NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ============================================================
-- 4. 정책 · 판정 (2루)
-- ============================================================

CREATE TABLE guarantee_agency (
    id           BIGSERIAL   PRIMARY KEY,
    code         VARCHAR(10) NOT NULL UNIQUE,
    name         VARCHAR(50) NOT NULL,
    agency_type  VARCHAR(20) NOT NULL,
    limit_basis  VARCHAR(50),
    fee_rate_min NUMERIC(6,3),
    fee_rate_max NUMERIC(6,3)
);

CREATE TABLE policy (
    id                  BIGSERIAL    PRIMARY KEY,
    code                VARCHAR(50)  NOT NULL UNIQUE,
    name                VARCHAR(200) NOT NULL,
    category            VARCHAR(30)  NOT NULL,
    tier                VARCHAR(10),
    operator            VARCHAR(100),
    guarantee_agency_id BIGINT       REFERENCES guarantee_agency (id),
    source              VARCHAR(30)  NOT NULL,
    external_id         VARCHAR(100),
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    detail_url          TEXT,
    apply_url           TEXT,
    discontinued_at     DATE,
    replaced_by_id      BIGINT       REFERENCES policy (id),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE policy_rule (
    id             BIGSERIAL   PRIMARY KEY,
    policy_id      BIGINT      NOT NULL REFERENCES policy (id),
    version        INT         NOT NULL,
    rule_json      JSONB       NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    effective_from DATE        NOT NULL,
    effective_to   DATE,
    drafted_by     VARCHAR(50),
    drafted_at     TIMESTAMPTZ,
    reviewed_by    VARCHAR(50),
    reviewed_at    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE policy_snapshot (
    id         BIGSERIAL   PRIMARY KEY,
    policy_id  BIGINT      NOT NULL REFERENCES policy (id),
    rule_id    BIGINT      REFERENCES policy_rule (id),
    source_url TEXT        NOT NULL,
    raw_text   TEXT        NOT NULL,
    checksum   VARCHAR(64) NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE policy_exclusion_group (
    id          BIGSERIAL    PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    pick_rule   VARCHAR(30)  NOT NULL DEFAULT 'MAX_BENEFIT'
);

CREATE TABLE policy_exclusion_member (
    id        BIGSERIAL PRIMARY KEY,
    group_id  BIGINT    NOT NULL REFERENCES policy_exclusion_group (id),
    policy_id BIGINT    NOT NULL REFERENCES policy (id)
);

CREATE TABLE policy_verdict (
    id                BIGSERIAL   PRIMARY KEY,
    plan_id           BIGINT      NOT NULL REFERENCES plan (id),
    policy_id         BIGINT      NOT NULL REFERENCES policy (id),
    rule_id           BIGINT      REFERENCES policy_rule (id),
    verdict           VARCHAR(20) NOT NULL,
    expected_amount   BIGINT,
    expected_rate     NUMERIC(6,3),
    expected_benefit  BIGINT,
    annual_total_cost BIGINT,
    is_selected       BOOLEAN     NOT NULL DEFAULT false,
    engine_version    VARCHAR(20) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE verdict_basis (
    id              BIGSERIAL    PRIMARY KEY,
    verdict_id      BIGINT       NOT NULL REFERENCES policy_verdict (id),
    condition_code  VARCHAR(50)  NOT NULL,
    condition_label VARCHAR(200) NOT NULL,
    required_text   VARCHAR(200),
    is_met          BOOLEAN,
    fact_code       VARCHAR(20),
    source_url      TEXT,
    snapshot_at     TIMESTAMPTZ
);

CREATE TABLE rejection_reason (
    id             BIGSERIAL    PRIMARY KEY,
    verdict_id     BIGINT       NOT NULL REFERENCES policy_verdict (id),
    reason_code    VARCHAR(50)  NOT NULL,
    reason_label   VARCHAR(200) NOT NULL,
    alternative_id BIGINT       REFERENCES policy (id)
);

-- ============================================================
-- 5. 대안 · 자산
-- ============================================================

CREATE TABLE alternative_plan (
    id                     BIGSERIAL   PRIMARY KEY,
    plan_id                BIGINT      NOT NULL REFERENCES plan (id),
    alt_type               VARCHAR(30) NOT NULL,
    params                 JSONB       NOT NULL DEFAULT '{}',
    recalculated_shortfall BIGINT,
    delta_monthly_burden   BIGINT,
    is_recommended         BOOLEAN     NOT NULL DEFAULT false,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE asset_option (
    id                     BIGSERIAL    PRIMARY KEY,
    plan_id                BIGINT       NOT NULL REFERENCES plan (id),
    asset_type             VARCHAR(30)  NOT NULL,
    balance                BIGINT,
    withdraw_amount        BIGINT,
    tax_penalty_rate       NUMERIC(6,3),
    tax_penalty_amount     BIGINT,
    re_contribution_years  NUMERIC(4,1),
    is_reversible          BOOLEAN      NOT NULL,
    compared_loan_interest BIGINT,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ============================================================
-- 6. 매물 · 계약 (3루) — 전세/월세 공통 모듈. lease_type 으로만 분기한다.
-- ============================================================

CREATE TABLE property (
    id                    BIGSERIAL    PRIMARY KEY,
    plan_id               BIGINT       NOT NULL REFERENCES plan (id),
    region_id             BIGINT       REFERENCES region (id),
    address               VARCHAR(300),
    road_address          VARCHAR(300),
    house_type            VARCHAR(30),
    area_m2               NUMERIC(6,2),
    market_price          BIGINT,
    official_price        BIGINT,
    senior_debt           BIGINT,
    is_violation_building BOOLEAN,
    is_trust_registered   BOOLEAN,
    owner_matches         BOOLEAN,
    jeonse_ratio          NUMERIC(6,3),
    jeonse_ratio_level    VARCHAR(20),
    guarantee_126_pass    BOOLEAN,
    min_priority_amount   BIGINT,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE property_check (
    id          BIGSERIAL    PRIMARY KEY,
    property_id BIGINT       NOT NULL REFERENCES property (id),
    check_code  VARCHAR(50)  NOT NULL,
    check_label VARCHAR(200) NOT NULL,
    result      VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN',
    fact_code   VARCHAR(20),
    source_url  TEXT,
    checked_at  TIMESTAMPTZ
);

CREATE TABLE lease_contract (
    id                    BIGSERIAL   PRIMARY KEY,
    plan_id               BIGINT      NOT NULL REFERENCES plan (id),
    property_id           BIGINT      REFERENCES property (id),
    lease_type            VARCHAR(20) NOT NULL,
    deposit               BIGINT      NOT NULL DEFAULT 0,
    monthly_rent          BIGINT      NOT NULL DEFAULT 0,
    maintenance_fee       BIGINT      NOT NULL DEFAULT 0,
    down_payment          BIGINT,
    contract_date         DATE,
    balance_date          DATE,
    move_in_date          DATE,
    confirmed_date_at     DATE,
    move_in_report_at     DATE,
    is_electronic         BOOLEAN     NOT NULL DEFAULT false,
    lease_end_date        DATE,
    renewal_notified_at   DATE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE contract_special_term (
    id          BIGSERIAL   PRIMARY KEY,
    contract_id BIGINT      NOT NULL REFERENCES lease_contract (id),
    term_code   VARCHAR(50) NOT NULL,
    is_included BOOLEAN     NOT NULL DEFAULT false,
    term_text   TEXT,
    fact_code   VARCHAR(20)
);

-- ============================================================
-- 7. 서류 · 증빙
-- ============================================================

CREATE TABLE document_type (
    id               BIGSERIAL    PRIMARY KEY,
    code             VARCHAR(50)  NOT NULL UNIQUE,
    name             VARCHAR(200) NOT NULL,
    issuer           VARCHAR(100),
    issue_url        TEXT,
    online_available BOOLEAN      NOT NULL DEFAULT true,
    validity_days    INT,
    fee              INT          NOT NULL DEFAULT 0,
    note             TEXT
);

CREATE TABLE user_document (
    id               BIGSERIAL   PRIMARY KEY,
    plan_id          BIGINT      NOT NULL REFERENCES plan (id),
    document_type_id BIGINT      NOT NULL REFERENCES document_type (id),
    policy_id        BIGINT      REFERENCES policy (id),
    status           VARCHAR(20) NOT NULL DEFAULT 'NEEDED',
    issued_at        DATE,
    expires_at       DATE,
    submitted_at     DATE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- 8. 가구원 · 동의 — 이름·주민식별정보를 담지 않는다(SEC-01-02)
-- ============================================================

CREATE TABLE household_member (
    id               BIGSERIAL   PRIMARY KEY,
    plan_id          BIGINT      NOT NULL REFERENCES plan (id),
    relation         VARCHAR(30) NOT NULL,
    consent_required BOOLEAN     NOT NULL DEFAULT false,
    verified_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE consent_token (
    id         BIGSERIAL    PRIMARY KEY,
    plan_id    BIGINT       NOT NULL REFERENCES plan (id),
    member_id  BIGINT       REFERENCES household_member (id),
    policy_id  BIGINT       REFERENCES policy (id),
    token_hash VARCHAR(64)  NOT NULL UNIQUE,
    purpose    VARCHAR(100) NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    used_at    TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ============================================================
-- 9. 순서 · 데드라인 · 신청 (3루)
-- ============================================================

CREATE TABLE plan_step (
    id              BIGSERIAL    PRIMARY KEY,
    plan_id         BIGINT       NOT NULL REFERENCES plan (id),
    step_code       VARCHAR(50)  NOT NULL,
    step_name       VARCHAR(200) NOT NULL,
    sequence        INT          NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'LOCKED',
    depends_on      JSONB        NOT NULL DEFAULT '[]',
    is_irreversible BOOLEAN      NOT NULL DEFAULT false,
    completed_at    TIMESTAMPTZ
);

-- deadline_type 3종을 반드시 분리한다.
--   LEGAL       공식 신청 가능기한 (예: 잔금일+3개월, 놓치면 복구 불가)
--   RECOMMENDED 홈런 권장 착수일  (예: D-21, D-7 — 법정 기한이 아님)
--   PROCESSING  은행 처리 예상기간
CREATE TABLE deadline (
    id            BIGSERIAL    PRIMARY KEY,
    plan_id       BIGINT       NOT NULL REFERENCES plan (id),
    step_id       BIGINT       REFERENCES plan_step (id),
    deadline_type VARCHAR(20)  NOT NULL,
    label         VARCHAR(200) NOT NULL,
    base_event    VARCHAR(50)  NOT NULL,
    base_date     DATE,
    offset_days   INT          NOT NULL DEFAULT 0,
    due_date      DATE,
    is_absolute   BOOLEAN      NOT NULL DEFAULT false,
    fact_code     VARCHAR(20),
    alerted_at    TIMESTAMPTZ
);

CREATE TABLE application (
    id                 BIGSERIAL   PRIMARY KEY,
    plan_id            BIGINT      NOT NULL REFERENCES plan (id),
    policy_id          BIGINT      NOT NULL REFERENCES policy (id),
    channel            VARCHAR(50),
    status             VARCHAR(20) NOT NULL DEFAULT 'PREPARING',
    submitted_at       TIMESTAMPTZ,
    result_at          TIMESTAMPTZ,
    reject_stage       VARCHAR(20),
    reject_reason_code VARCHAR(50),
    approved_amount    BIGINT,
    approved_rate      NUMERIC(6,3),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- 10. 홈 · 사후관리 (4루)
-- ============================================================

CREATE TABLE loan_account (
    id                    BIGSERIAL   PRIMARY KEY,
    plan_id               BIGINT      NOT NULL REFERENCES plan (id),
    policy_id             BIGINT      REFERENCES policy (id),
    bank_code             VARCHAR(20),
    principal             BIGINT      NOT NULL,
    rate                  NUMERIC(6,3) NOT NULL,
    repayment_type        VARCHAR(30) NOT NULL,
    executed_at           DATE        NOT NULL,
    maturity_at           DATE,
    rate_cut_eligible_at  DATE,
    rate_cut_requested_at DATE
);

CREATE TABLE fixed_expense (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES app_user (id),
    plan_id    BIGINT       REFERENCES plan (id),
    name       VARCHAR(100) NOT NULL,
    category   VARCHAR(30)  NOT NULL,
    amount     BIGINT       NOT NULL,
    due_day    SMALLINT,
    is_active  BOOLEAN      NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE monthly_checkin (
    id                  BIGSERIAL   PRIMARY KEY,
    user_id             BIGINT      NOT NULL REFERENCES app_user (id),
    plan_id             BIGINT      REFERENCES plan (id),
    year_month          VARCHAR(7)  NOT NULL,
    planned_income      BIGINT,
    actual_income       BIGINT,
    planned_expense     BIGINT,
    actual_expense      BIGINT,
    actual_housing_cost BIGINT,
    remaining           BIGINT,
    rir                 NUMERIC(6,3),
    over_budget_items   JSONB       NOT NULL DEFAULT '[]',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- trigger_codes 에 신속채무조정 청년특례 5요건(FCT-128)을 그대로 넣는다.
CREATE TABLE delinquency_risk (
    id                      BIGSERIAL   PRIMARY KEY,
    user_id                 BIGINT      NOT NULL REFERENCES app_user (id),
    assessed_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    risk_level              VARCHAR(20) NOT NULL,
    trigger_codes           JSONB       NOT NULL DEFAULT '[]',
    credit_score_percentile SMALLINT,
    recent_delinquency_cnt  SMALLINT,
    engine_version          VARCHAR(20) NOT NULL
);

-- ============================================================
-- 11. 교육 — 콘텐츠를 만들지 않고 심의된 외부 자산을 물어온다
-- ============================================================

CREATE TABLE education_content (
    id                 BIGSERIAL    PRIMARY KEY,
    code               VARCHAR(50)  NOT NULL UNIQUE,
    title              VARCHAR(200) NOT NULL,
    topic              VARCHAR(30)  NOT NULL,
    source             VARCHAR(30)  NOT NULL,
    external_url       TEXT,
    review_no          VARCHAR(50),
    review_valid_until DATE,
    estimated_minutes  SMALLINT,
    is_active          BOOLEAN      NOT NULL DEFAULT true
);

CREATE TABLE education_progress (
    id           BIGSERIAL   PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES app_user (id),
    content_id   BIGINT      NOT NULL REFERENCES education_content (id),
    status       VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    progress_pct SMALLINT    NOT NULL DEFAULT 0,
    quiz_score   SMALLINT,
    completed_at TIMESTAMPTZ,
    expires_at   DATE,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE education_trigger (
    id             BIGSERIAL   PRIMARY KEY,
    code           VARCHAR(50) NOT NULL UNIQUE,
    event_code     VARCHAR(50) NOT NULL,
    content_id     BIGINT      NOT NULL REFERENCES education_content (id),
    condition_json JSONB       NOT NULL DEFAULT '{}',
    is_active      BOOLEAN     NOT NULL DEFAULT true
);

CREATE TABLE intervention_log (
    id                 BIGSERIAL   PRIMARY KEY,
    user_id            BIGINT      NOT NULL REFERENCES app_user (id),
    trigger_id         BIGINT      REFERENCES education_trigger (id),
    shown_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    decision           VARCHAR(30),
    outcome_checked_at TIMESTAMPTZ,
    behavior_changed   BOOLEAN
);

-- ============================================================
-- 12. AI 코치
-- ============================================================

-- 사람이 검수한 카드만 들어간다. LLM 은 카드를 고르고 엮어 말할 뿐 사실을 만들지 않는다.
-- fact_refs 는 config_effective.fact_code 배열 → 출력 검증기의 대조 기준.
CREATE TABLE knowledge_card (
    id          BIGSERIAL    PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL UNIQUE,
    title       VARCHAR(200) NOT NULL,
    body        TEXT         NOT NULL,
    stage       JSONB        NOT NULL DEFAULT '[]',
    topic       JSONB        NOT NULL DEFAULT '[]',
    trigger_codes JSONB      NOT NULL DEFAULT '[]',
    aliases     JSONB        NOT NULL DEFAULT '[]',
    fact_refs   JSONB        NOT NULL DEFAULT '[]',
    source_url  TEXT,
    source_org  VARCHAR(100),
    reviewed_by VARCHAR(50),
    reviewed_at TIMESTAMPTZ,
    is_active   BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE coach_session (
    id              BIGSERIAL   PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES app_user (id),
    plan_id         BIGINT      REFERENCES plan (id),
    screen_code     VARCHAR(50),
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_message_at TIMESTAMPTZ,
    closed_at       TIMESTAMPTZ
);

CREATE TABLE coach_message (
    id            BIGSERIAL   PRIMARY KEY,
    session_id    BIGINT      NOT NULL REFERENCES coach_session (id),
    role          VARCHAR(20) NOT NULL,
    content       TEXT        NOT NULL,
    used_cards    JSONB       NOT NULL DEFAULT '[]',
    tool_calls    JSONB       NOT NULL DEFAULT '[]',
    fallback_used BOOLEAN     NOT NULL DEFAULT false,
    reject_reason VARCHAR(50),
    model         VARCHAR(50),
    latency_ms    INT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- input_hash 로 동일 입력 재요청을 DB 에서 반환한다. 데모 반복 시연 비용·지연 0.
CREATE TABLE ai_generation (
    id            BIGSERIAL   PRIMARY KEY,
    feature_code  VARCHAR(30) NOT NULL,
    input_hash    VARCHAR(64) NOT NULL UNIQUE,
    output        TEXT        NOT NULL,
    used_cards    JSONB       NOT NULL DEFAULT '[]',
    model         VARCHAR(50),
    fallback_used BOOLEAN     NOT NULL DEFAULT false,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- 13. 운영 · 설정
-- ============================================================

-- 팩트 레지스트리 141행이 그대로 들어온다. 매년 바뀌는 수치의 단일 출처.
CREATE TABLE config_effective (
    id             BIGSERIAL    PRIMARY KEY,
    fact_code      VARCHAR(20)  NOT NULL,
    category       VARCHAR(30)  NOT NULL,
    item           VARCHAR(200) NOT NULL,
    value_text     TEXT,
    value_num      NUMERIC(18,3),
    unit           VARCHAR(20),
    apply_condition TEXT,
    source         VARCHAR(100),
    source_url     TEXT,
    confidence     VARCHAR(20)  NOT NULL DEFAULT 'CONFIRMED',
    change_cycle   VARCHAR(20),
    related_feature VARCHAR(50),
    note           TEXT,
    effective_from DATE,
    effective_to   DATE,
    updated_by     VARCHAR(50),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE external_api_health (
    id          BIGSERIAL   PRIMARY KEY,
    source_code VARCHAR(30) NOT NULL,
    status      VARCHAR(20) NOT NULL,
    latency_ms  INT,
    error_msg   TEXT,
    checked_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE audit_log (
    id          BIGSERIAL   PRIMARY KEY,
    actor_type  VARCHAR(20) NOT NULL,
    actor_id    VARCHAR(50),
    action      VARCHAR(50) NOT NULL,
    target_type VARCHAR(50),
    target_id   VARCHAR(50),
    payload     JSONB       NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- === 인덱스 · 제약 (ERDCloud Import 시에는 여기부터 제외) ===

ALTER TABLE app_user      ADD CONSTRAINT uq_app_user_provider UNIQUE (auth_provider, provider_user_id);
ALTER TABLE terms         ADD CONSTRAINT uq_terms_code_version UNIQUE (code, version);
ALTER TABLE policy_rule   ADD CONSTRAINT uq_policy_rule_version UNIQUE (policy_id, version);
ALTER TABLE policy_verdict ADD CONSTRAINT uq_policy_verdict UNIQUE (plan_id, policy_id, rule_id);
ALTER TABLE policy_exclusion_member ADD CONSTRAINT uq_exclusion_member UNIQUE (group_id, policy_id);
ALTER TABLE monthly_checkin ADD CONSTRAINT uq_monthly_checkin UNIQUE (user_id, year_month);
ALTER TABLE education_progress ADD CONSTRAINT uq_education_progress UNIQUE (user_id, content_id);
ALTER TABLE plan_step     ADD CONSTRAINT uq_plan_step UNIQUE (plan_id, step_code);
ALTER TABLE config_effective ADD CONSTRAINT uq_config_effective UNIQUE (fact_code, effective_from);

ALTER TABLE app_user       ADD CONSTRAINT ck_auth_provider CHECK (auth_provider IN ('KAKAO','GOOGLE','LOCAL'));
ALTER TABLE plan           ADD CONSTRAINT ck_lease_type CHECK (lease_type IN ('JEONSE','WOLSE','BANJEONSE'));
ALTER TABLE plan           ADD CONSTRAINT ck_stage CHECK (stage IN ('BENCH','FIRST','SECOND','THIRD','HOME'));
ALTER TABLE plan           ADD CONSTRAINT ck_plan_status CHECK (status IN ('ACTIVE','PAUSED','DONE','ABANDONED'));
ALTER TABLE diagnosis      ADD CONSTRAINT ck_diagnosis_verdict CHECK (verdict IN ('POSSIBLE','CAUTION','DIFFICULT'));
ALTER TABLE diagnosis      ADD CONSTRAINT ck_rir_level CHECK (rir_level IS NULL OR rir_level IN ('STABLE','WATCH','RISK'));
ALTER TABLE policy         ADD CONSTRAINT ck_policy_status CHECK (status IN ('ACTIVE','DISCONTINUED','DRAFT'));
ALTER TABLE policy         ADD CONSTRAINT ck_policy_tier CHECK (tier IS NULL OR tier IN ('L1','L2','L3'));
ALTER TABLE policy_rule    ADD CONSTRAINT ck_rule_status CHECK (status IN ('DRAFT','REVIEWED','ACTIVE','RETIRED'));
ALTER TABLE policy_verdict ADD CONSTRAINT ck_verdict CHECK (verdict IN ('PASS','NEED_INFO','FAIL'));
ALTER TABLE property_check ADD CONSTRAINT ck_check_result CHECK (result IN ('PASS','FAIL','UNKNOWN'));
ALTER TABLE plan_step      ADD CONSTRAINT ck_step_status CHECK (status IN ('LOCKED','READY','DOING','DONE','SKIPPED'));
ALTER TABLE deadline       ADD CONSTRAINT ck_deadline_type CHECK (deadline_type IN ('LEGAL','RECOMMENDED','PROCESSING'));
ALTER TABLE application    ADD CONSTRAINT ck_app_status CHECK (status IN ('PREPARING','SUBMITTED','SCREENING','APPROVED','REJECTED','CANCELED'));
ALTER TABLE application    ADD CONSTRAINT ck_reject_stage CHECK (reject_stage IS NULL OR reject_stage IN ('BANK','GUARANTEE','DOCUMENT','PRODUCT'));
ALTER TABLE user_document  ADD CONSTRAINT ck_doc_status CHECK (status IN ('NEEDED','ISSUED','SUBMITTED','EXPIRED'));
ALTER TABLE coach_message  ADD CONSTRAINT ck_coach_role CHECK (role IN ('USER','ASSISTANT','SYSTEM'));
ALTER TABLE config_effective ADD CONSTRAINT ck_confidence CHECK (confidence IN ('CONFIRMED','REVIEW','CONFLICT','UNKNOWN','CORRECTED','RETIRED'));
ALTER TABLE delinquency_risk ADD CONSTRAINT ck_risk_level CHECK (risk_level IN ('SAFE','WATCH','WARNING','DANGER'));

CREATE INDEX ix_plan_user            ON plan (user_id, status);
CREATE INDEX ix_plan_input_plan      ON plan_input (plan_id);
CREATE INDEX ix_diagnosis_plan       ON diagnosis (plan_id, created_at DESC);
CREATE INDEX ix_policy_verdict_plan  ON policy_verdict (plan_id, verdict);
CREATE INDEX ix_verdict_basis_v      ON verdict_basis (verdict_id);
CREATE INDEX ix_policy_rule_active   ON policy_rule (policy_id, status, effective_from);
CREATE INDEX ix_policy_rule_json     ON policy_rule USING gin (rule_json);
CREATE INDEX ix_user_document_plan   ON user_document (plan_id, status);
CREATE INDEX ix_deadline_due         ON deadline (plan_id, due_date);
CREATE INDEX ix_plan_step_plan       ON plan_step (plan_id, sequence);
CREATE INDEX ix_consent_token_exp    ON consent_token (expires_at) WHERE used_at IS NULL AND revoked_at IS NULL;
CREATE INDEX ix_coach_message_sess   ON coach_message (session_id, created_at);
CREATE INDEX ix_knowledge_card_trig  ON knowledge_card USING gin (trigger_codes);
CREATE INDEX ix_knowledge_card_topic ON knowledge_card USING gin (topic);
CREATE INDEX ix_config_lookup        ON config_effective (fact_code, effective_from DESC);
CREATE INDEX ix_audit_target         ON audit_log (target_type, target_id, created_at DESC);
CREATE INDEX ix_financial_user       ON financial_snapshot (user_id, as_of DESC);

COMMENT ON TABLE  financial_snapshot IS '사용자 본인 대시보드용. 정책 판정 근거로는 verdict_basis.is_met(boolean)만 쓴다.';
COMMENT ON COLUMN verdict_basis.is_met IS '소득 금액이 아니라 충족 여부만 저장(SEC-01-02 무저장 원칙).';
COMMENT ON COLUMN plan_input.unknown_fields IS '"모름" 입력 항목 목록. 불가가 아니라 NEED_INFO 로 보내는 근거.';
COMMENT ON COLUMN deadline.deadline_type IS 'LEGAL=공식 신청기한 / RECOMMENDED=홈런 권장 착수일 / PROCESSING=은행 처리 예상기간. 절대 섞지 말 것.';
COMMENT ON COLUMN application.reject_stage IS '은행 심사인지 보증기관 심사인지에 따라 대처가 완전히 달라진다.';
COMMENT ON COLUMN education_content.review_no IS '준법감시인 심의필 번호. 심의된 콘텐츠만 노출한다.';
COMMENT ON COLUMN knowledge_card.fact_refs IS 'config_effective.fact_code 배열. AI 출력 검증기의 숫자 대조 기준.';
COMMENT ON COLUMN policy.replaced_by_id IS '중기청 대출(FCT-135) 같은 폐지 상품의 대체 상품.';
COMMENT ON COLUMN config_effective.effective_from IS '미확인(UNKNOWN) 항목은 시행일이 없으므로 NULL 을 허용한다.';
COMMENT ON TABLE  config_effective IS '팩트 레지스트리 141행. 매년 바뀌는 수치를 코드에 박지 않기 위한 단일 출처.';
