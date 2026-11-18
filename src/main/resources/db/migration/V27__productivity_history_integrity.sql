ALTER TABLE clinical_template_versions ADD published_at DATETIME(6) NULL;
ALTER TABLE patient_allergies ADD recorded_at DATETIME(6) NULL;
UPDATE clinical_template_versions v JOIN clinical_templates t ON t.id=v.template_id
 SET v.published_at=v.created_at
 WHERE (t.owner_staff_id IS NOT NULL AND JSON_UNQUOTE(JSON_EXTRACT(v.snapshot,'$.state'))='PUBLISHED')
 OR (t.owner_staff_id IS NULL AND t.state='PUBLISHED' AND t.version=v.version);
CREATE TABLE appointment_coordination_archive LIKE appointment_coordination;
ALTER TABLE appointment_coordination_archive DROP PRIMARY KEY, ADD PRIMARY KEY(absence_id,appointment_id);
DELIMITER $$
CREATE TRIGGER diagnostic_stage_insert AFTER INSERT ON diagnostic_orders FOR EACH ROW
BEGIN
 IF EXISTS(SELECT 1 FROM encounters WHERE id=NEW.encounter_id AND status='WAITING_RESULTS') THEN
  CALL record_encounter_stage(NEW.encounter_id,'DIAGNOSTICS');
 END IF;
END$$
DELIMITER ;
