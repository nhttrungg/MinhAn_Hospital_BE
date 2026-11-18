CREATE TABLE doctor_absences (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, doctor_staff_id BIGINT NOT NULL, starts_at DATETIME(6) NOT NULL,
 ends_at DATETIME(6) NOT NULL, reason VARCHAR(500) NOT NULL, actor_user_id BIGINT NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), request_id VARCHAR(100) NOT NULL,
 UNIQUE KEY uk_absence_request(actor_user_id,request_id), CHECK(starts_at<ends_at),
 FOREIGN KEY(doctor_staff_id) REFERENCES doctor_profiles(staff_id), FOREIGN KEY(actor_user_id) REFERENCES users(id),
 INDEX ix_absence_doctor(doctor_staff_id,starts_at,ends_at)
) ENGINE=InnoDB;
CREATE TABLE appointment_coordination (
 appointment_id BIGINT PRIMARY KEY, absence_id BIGINT NOT NULL, old_slot_id BIGINT NOT NULL,
 contact_status VARCHAR(30) NOT NULL DEFAULT 'NOT_CONTACTED', contact_note VARCHAR(1000),
 resolution VARCHAR(20) NOT NULL DEFAULT 'PENDING', revision INT NOT NULL DEFAULT 0,
 agreed_at DATETIME(6), agreed_by BIGINT, replacement_slot_id BIGINT, resolved_at DATETIME(6), resolved_by BIGINT,
 FOREIGN KEY(appointment_id) REFERENCES appointments(id), FOREIGN KEY(absence_id) REFERENCES doctor_absences(id),
 FOREIGN KEY(old_slot_id) REFERENCES appointment_slots(id), FOREIGN KEY(replacement_slot_id) REFERENCES appointment_slots(id),
 CHECK(contact_status IN ('NOT_CONTACTED','UNREACHABLE','CHOOSING','AGREED')),
 CHECK(resolution IN ('PENDING','RESCHEDULED','CANCELLED')), INDEX ix_coordination_status(absence_id,resolution,contact_status)
) ENGINE=InnoDB;
