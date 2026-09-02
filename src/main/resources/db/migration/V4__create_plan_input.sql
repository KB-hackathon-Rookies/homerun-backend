CREATE TABLE plan_input (
                            id BIGSERIAL PRIMARY KEY,

                            plan_id BIGINT NOT NULL,
                            hope_deposit BIGINT,
                            current_deposit BIGINT,
                            monthly_rent BIGINT,
                            maintenance_fee BIGINT,
                            max_monthly_burden BIGINT,

                            region_id BIGINT,
                            area_m2 NUMERIC(6, 2),
                            house_type VARCHAR(30),

                            is_homeless BOOLEAN,
                            householder_status VARCHAR(30),
                            marital_status VARCHAR(20),

                            employment_type VARCHAR(30),
                            employment_months INT,
                            company_size VARCHAR(30),

                            unknown_fields JSONB NOT NULL DEFAULT '[]',

                            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

                            CONSTRAINT fk_plan_input_plan
                                FOREIGN KEY (plan_id)
                                    REFERENCES plan(id),

                            CONSTRAINT fk_plan_input_region
                                FOREIGN KEY (region_id)
                                    REFERENCES region(id)
);