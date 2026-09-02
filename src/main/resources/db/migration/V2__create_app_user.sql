CREATE TABLE app_user (
                          id BIGSERIAL PRIMARY KEY,

                          auth_provider VARCHAR(20) NOT NULL,

                          provider_user_id VARCHAR(100) NOT NULL,

                          nickname VARCHAR(50),

                          email VARCHAR(255),

                          name VARCHAR(50),

                          birth_date DATE,

                          phone VARCHAR(20),

                          phone_verified_at TIMESTAMPTZ,

                          residence_region_id BIGINT,

                          military_months INT NOT NULL DEFAULT 0,

                          created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

                          updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

                          deleted_at TIMESTAMPTZ,

                          CONSTRAINT uq_app_user_provider
                              UNIQUE (auth_provider, provider_user_id),

                          CONSTRAINT fk_app_user_residence_region
                              FOREIGN KEY (residence_region_id)
                                  REFERENCES region(id),

                          CONSTRAINT chk_app_user_auth_provider
                              CHECK (auth_provider IN ('KAKAO', 'GOOGLE', 'LOCAL')),

                          CONSTRAINT chk_app_user_military_months
                              CHECK (military_months >= 0)
);