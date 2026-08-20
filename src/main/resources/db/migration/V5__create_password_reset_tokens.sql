CREATE TABLE password_reset_tokens (

                                       id BIGSERIAL PRIMARY KEY,

                                       token_hash VARCHAR(64) NOT NULL,

                                       user_id BIGINT NOT NULL,

                                       expires_at TIMESTAMP NOT NULL,

                                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                       used BOOLEAN NOT NULL DEFAULT FALSE,

                                       CONSTRAINT uk_password_reset_token_hash
                                           UNIQUE (token_hash),

                                       CONSTRAINT uk_password_reset_user
                                           UNIQUE (user_id),

                                       CONSTRAINT fk_password_reset_user
                                           FOREIGN KEY (user_id)
                                               REFERENCES users(id)
                                               ON DELETE CASCADE
);


CREATE INDEX idx_password_reset_token_hash
    ON password_reset_tokens(token_hash);