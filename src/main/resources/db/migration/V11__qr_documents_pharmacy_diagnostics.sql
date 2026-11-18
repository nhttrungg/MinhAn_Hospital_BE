CREATE TABLE pharmacy_counters (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(30) NOT NULL UNIQUE, name VARCHAR(200) NOT NULL,
 room_id BIGINT NOT NULL, active TINYINT NOT NULL DEFAULT 1, bank_bin VARCHAR(6), account_no VARCHAR(19), account_name VARCHAR(100),
 revision INT NOT NULL DEFAULT 0, FOREIGN KEY(room_id) REFERENCES rooms(id)
) ENGINE=InnoDB;
CREATE TABLE counter_staff (
 counter_id BIGINT NOT NULL, staff_id BIGINT NOT NULL, PRIMARY KEY(counter_id,staff_id),
 FOREIGN KEY(counter_id) REFERENCES pharmacy_counters(id), FOREIGN KEY(staff_id) REFERENCES staff(id)
) ENGINE=InnoDB;
CREATE TABLE medication_prices (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, counter_id BIGINT NOT NULL, medication_id BIGINT NOT NULL,
 price DECIMAL(15,2) NOT NULL, effective_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(counter_id) REFERENCES pharmacy_counters(id), FOREIGN KEY(medication_id) REFERENCES medications(id),
 CHECK(price>=0), INDEX ix_counter_price(counter_id,medication_id,id)
) ENGINE=InnoDB;
CREATE TABLE medication_batches (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, counter_id BIGINT NOT NULL, medication_id BIGINT NOT NULL, batch_code VARCHAR(100) NOT NULL,
 expiry_date DATE NOT NULL, quantity DECIMAL(12,2) NOT NULL DEFAULT 0, reserved DECIMAL(12,2) NOT NULL DEFAULT 0,
 active TINYINT NOT NULL DEFAULT 1, revision INT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_counter_batch(counter_id,medication_id,batch_code,expiry_date),
 FOREIGN KEY(counter_id) REFERENCES pharmacy_counters(id), FOREIGN KEY(medication_id) REFERENCES medications(id),
 CHECK(quantity>=0 AND reserved>=0 AND reserved<=quantity)
) ENGINE=InnoDB;
CREATE TABLE pharmacy_orders (
 encounter_id BIGINT PRIMARY KEY, prescription_id BIGINT NOT NULL, counter_id BIGINT NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'OPEN', revision INT NOT NULL DEFAULT 0, close_reason VARCHAR(500),
 FOREIGN KEY(encounter_id) REFERENCES encounters(id), FOREIGN KEY(prescription_id) REFERENCES prescriptions(id),
 FOREIGN KEY(counter_id) REFERENCES pharmacy_counters(id), CHECK(status IN ('OPEN','CLOSED'))
) ENGINE=InnoDB;
CREATE TABLE dispensings (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, encounter_id BIGINT NOT NULL, prescription_id BIGINT NOT NULL, counter_id BIGINT NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', revision INT NOT NULL DEFAULT 0, shortage_reason VARCHAR(500),
 prepared_by BIGINT NOT NULL, delivered_by BIGINT, prepared_at DATETIME(6), delivered_at DATETIME(6),
 FOREIGN KEY(encounter_id) REFERENCES encounters(id), FOREIGN KEY(prescription_id) REFERENCES prescriptions(id),
 FOREIGN KEY(counter_id) REFERENCES pharmacy_counters(id), FOREIGN KEY(prepared_by) REFERENCES users(id),
 FOREIGN KEY(delivered_by) REFERENCES users(id), CHECK(status IN ('DRAFT','READY','DELIVERED','CANCELLED')),
 INDEX ix_dispensing_order(encounter_id,status)
) ENGINE=InnoDB;
CREATE TABLE dispensing_lines (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, dispensing_id BIGINT NOT NULL, prescription_line_id BIGINT NOT NULL,
 batch_id BIGINT NOT NULL, quantity DECIMAL(10,2) NOT NULL, unit_price DECIMAL(15,2), checked TINYINT NOT NULL,
 FOREIGN KEY(dispensing_id) REFERENCES dispensings(id), FOREIGN KEY(prescription_line_id) REFERENCES prescription_lines(id),
 FOREIGN KEY(batch_id) REFERENCES medication_batches(id), CHECK(quantity>0), UNIQUE KEY uk_dispensing_line(dispensing_id,prescription_line_id,batch_id)
) ENGINE=InnoDB;
CREATE TABLE workflow_events (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, entity_type VARCHAR(30) NOT NULL, entity_id BIGINT NOT NULL,
 action VARCHAR(40) NOT NULL, actor_user_id BIGINT NOT NULL, counter_id BIGINT, details JSON NOT NULL,
 occurred_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), FOREIGN KEY(actor_user_id) REFERENCES users(id),
 INDEX ix_trace(entity_type,entity_id,id)
) ENGINE=InnoDB;
CREATE TABLE stock_movements (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, batch_id BIGINT NOT NULL, action VARCHAR(20) NOT NULL,
 quantity_delta DECIMAL(12,2) NOT NULL, reserved_delta DECIMAL(12,2) NOT NULL, quantity_after DECIMAL(12,2) NOT NULL,
 reserved_after DECIMAL(12,2) NOT NULL, dispensing_id BIGINT, actor_user_id BIGINT NOT NULL, reason VARCHAR(500) NOT NULL,
 occurred_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), FOREIGN KEY(batch_id) REFERENCES medication_batches(id),
 FOREIGN KEY(dispensing_id) REFERENCES dispensings(id), FOREIGN KEY(actor_user_id) REFERENCES users(id)
) ENGINE=InnoDB;
CREATE TABLE diagnostic_staff (
 room_id BIGINT NOT NULL, staff_id BIGINT NOT NULL, can_review TINYINT NOT NULL DEFAULT 0,
 PRIMARY KEY(room_id,staff_id), FOREIGN KEY(room_id) REFERENCES rooms(id), FOREIGN KEY(staff_id) REFERENCES staff(id)
) ENGINE=InnoDB;
CREATE TABLE diagnostic_definitions (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, service_id BIGINT NOT NULL, name VARCHAR(200) NOT NULL,
 unit VARCHAR(50), reference_range VARCHAR(200), sort_order INT NOT NULL DEFAULT 0,
 FOREIGN KEY(service_id) REFERENCES services(id)
) ENGINE=InnoDB;
CREATE TABLE diagnostic_orders (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, encounter_id BIGINT NOT NULL, service_id BIGINT NOT NULL, room_id BIGINT NOT NULL,
 category VARCHAR(20) NOT NULL, status VARCHAR(30) NOT NULL DEFAULT 'ISSUED', revision INT NOT NULL DEFAULT 0,
 notes TEXT, issued_by BIGINT NOT NULL, issued_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), accepted_at DATETIME(6),
 FOREIGN KEY(encounter_id) REFERENCES encounters(id), FOREIGN KEY(service_id) REFERENCES services(id),
 FOREIGN KEY(room_id) REFERENCES rooms(id), FOREIGN KEY(issued_by) REFERENCES users(id),
 CHECK(status IN ('ISSUED','ACCEPTED','IN_PROGRESS','COMPLETED','CANCELLED')), INDEX ix_diagnostic_encounter(encounter_id,status)
) ENGINE=InnoDB;
CREATE TABLE diagnostic_samples (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'COLLECTED',
 collected_by BIGINT NOT NULL, collected_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), rejected_reason VARCHAR(500),
 FOREIGN KEY(order_id) REFERENCES diagnostic_orders(id), FOREIGN KEY(collected_by) REFERENCES users(id), CHECK(status IN ('COLLECTED','REJECTED'))
) ENGINE=InnoDB;
CREATE TABLE diagnostic_results (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, version INT NOT NULL, revision INT NOT NULL DEFAULT 0,
 status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', measurements JSON NOT NULL, description TEXT, conclusion TEXT, reason VARCHAR(500),
 author_id BIGINT NOT NULL, reviewer_id BIGINT, published_at DATETIME(6),
 FOREIGN KEY(order_id) REFERENCES diagnostic_orders(id), FOREIGN KEY(author_id) REFERENCES users(id),
 FOREIGN KEY(reviewer_id) REFERENCES users(id), UNIQUE KEY uk_result_version(order_id,version), CHECK(status IN ('DRAFT','SUBMITTED','PUBLISHED'))
) ENGINE=InnoDB;
CREATE TABLE diagnostic_attachments (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, result_id BIGINT NOT NULL, filename VARCHAR(200) NOT NULL, content_type VARCHAR(50) NOT NULL,
 content LONGBLOB NOT NULL, uploaded_by BIGINT NOT NULL,
 FOREIGN KEY(result_id) REFERENCES diagnostic_results(id), FOREIGN KEY(uploaded_by) REFERENCES users(id)
) ENGINE=InnoDB;
ALTER TABLE invoices ADD INDEX ix_invoice_appointment(appointment_id);
ALTER TABLE invoices DROP INDEX appointment_id;
ALTER TABLE invoices ADD COLUMN source_type VARCHAR(30) NOT NULL DEFAULT 'EXAMINATION',
 ADD COLUMN source_id BIGINT NULL, ADD COLUMN counter_id BIGINT NULL, ADD COLUMN revision INT NOT NULL DEFAULT 0,
 ADD COLUMN bank_snapshot JSON NULL, ADD CONSTRAINT fk_invoice_counter FOREIGN KEY(counter_id) REFERENCES pharmacy_counters(id);
UPDATE invoices SET source_id=appointment_id WHERE appointment_id IS NOT NULL;
ALTER TABLE invoices ADD UNIQUE KEY uk_invoice_origin(source_type,source_id);
ALTER TABLE invoices DROP CHECK chk_invoice_status;
ALTER TABLE invoices ADD CONSTRAINT chk_invoice_status CHECK(status IN ('OPEN','PARTIAL','PAID','VOID'));
ALTER TABLE receipts ADD COLUMN transaction_reference VARCHAR(100), ADD COLUMN note VARCHAR(500);
CREATE TABLE refunds (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, invoice_id BIGINT NOT NULL, amount DECIMAL(15,2) NOT NULL,
 method VARCHAR(20) NOT NULL, reason VARCHAR(500) NOT NULL, transaction_reference VARCHAR(100), actor_user_id BIGINT NOT NULL,
 recorded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), FOREIGN KEY(invoice_id) REFERENCES invoices(id),
 FOREIGN KEY(actor_user_id) REFERENCES users(id), CHECK(amount>0), CHECK(method IN ('CASH','TRANSFER'))
) ENGINE=InnoDB;
CREATE TABLE business_qr (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, token VARCHAR(64) NOT NULL UNIQUE, kind VARCHAR(30) NOT NULL, source_id BIGINT NOT NULL,
 source_revision INT NOT NULL, revoked TINYINT NOT NULL DEFAULT 0, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_qr_source(kind,source_id,source_revision)
) ENGINE=InnoDB;
CREATE TABLE issued_documents (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, type VARCHAR(40) NOT NULL, source_id BIGINT NOT NULL, source_version VARCHAR(60) NOT NULL,
 template_version VARCHAR(30) NOT NULL, patient_id BIGINT NOT NULL, snapshot JSON NOT NULL, pdf LONGBLOB NOT NULL,
 issued_by BIGINT NOT NULL, issued_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(patient_id) REFERENCES patient_profiles(id), FOREIGN KEY(issued_by) REFERENCES users(id),
 UNIQUE KEY uk_document_source(type,source_id,source_version,template_version)
) ENGINE=InnoDB;
CREATE TABLE workflow_requests (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, actor_user_id BIGINT NOT NULL, operation VARCHAR(50) NOT NULL,
 request_key VARCHAR(100) NOT NULL, request_hash VARCHAR(64) NOT NULL, response JSON NOT NULL,
 UNIQUE KEY uk_workflow_request(actor_user_id,operation,request_key)
) ENGINE=InnoDB;
