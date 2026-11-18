-- V23 remains intact: legacy support tasks are no longer used by the application.
CREATE TABLE clinical_templates (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, owner_staff_id BIGINT NULL, specialty_id BIGINT NOT NULL,
 name VARCHAR(200) NOT NULL, state VARCHAR(20) NOT NULL DEFAULT 'DRAFT', version INT NOT NULL DEFAULT 1,
 chief_complaint VARCHAR(1000), diagnosis TEXT, conclusion TEXT, instructions TEXT,
 author_user_id BIGINT NOT NULL, updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(owner_staff_id) REFERENCES staff(id), FOREIGN KEY(specialty_id) REFERENCES specialties(id),
 FOREIGN KEY(author_user_id) REFERENCES users(id), CHECK(state IN ('DRAFT','PUBLISHED','INACTIVE')),
 INDEX ix_template_scope(owner_staff_id,specialty_id,state)
) ENGINE=InnoDB;
CREATE TABLE clinical_template_versions (
 template_id BIGINT NOT NULL, version INT NOT NULL, snapshot JSON NOT NULL, author_user_id BIGINT NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), PRIMARY KEY(template_id,version),
 FOREIGN KEY(template_id) REFERENCES clinical_templates(id), FOREIGN KEY(author_user_id) REFERENCES users(id)
) ENGINE=InnoDB;
CREATE TABLE encounter_template_applications (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, encounter_id BIGINT NOT NULL, template_id BIGINT NOT NULL,
 template_version INT NOT NULL, applied_by BIGINT NOT NULL, applied_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(encounter_id) REFERENCES encounters(id), FOREIGN KEY(template_id,template_version) REFERENCES clinical_template_versions(template_id,version),
 FOREIGN KEY(applied_by) REFERENCES users(id), INDEX ix_template_encounter(encounter_id,id)
) ENGINE=InnoDB;
CREATE TABLE clinical_draft_requests (
 actor_user_id BIGINT NOT NULL, encounter_id BIGINT NOT NULL, kind VARCHAR(20) NOT NULL, request_id VARCHAR(100) NOT NULL,
 request_hash CHAR(64) NOT NULL, response JSON NOT NULL, saved_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(actor_user_id,encounter_id,kind,request_id), FOREIGN KEY(actor_user_id) REFERENCES users(id),
 FOREIGN KEY(encounter_id) REFERENCES encounters(id)
) ENGINE=InnoDB;
ALTER TABLE encounters ADD INDEX ix_patient_completed(patient_id,status,completed_at);
ALTER TABLE diagnostic_results ADD INDEX ix_result_published(order_id,status,version);
ALTER TABLE medication_batches ADD INDEX ix_batch_available(medication_id,counter_id,active,expiry_date);
