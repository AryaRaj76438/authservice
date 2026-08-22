CREATE TABLE email_outbox (

                              id BIGSERIAL PRIMARY KEY,

                              recipient VARCHAR(320) NOT NULL,

                              subject VARCHAR(255) NOT NULL,

                              body TEXT NOT NULL,

                              status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

                              attempt_count INTEGER NOT NULL DEFAULT 0,

                              next_attempt_at TIMESTAMP,

                              sent_at TIMESTAMP,

                              last_attempt_at TIMESTAMP,

                              last_error TEXT,

                              created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);


CREATE INDEX idx_email_outbox_status
    ON email_outbox(status);


CREATE INDEX idx_email_outbox_next_attempt
    ON email_outbox(next_attempt_at);