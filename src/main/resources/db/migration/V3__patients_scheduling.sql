CREATE TABLE specialties (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    department_id BIGINT NOT NULL,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    active TINYINT(1) NOT NULL DEFAULT 1,
    FOREIGN KEY (department_id) REFERENCES departments(id)
) ENGINE=InnoDB;

CREATE TABLE patient_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    patient_code VARCHAR(30) NOT NULL UNIQUE,
    user_id BIGINT NULL UNIQUE,
    full_name VARCHAR(200) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(255),
    date_of_birth DATE,
    gender VARCHAR(20),
    address VARCHAR(500),
    identity_number VARCHAR(30),
    allergies TEXT,
    medical_history TEXT,
    emergency_contact VARCHAR(200),
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX ix_patients_name (full_name)
) ENGINE=InnoDB;

CREATE TABLE doctor_profiles (
    staff_id BIGINT NOT NULL PRIMARY KEY,
    specialty_id BIGINT NOT NULL,
    bio VARCHAR(1000),
    avatar_path VARCHAR(500),
    FOREIGN KEY (staff_id) REFERENCES staff(id),
    FOREIGN KEY (specialty_id) REFERENCES specialties(id)
) ENGINE=InnoDB;

CREATE TABLE doctor_services (
    doctor_staff_id BIGINT NOT NULL,
    service_id BIGINT NOT NULL,
    PRIMARY KEY (doctor_staff_id, service_id),
    FOREIGN KEY (doctor_staff_id) REFERENCES doctor_profiles(staff_id),
    FOREIGN KEY (service_id) REFERENCES services(id)
) ENGINE=InnoDB;

CREATE TABLE weekly_slot_templates (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    doctor_staff_id BIGINT NOT NULL,
    weekday TINYINT NOT NULL,
    starts_time TIME NOT NULL,
    ends_time TIME NOT NULL,
    room_id BIGINT NOT NULL,
    service_id BIGINT NOT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    FOREIGN KEY (doctor_staff_id) REFERENCES doctor_profiles(staff_id),
    FOREIGN KEY (room_id) REFERENCES rooms(id),
    FOREIGN KEY (service_id) REFERENCES services(id),
    CONSTRAINT chk_template_weekday CHECK (weekday BETWEEN 1 AND 7),
    CONSTRAINT chk_template_times CHECK (starts_time < ends_time)
) ENGINE=InnoDB;

CREATE TABLE appointment_slots (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    doctor_staff_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    service_id BIGINT NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    note VARCHAR(500),
    rejection_reason VARCHAR(500),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    FOREIGN KEY (doctor_staff_id) REFERENCES doctor_profiles(staff_id),
    FOREIGN KEY (room_id) REFERENCES rooms(id),
    FOREIGN KEY (service_id) REFERENCES services(id),
    CONSTRAINT chk_slot_times CHECK (starts_at < ends_at),
    CONSTRAINT chk_slot_status CHECK (status IN ('DRAFT','SUBMITTED','PUBLISHED','REJECTED','CLOSE_REQUESTED','CLOSED')),
    INDEX ix_slots_doctor_time (doctor_staff_id, starts_at, ends_at),
    INDEX ix_slots_room_time (room_id, starts_at, ends_at),
    INDEX ix_slots_search (status, starts_at)
) ENGINE=InnoDB;

CREATE TABLE appointments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    appointment_code VARCHAR(30) NOT NULL UNIQUE,
    patient_id BIGINT NOT NULL,
    slot_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED',
    symptoms VARCHAR(1000),
    booked_price DECIMAL(15,2) NOT NULL,
    queue_number INT,
    checked_in_at DATETIME(6),
    booked_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    revision INT NOT NULL DEFAULT 0,
    cancellation_reason VARCHAR(500),
    FOREIGN KEY (patient_id) REFERENCES patient_profiles(id),
    FOREIGN KEY (slot_id) REFERENCES appointment_slots(id),
    CONSTRAINT chk_appointment_status CHECK (status IN ('CONFIRMED','CHECKED_IN','COMPLETED','CANCELLED','NO_SHOW','NEEDS_RESCHEDULE')),
    INDEX ix_appointments_patient (patient_id, booked_at),
    INDEX ix_appointments_slot (slot_id)
) ENGINE=InnoDB;

CREATE TABLE slot_reservations (
    slot_id BIGINT NOT NULL PRIMARY KEY,
    appointment_id BIGINT NOT NULL UNIQUE,
    FOREIGN KEY (slot_id) REFERENCES appointment_slots(id),
    FOREIGN KEY (appointment_id) REFERENCES appointments(id)
) ENGINE=InnoDB;

CREATE TABLE appointment_changes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    appointment_id BIGINT NOT NULL,
    old_slot_id BIGINT,
    new_slot_id BIGINT,
    actor_user_id BIGINT,
    action VARCHAR(30) NOT NULL,
    occurred_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (appointment_id) REFERENCES appointments(id),
    FOREIGN KEY (old_slot_id) REFERENCES appointment_slots(id),
    FOREIGN KEY (new_slot_id) REFERENCES appointment_slots(id),
    FOREIGN KEY (actor_user_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE idempotency_results (
    actor_user_id BIGINT NOT NULL,
    operation VARCHAR(50) NOT NULL,
    request_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    result_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (actor_user_id, operation, request_key),
    FOREIGN KEY (actor_user_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE clinic_settings (
    id BIGINT NOT NULL PRIMARY KEY,
    clinic_name VARCHAR(200) NOT NULL,
    address VARCHAR(500),
    phone VARCHAR(30),
    email VARCHAR(255),
    working_hours VARCHAR(500)
) ENGINE=InnoDB;

INSERT INTO specialties (id, department_id, code, name, description) VALUES
    (1, 2, 'NOI', 'Nội tổng quát', 'Khám và điều trị nội khoa'),
    (2, 3, 'NGOAI', 'Ngoại tổng quát', 'Khám và điều trị ngoại khoa');

INSERT INTO clinic_settings (id, clinic_name, working_hours) VALUES
    (1, 'Bệnh viện An Tâm', 'Lịch làm việc theo từng bác sĩ; vui lòng xem khoảng khám đang mở.');

INSERT INTO patient_profiles (patient_code, user_id, full_name, phone, email)
SELECT 'BN000001', id, full_name, phone, email FROM users WHERE username = 'benhnhan01';

INSERT INTO doctor_profiles (staff_id, specialty_id, bio)
SELECT s.id, 1, 'Khám nội khoa tổng quát.' FROM staff s JOIN users u ON u.id = s.user_id WHERE u.username = 'bacsi01';
INSERT INTO doctor_profiles (staff_id, specialty_id, bio)
SELECT s.id, 2, 'Khám ngoại khoa tổng quát.' FROM staff s JOIN users u ON u.id = s.user_id WHERE u.username = 'bacsi02';
INSERT INTO doctor_services (doctor_staff_id, service_id)
SELECT d.staff_id, IF(d.specialty_id = 1, 1, 2) FROM doctor_profiles d;

INSERT INTO appointment_slots (doctor_staff_id, room_id, service_id, starts_at, ends_at, status)
SELECT d.staff_id, 1, 1, TIMESTAMP(UTC_DATE() + INTERVAL 1 DAY, '03:20:00'), TIMESTAMP(UTC_DATE() + INTERVAL 1 DAY, '04:20:00'), 'PUBLISHED'
FROM doctor_profiles d WHERE d.specialty_id = 1;
INSERT INTO appointment_slots (doctor_staff_id, room_id, service_id, starts_at, ends_at, status)
SELECT d.staff_id, 1, 1, TIMESTAMP(UTC_DATE() + INTERVAL 1 DAY, '04:30:00'), TIMESTAMP(UTC_DATE() + INTERVAL 1 DAY, '05:30:00'), 'PUBLISHED'
FROM doctor_profiles d WHERE d.specialty_id = 1;
INSERT INTO appointment_slots (doctor_staff_id, room_id, service_id, starts_at, ends_at, status)
SELECT d.staff_id, 2, 2, TIMESTAMP(UTC_DATE() + INTERVAL 1 DAY, '03:20:00'), TIMESTAMP(UTC_DATE() + INTERVAL 1 DAY, '04:20:00'), 'PUBLISHED'
FROM doctor_profiles d WHERE d.specialty_id = 2;
