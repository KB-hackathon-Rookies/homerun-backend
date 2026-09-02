ALTER TABLE user_agreement
    ADD CONSTRAINT uq_user_agreement_user_terms UNIQUE (user_id, terms_id);

CREATE INDEX ix_terms_active
    ON terms (is_required, effective_from, effective_to);

CREATE INDEX ix_user_agreement_user
    ON user_agreement (user_id, agreed);

INSERT INTO terms (code, version, title, is_required, content_url, effective_from)
VALUES
    ('SERVICE_TERMS', '1.0', '서비스 이용약관', true, '/terms/service', DATE '2026-09-02'),
    ('PRIVACY_POLICY', '1.0', '개인정보 처리 안내', true, '/privacy', DATE '2026-09-02')
ON CONFLICT (code, version) DO NOTHING;
