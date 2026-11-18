ALTER TABLE appointment_slots DROP CHECK chk_slot_status;
ALTER TABLE appointment_slots ADD CONSTRAINT chk_slot_status
    CHECK (status IN ('DRAFT','SUBMITTED','PUBLISHED','REJECTED','CLOSE_REQUESTED','CLOSED'));

CREATE TABLE account_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    purpose VARCHAR(30) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX ix_account_tokens_user (user_id, purpose)
) ENGINE=InnoDB;

CREATE TABLE email_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    sent_at DATETIME(6),
    last_error VARCHAR(500),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_email_status CHECK (status IN ('PENDING','SENDING','SENT','FAILED')),
    INDEX ix_email_ready (status, next_attempt_at)
) ENGINE=InnoDB;
