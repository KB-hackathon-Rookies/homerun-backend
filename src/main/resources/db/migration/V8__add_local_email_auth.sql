ALTER TABLE app_user
    ADD COLUMN password_hash VARCHAR(100),
    ADD COLUMN email_verified_at TIMESTAMPTZ;

CREATE UNIQUE INDEX uq_app_user_local_email
    ON app_user (lower(email))
    WHERE auth_provider = 'LOCAL' AND deleted_at IS NULL;
