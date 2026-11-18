CREATE TABLE encounter_reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    encounter_id BIGINT NOT NULL UNIQUE,
    patient_id BIGINT NOT NULL, doctor_staff_id BIGINT NOT NULL,
    rating TINYINT NOT NULL, comment VARCHAR(1000),
    visible TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 5),
    INDEX ix_reviews_doctor (doctor_staff_id,visible,created_at),
    FOREIGN KEY (encounter_id) REFERENCES encounters(id),
    FOREIGN KEY (patient_id) REFERENCES patient_profiles(id),
    FOREIGN KEY (doctor_staff_id) REFERENCES doctor_profiles(staff_id)
) ENGINE=InnoDB;
ALTER TABLE patient_profiles ADD COLUMN owner_user_id BIGINT NULL,
    ADD COLUMN relationship VARCHAR(30) NULL,
    ADD CONSTRAINT fk_patient_owner FOREIGN KEY (owner_user_id) REFERENCES users(id),
    ADD INDEX ix_patient_owner (owner_user_id,active);
