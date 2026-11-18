ALTER TABLE clinic_settings ADD wait_payment_minutes INT NOT NULL DEFAULT 30,
 ADD wait_doctor_minutes INT NOT NULL DEFAULT 30, ADD wait_diagnostic_minutes INT NOT NULL DEFAULT 60;
CREATE TABLE encounter_stages (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, encounter_id BIGINT NOT NULL, stage VARCHAR(30) NOT NULL,
 started_at DATETIME(6) NOT NULL, ended_at DATETIME(6),
 FOREIGN KEY(encounter_id) REFERENCES encounters(id),
 CHECK(stage IN ('PAYMENT','DOCTOR','EXAMINING','DIAGNOSTICS','REVIEW','COMPLETED')),
 INDEX ix_stage_encounter(encounter_id,id), INDEX ix_stage_time(stage,started_at,ended_at)
) ENGINE=InnoDB;
CREATE TABLE serving_slot_history (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, slot_id BIGINT NOT NULL, doctor_staff_id BIGINT NOT NULL,
 department_id BIGINT NOT NULL, room_id BIGINT NOT NULL, service_id BIGINT NOT NULL,
 starts_at DATETIME(6) NOT NULL, ends_at DATETIME(6) NOT NULL, status VARCHAR(20) NOT NULL,
 recorded_at DATETIME(6) NOT NULL, history_complete TINYINT NOT NULL,
 INDEX ix_serving_slot(slot_id,id), INDEX ix_serving_time(starts_at,doctor_staff_id,room_id)
) ENGINE=InnoDB;
-- Capture a present-day baseline only. This is explicitly not a reconstructed history.
INSERT INTO serving_slot_history(slot_id,doctor_staff_id,department_id,room_id,service_id,starts_at,ends_at,status,recorded_at,history_complete)
 SELECT sl.id,sl.doctor_staff_id,s.department_id,sl.room_id,sl.service_id,sl.starts_at,sl.ends_at,sl.status,UTC_TIMESTAMP(6),0 FROM appointment_slots sl JOIN staff s ON s.id=sl.doctor_staff_id;
CREATE TABLE capacity_appointment_history (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, appointment_id BIGINT NOT NULL, slot_id BIGINT NOT NULL,
 doctor_staff_id BIGINT NOT NULL, room_id BIGINT NOT NULL, service_id BIGINT NOT NULL,
 starts_at DATETIME(6) NOT NULL, ends_at DATETIME(6) NOT NULL, status VARCHAR(30) NOT NULL,
 recorded_at DATETIME(6) NOT NULL, INDEX ix_capacity_appointment(appointment_id,id),
 INDEX ix_capacity_doctor_time(doctor_staff_id,starts_at)
) ENGINE=InnoDB;
INSERT INTO capacity_appointment_history(appointment_id,slot_id,doctor_staff_id,room_id,service_id,starts_at,ends_at,status,recorded_at)
 SELECT a.id,a.slot_id,s.doctor_staff_id,s.room_id,s.service_id,s.starts_at,s.ends_at,a.status,UTC_TIMESTAMP(6) FROM appointments a JOIN appointment_slots s ON s.id=a.slot_id;
CREATE TABLE capacity_exam_starts (
 encounter_id BIGINT PRIMARY KEY, appointment_id BIGINT NOT NULL, slot_id BIGINT NOT NULL,
 doctor_staff_id BIGINT NOT NULL, room_id BIGINT NOT NULL, service_id BIGINT NOT NULL,
 starts_at DATETIME(6) NOT NULL, examined_at DATETIME(6) NOT NULL,
 INDEX ix_exam_capacity(doctor_staff_id,starts_at)
) ENGINE=InnoDB;
DELIMITER $$
CREATE PROCEDURE record_encounter_stage(IN eid BIGINT, IN next_stage VARCHAR(30))
BEGIN
 DECLARE previous_stage VARCHAR(30);
 SELECT stage INTO previous_stage FROM encounter_stages WHERE encounter_id=eid ORDER BY id DESC LIMIT 1;
 IF previous_stage IS NULL OR previous_stage<>next_stage THEN
  UPDATE encounter_stages SET ended_at=UTC_TIMESTAMP(6) WHERE encounter_id=eid AND ended_at IS NULL;
  INSERT INTO encounter_stages(encounter_id,stage,started_at) VALUES(eid,next_stage,UTC_TIMESTAMP(6));
 END IF;
END$$
CREATE TRIGGER encounter_stage_insert AFTER INSERT ON encounters FOR EACH ROW
BEGIN
 CALL record_encounter_stage(NEW.id,IF(EXISTS(SELECT 1 FROM invoices WHERE appointment_id=NEW.appointment_id AND source_type='EXAMINATION' AND status='PAID'),'DOCTOR','PAYMENT'));
END$$
CREATE TRIGGER encounter_stage_update AFTER UPDATE ON encounters FOR EACH ROW
BEGIN
 IF NEW.status<>OLD.status THEN
  CALL record_encounter_stage(NEW.id,CASE NEW.status WHEN 'IN_PROGRESS' THEN 'EXAMINING' WHEN 'COMPLETED' THEN 'COMPLETED' WHEN 'WAITING_RESULTS' THEN IF(EXISTS(SELECT 1 FROM diagnostic_orders WHERE encounter_id=NEW.id AND status NOT IN ('COMPLETED','CANCELLED')),'DIAGNOSTICS','REVIEW') ELSE 'DOCTOR' END);
 END IF;
 IF OLD.started_at IS NULL AND NEW.started_at IS NOT NULL THEN
  INSERT IGNORE INTO capacity_exam_starts(encounter_id,appointment_id,slot_id,doctor_staff_id,room_id,service_id,starts_at,examined_at)
   SELECT NEW.id,NEW.appointment_id,s.id,NEW.doctor_staff_id,s.room_id,s.service_id,s.starts_at,NEW.started_at FROM appointments a JOIN appointment_slots s ON s.id=a.slot_id WHERE a.id=NEW.appointment_id;
 END IF;
END$$
CREATE TRIGGER invoice_stage_update AFTER UPDATE ON invoices FOR EACH ROW
BEGIN
 DECLARE eid BIGINT;
 IF NEW.source_type='EXAMINATION' AND NEW.status='PAID' AND OLD.status<>'PAID' THEN
  SELECT id INTO eid FROM encounters WHERE appointment_id=NEW.appointment_id AND status='WAITING';
  IF eid IS NOT NULL THEN CALL record_encounter_stage(eid,'DOCTOR'); END IF;
 END IF;
END$$
CREATE TRIGGER diagnostic_stage_update AFTER UPDATE ON diagnostic_orders FOR EACH ROW
BEGIN
 IF NEW.status<>OLD.status AND EXISTS(SELECT 1 FROM encounters WHERE id=NEW.encounter_id AND status='WAITING_RESULTS') THEN
  CALL record_encounter_stage(NEW.encounter_id,IF(EXISTS(SELECT 1 FROM diagnostic_orders WHERE encounter_id=NEW.encounter_id AND status NOT IN ('COMPLETED','CANCELLED')),'DIAGNOSTICS','REVIEW'));
 END IF;
END$$
CREATE TRIGGER serving_slot_insert AFTER INSERT ON appointment_slots FOR EACH ROW
BEGIN
 INSERT INTO serving_slot_history(slot_id,doctor_staff_id,department_id,room_id,service_id,starts_at,ends_at,status,recorded_at,history_complete)
  SELECT NEW.id,NEW.doctor_staff_id,s.department_id,NEW.room_id,NEW.service_id,NEW.starts_at,NEW.ends_at,NEW.status,UTC_TIMESTAMP(6),1 FROM staff s WHERE s.id=NEW.doctor_staff_id;
END$$
CREATE TRIGGER serving_slot_update AFTER UPDATE ON appointment_slots FOR EACH ROW
BEGIN
 IF NEW.status<>OLD.status OR NEW.doctor_staff_id<>OLD.doctor_staff_id OR NEW.room_id<>OLD.room_id OR NEW.service_id<>OLD.service_id OR NEW.starts_at<>OLD.starts_at OR NEW.ends_at<>OLD.ends_at THEN
  INSERT INTO serving_slot_history(slot_id,doctor_staff_id,department_id,room_id,service_id,starts_at,ends_at,status,recorded_at,history_complete)
   SELECT NEW.id,NEW.doctor_staff_id,s.department_id,NEW.room_id,NEW.service_id,NEW.starts_at,NEW.ends_at,NEW.status,UTC_TIMESTAMP(6),1 FROM staff s WHERE s.id=NEW.doctor_staff_id;
 END IF;
END$$
CREATE TRIGGER capacity_appointment_insert AFTER INSERT ON appointments FOR EACH ROW
BEGIN
 INSERT INTO capacity_appointment_history(appointment_id,slot_id,doctor_staff_id,room_id,service_id,starts_at,ends_at,status,recorded_at)
  SELECT NEW.id,s.id,s.doctor_staff_id,s.room_id,s.service_id,s.starts_at,s.ends_at,NEW.status,UTC_TIMESTAMP(6) FROM appointment_slots s WHERE s.id=NEW.slot_id;
END$$
CREATE TRIGGER capacity_appointment_update AFTER UPDATE ON appointments FOR EACH ROW
BEGIN
 IF NEW.status<>OLD.status OR NEW.slot_id<>OLD.slot_id THEN
  INSERT INTO capacity_appointment_history(appointment_id,slot_id,doctor_staff_id,room_id,service_id,starts_at,ends_at,status,recorded_at)
   SELECT NEW.id,s.id,s.doctor_staff_id,s.room_id,s.service_id,s.starts_at,s.ends_at,NEW.status,UTC_TIMESTAMP(6) FROM appointment_slots s WHERE s.id=NEW.slot_id;
 END IF;
END$$
DELIMITER ;
