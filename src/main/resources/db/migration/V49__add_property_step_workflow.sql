ALTER TABLE property
    ADD COLUMN official_price_year INT,
    ADD COLUMN official_price_source VARCHAR(40),
    ADD COLUMN is_non_residential BOOLEAN,
    ADD COLUMN workflow_step VARCHAR(20) NOT NULL DEFAULT 'REGISTRY',
    ADD COLUMN workflow_status VARCHAR(30) NOT NULL DEFAULT 'IN_PROGRESS',
    ADD COLUMN workflow_revision INT NOT NULL DEFAULT 1;

ALTER TABLE property
    ADD CONSTRAINT ck_property_official_price_year
        CHECK (official_price_year IS NULL OR official_price_year BETWEEN 2000 AND 2100),
    ADD CONSTRAINT ck_property_official_price_source
        CHECK (official_price_source IS NULL OR official_price_source IN
            ('REALTY_PRICE_APARTMENT', 'REALTY_PRICE_DETACHED', 'HOMETAX_STANDARD_VALUE')),
    ADD CONSTRAINT ck_property_workflow_step
        CHECK (workflow_step IN ('BUILDING', 'VIOLATION', 'REGISTRY', 'COMPLETE')),
    ADD CONSTRAINT ck_property_workflow_status
        CHECK (workflow_status IN
            ('IN_PROGRESS', 'NEEDS_CONFIRMATION', 'READY_FOR_CONSULTATION', 'CONSULTED', 'BLOCKED')),
    ADD CONSTRAINT ck_property_workflow_revision CHECK (workflow_revision > 0);
