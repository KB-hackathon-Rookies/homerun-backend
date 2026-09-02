CREATE TABLE plan (
    id BIGSERIAL PRIMARY KEY,

    user_id BIGSERIAL NOT NULL,
    lease_type VARCHAR(20) NOT NULL,
    start_situation VARCHAR(30),

    stage VARCHAR(20) NOT NULL DEFAULT 'BENCH',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    is_favorite BOOLEAN NOT NULL DEFAULT false,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at TIMESTAMPTZ,

    CONSTRAINT fk_plan_user
        FOREIGN KEY (user_id)
        REFERENCES app_user(id)
);