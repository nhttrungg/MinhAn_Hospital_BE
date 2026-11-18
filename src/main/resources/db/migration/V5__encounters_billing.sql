CREATE TABLE encounters (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    appointment_id BIGINT NOT NULL UNIQUE,
    patient_id BIGINT NOT NULL,
    doctor_staff_id BIGINT NOT NULL,
    nurse_staff_id BIGINT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'WAITING',
    revision INT NOT NULL DEFAULT 0,
    chief_complaint VARCHAR(1000),
    medical_history TEXT,
    allergies TEXT,
    vitals VARCHAR(1000),
    diagnosis TEXT,
    conclusion TEXT,
    started_at DATETIME(6),
    completed_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    FOREIGN KEY (appointment_id) REFERENCES appointments(id),
    FOREIGN KEY (patient_id) REFERENCES patient_profiles(id),
    FOREIGN KEY (doctor_staff_id) REFERENCES doctor_profiles(staff_id),
    FOREIGN KEY (nurse_staff_id) REFERENCES staff(id),
    CONSTRAINT chk_encounter_status CHECK (status IN ('WAITING','IN_PROGRESS','WAITING_RESULTS','COMPLETED')),
    INDEX ix_encounters_doctor_status (doctor_staff_id,status),
    INDEX ix_encounters_patient (patient_id,created_at)
) ENGINE=InnoDB;

CREATE TABLE encounter_amendments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    encounter_id BIGINT NOT NULL,
    author_user_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (encounter_id) REFERENCES encounters(id),
    FOREIGN KEY (author_user_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE invoices (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    invoice_code VARCHAR(30) NOT NULL UNIQUE,
    appointment_id BIGINT NULL UNIQUE,
    patient_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (appointment_id) REFERENCES appointments(id),
    FOREIGN KEY (patient_id) REFERENCES patient_profiles(id),
    CONSTRAINT chk_invoice_status CHECK (status IN ('OPEN','PARTIAL','PAID')),
    INDEX ix_invoices_patient (patient_id,created_at)
) ENGINE=InnoDB;

CREATE TABLE invoice_lines (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_id BIGINT NOT NULL,
    description_snapshot VARCHAR(255) NOT NULL,
    unit_snapshot VARCHAR(50) NOT NULL,
    quantity DECIMAL(10,2) NOT NULL,
    unit_price DECIMAL(15,2) NOT NULL,
    amount DECIMAL(15,2) GENERATED ALWAYS AS (quantity * unit_price) STORED,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    void_reason VARCHAR(500),
    FOREIGN KEY (invoice_id) REFERENCES invoices(id),
    CONSTRAINT chk_invoice_line_status CHECK (status IN ('ACTIVE','VOID')),
    CONSTRAINT chk_invoice_line_amount CHECK (quantity > 0 AND unit_price >= 0),
    UNIQUE KEY uk_invoice_source (invoice_id,source_type,source_id)
) ENGINE=InnoDB;

CREATE TABLE receipts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    receipt_code VARCHAR(30) NOT NULL UNIQUE,
    invoice_id BIGINT NOT NULL,
    cashier_user_id BIGINT NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    method VARCHAR(20) NOT NULL,
    recorded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (invoice_id) REFERENCES invoices(id),
    FOREIGN KEY (cashier_user_id) REFERENCES users(id),
    CONSTRAINT chk_receipt_amount CHECK (amount > 0),
    CONSTRAINT chk_receipt_method CHECK (method IN ('CASH','TRANSFER')),
    INDEX ix_receipts_invoice (invoice_id,recorded_at)
) ENGINE=InnoDB;
