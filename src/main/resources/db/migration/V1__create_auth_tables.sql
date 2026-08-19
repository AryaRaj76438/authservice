-- =====================================================
-- USERS
-- =====================================================

CREATE TABLE users (

                       id BIGSERIAL PRIMARY KEY,

                       name VARCHAR(100) NOT NULL,

                       email VARCHAR(320) NOT NULL,

                       password VARCHAR(255) NOT NULL,

                       role VARCHAR(20) NOT NULL,

                       email_verified BOOLEAN NOT NULL DEFAULT FALSE,

                       enabled BOOLEAN NOT NULL DEFAULT TRUE,

                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                       updated_at TIMESTAMP
);


-- =====================================================
-- USER EMAIL MUST BE UNIQUE
-- =====================================================

CREATE UNIQUE INDEX uk_users_email
    ON users(email);


-- =====================================================
-- VERIFICATION TOKENS
-- =====================================================

CREATE TABLE verification_tokens (

                                     id BIGSERIAL PRIMARY KEY,

                                     token_hash VARCHAR(64) NOT NULL,

                                     user_id BIGINT NOT NULL,

                                     expires_at TIMESTAMP NOT NULL,

                                     created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                     CONSTRAINT uk_verification_token_hash
                                         UNIQUE (token_hash),

                                     CONSTRAINT uk_verification_user
                                         UNIQUE (user_id),

                                     CONSTRAINT fk_verification_user
                                         FOREIGN KEY (user_id)
                                             REFERENCES users(id)
                                             ON DELETE CASCADE
);


-- =====================================================
-- REFRESH TOKENS
-- =====================================================

CREATE TABLE refresh_tokens (

                                id BIGSERIAL PRIMARY KEY,

                                token_hash VARCHAR(64) NOT NULL,

                                user_id BIGINT NOT NULL,

                                expires_at TIMESTAMP NOT NULL,

                                revoked BOOLEAN NOT NULL DEFAULT FALSE,

                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                CONSTRAINT uk_refresh_token_hash
                                    UNIQUE (token_hash),

                                CONSTRAINT fk_refresh_token_user
                                    FOREIGN KEY (user_id)
                                        REFERENCES users(id)
                                        ON DELETE CASCADE
);


-- =====================================================
-- INDEXES
-- =====================================================

CREATE INDEX idx_verification_token_hash
    ON verification_tokens(token_hash);

CREATE INDEX idx_refresh_token_hash
    ON refresh_tokens(token_hash);

CREATE INDEX idx_refresh_token_user
    ON refresh_tokens(user_id);