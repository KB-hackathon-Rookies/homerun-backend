ALTER TABLE lease_contract
    ADD COLUMN loan_product_kind VARCHAR(30),
    ADD COLUMN collateral_method VARCHAR(30),
    ADD COLUMN application_method VARCHAR(20),
    ADD COLUMN house_type VARCHAR(30);

ALTER TABLE lease_contract ADD CONSTRAINT ck_contract_loan_product_kind
    CHECK (loan_product_kind IS NULL OR loan_product_kind IN ('FUND_YOUTH','FUND_GENERAL','BANK'));
ALTER TABLE lease_contract ADD CONSTRAINT ck_contract_collateral_method
    CHECK (collateral_method IS NULL OR collateral_method IN ('HUG_SAFE_JEONSE','HF','SGI','CLAIM_TRANSFER','NONE'));
ALTER TABLE lease_contract ADD CONSTRAINT ck_contract_application_method
    CHECK (application_method IS NULL OR application_method IN ('BANK_VISIT','ONLINE'));
ALTER TABLE lease_contract ADD CONSTRAINT ck_contract_house_type
    CHECK (house_type IS NULL OR house_type IN ('APARTMENT','OFFICETEL','VILLA','MULTI_FAMILY','DETACHED','OTHER'));

CREATE TABLE registry_snapshot (
    id                                      BIGSERIAL PRIMARY KEY,
    contract_id                             BIGINT NOT NULL REFERENCES lease_contract (id) ON DELETE CASCADE,
    stage                                   VARCHAR(30) NOT NULL,
    owner_matches_contract_party            BOOLEAN,
    senior_debt                             BIGINT,
    mortgage_count                          INT,
    is_leasehold_registered                 BOOLEAN,
    has_seizure_or_disposition_restriction  BOOLEAN,
    is_auction_in_progress                  BOOLEAN,
    is_trust_registered                     BOOLEAN,
    issued_at                               DATE NOT NULL,
    recorded_at                             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_registry_snapshot_stage UNIQUE (contract_id, stage),
    CONSTRAINT ck_registry_snapshot_stage CHECK (stage IN ('CONTRACT_SIGNING','SETTLEMENT_DAY')),
    CONSTRAINT ck_registry_snapshot_senior_debt CHECK (senior_debt IS NULL OR senior_debt >= 0),
    CONSTRAINT ck_registry_snapshot_mortgage_count CHECK (mortgage_count IS NULL OR mortgage_count >= 0)
);

ALTER TABLE user_document
    ADD COLUMN purpose VARCHAR(30) NOT NULL DEFAULT 'GENERAL',
    ADD COLUMN issue_options JSONB NOT NULL DEFAULT '{}';

ALTER TABLE user_document ADD CONSTRAINT ck_user_document_purpose CHECK (purpose IN (
    'GENERAL','BANK_CONSULTATION','LOAN_APPLICATION','CONTRACT_REVIEW',
    'RETURN_GUARANTEE','GUARANTEE_FEE_SUPPORT','YEAR_END_TAX'));

DROP INDEX uq_user_document_plan_type;
CREATE UNIQUE INDEX uq_user_document_plan_type_purpose
    ON user_document (plan_id, document_type_id, purpose)
    WHERE policy_id IS NULL;
