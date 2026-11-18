ALTER TABLE appointments ADD COLUMN reminder_sent_at DATETIME(6) NULL;
ALTER TABLE encounters ADD COLUMN follow_up_reminded_at DATETIME(6) NULL;
ALTER TABLE notifications MODIFY appointment_id BIGINT NULL,
    ADD COLUMN kind VARCHAR(30) NOT NULL DEFAULT 'APPOINTMENT',
    ADD COLUMN link VARCHAR(500) NULL,
    ADD COLUMN dedupe_key VARCHAR(100) NULL,
    ADD UNIQUE KEY uk_notification_dedupe (user_id,dedupe_key);
ALTER TABLE clinic_settings ADD COLUMN reminders_enabled TINYINT(1) NOT NULL DEFAULT 1,
    ADD COLUMN reminder_hours_before INT NOT NULL DEFAULT 24;
CREATE TABLE waitlist_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT NOT NULL,
    service_id BIGINT NULL, specialty_id BIGINT NULL, doctor_staff_id BIGINT NULL,
    date_from DATE NOT NULL, date_to DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notified_at DATETIME(6) NULL, notified_slot_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_waitlist_status CHECK (status IN ('ACTIVE','NOTIFIED','BOOKED','EXPIRED','CANCELLED')),
    CONSTRAINT chk_waitlist_dates CHECK (date_from<=date_to),
    INDEX ix_waitlist_match (status,date_from,date_to),
    INDEX ix_waitlist_patient (patient_id,status),
    FOREIGN KEY (patient_id) REFERENCES patient_profiles(id),
    FOREIGN KEY (service_id) REFERENCES services(id),
    FOREIGN KEY (specialty_id) REFERENCES specialties(id),
    FOREIGN KEY (doctor_staff_id) REFERENCES doctor_profiles(staff_id),
    FOREIGN KEY (notified_slot_id) REFERENCES appointment_slots(id)
) ENGINE=InnoDB;

-- Durable delivery claim: repeated AFTER_COMMIT events must not alert another wave.
CREATE TABLE waitlist_slot_releases (
    release_key VARCHAR(100) PRIMARY KEY,
    slot_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (slot_id) REFERENCES appointment_slots(id)
) ENGINE=InnoDB;
