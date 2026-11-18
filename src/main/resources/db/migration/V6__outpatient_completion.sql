ALTER TABLE encounters ADD COLUMN instructions TEXT, ADD COLUMN follow_up_date DATE;
ALTER TABLE email_outbox ADD COLUMN appointment_id BIGINT NULL,
    ADD COLUMN appointment_revision INT NULL,
    ADD CONSTRAINT fk_email_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id);
ALTER TABLE email_outbox DROP CHECK chk_email_status;
ALTER TABLE email_outbox ADD CONSTRAINT chk_email_status CHECK (status IN ('PENDING','SENDING','SENT','FAILED','SKIPPED'));

CREATE TABLE medications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    ingredient VARCHAR(200) NOT NULL,
    strength VARCHAR(100) NOT NULL,
    form VARCHAR(100) NOT NULL,
    unit VARCHAR(50) NOT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB;

CREATE TABLE prescriptions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    encounter_id BIGINT NOT NULL,
    version INT NOT NULL,
    revision INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    reason VARCHAR(500),
    author_user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    finalized_at DATETIME(6),
    FOREIGN KEY (encounter_id) REFERENCES encounters(id),
    FOREIGN KEY (author_user_id) REFERENCES users(id),
    UNIQUE KEY uk_prescription_version (encounter_id,version),
    CONSTRAINT chk_prescription_state CHECK (status IN ('DRAFT','FINAL'))
) ENGINE=InnoDB;

CREATE TABLE prescription_lines (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    prescription_id BIGINT NOT NULL,
    medication_id BIGINT NOT NULL,
    name_snapshot VARCHAR(200) NOT NULL,
    strength_snapshot VARCHAR(100) NOT NULL,
    unit_snapshot VARCHAR(50) NOT NULL,
    dose VARCHAR(100) NOT NULL,
    route VARCHAR(100) NOT NULL,
    frequency VARCHAR(100) NOT NULL,
    days INT NOT NULL,
    quantity DECIMAL(10,2) NOT NULL,
    instructions VARCHAR(1000),
    FOREIGN KEY (prescription_id) REFERENCES prescriptions(id),
    FOREIGN KEY (medication_id) REFERENCES medications(id),
    CONSTRAINT chk_prescription_quantity CHECK (days > 0 AND quantity > 0)
) ENGINE=InnoDB;

CREATE TABLE notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    appointment_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    body VARCHAR(1000) NOT NULL,
    read_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (appointment_id) REFERENCES appointments(id),
    INDEX ix_notifications_user (user_id,id)
) ENGINE=InnoDB;

-- Existing zero-fee invoices need no receipt to become paid.
UPDATE invoices i SET status='PAID' WHERE status='OPEN' AND NOT EXISTS
    (SELECT 1 FROM invoice_lines l WHERE l.invoice_id=i.id AND l.status='ACTIVE' AND l.amount>0);
