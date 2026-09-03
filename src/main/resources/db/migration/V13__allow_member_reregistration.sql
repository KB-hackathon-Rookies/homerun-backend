ALTER TABLE app_user
    DROP CONSTRAINT uq_app_user_provider;

CREATE UNIQUE INDEX uq_app_user_active_provider
    ON app_user (auth_provider, provider_user_id)
    WHERE deleted_at IS NULL;
