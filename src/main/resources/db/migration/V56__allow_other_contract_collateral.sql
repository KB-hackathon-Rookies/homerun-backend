ALTER TABLE lease_contract DROP CONSTRAINT ck_contract_collateral_method;

ALTER TABLE lease_contract ADD CONSTRAINT ck_contract_collateral_method
    CHECK (collateral_method IS NULL OR collateral_method IN (
        'HUG_SAFE_JEONSE', 'HF', 'SGI', 'CLAIM_TRANSFER', 'OTHER', 'NONE'
    ));
