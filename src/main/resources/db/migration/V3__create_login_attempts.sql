CREATE TABLE login_attempts (

                                id BIGSERIAL PRIMARY KEY,

                                email VARCHAR(320) NOT NULL,

                                failed_attempts INTEGER NOT NULL DEFAULT 0,

                                first_failed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                locked_until TIMESTAMP,

                                CONSTRAINT uk_login_attempt_email
                                    UNIQUE (email)
);

CREATE INDEX idx_login_attempt_email
    ON login_attempts(email);