CREATE INDEX idx_financial_snapshot_user_created
    ON financial_snapshot (user_id, created_at DESC, id DESC);
