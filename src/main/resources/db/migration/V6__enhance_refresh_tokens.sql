ALTER TABLE refresh_tokens
    ADD COLUMN revoked_at TIMESTAMP;

ALTER TABLE refresh_tokens
    ADD COLUMN replaced_by_token_hash VARCHAR(64);

CREATE INDEX idx_refresh_token_replaced_by
    ON refresh_tokens(replaced_by_token_hash);