CREATE TABLE open_banking_connection (
    id                       BIGSERIAL    PRIMARY KEY,
    member_id                BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    user_seq_no              VARCHAR(20)  NOT NULL,
    access_token_ciphertext  TEXT         NOT NULL,
    refresh_token_ciphertext TEXT         NOT NULL,
    token_type               VARCHAR(20)  NOT NULL,
    scope                    VARCHAR(200) NOT NULL,
    access_token_expires_at  TIMESTAMPTZ  NOT NULL,
    refresh_token_expires_at TIMESTAMPTZ,
    created_at               TIMESTAMPTZ  NOT NULL,
    updated_at               TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_open_banking_connection_member UNIQUE (member_id)
);

CREATE INDEX ix_open_banking_connection_user_seq_no
    ON open_banking_connection (user_seq_no);
