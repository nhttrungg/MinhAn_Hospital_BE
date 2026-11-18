-- F7/F8. Demonstration catalog data is loaded separately; never a clinical knowledge base.
CREATE TABLE icd10_codes (
 code VARCHAR(10) PRIMARY KEY, name_vi VARCHAR(300) NOT NULL, name_en VARCHAR(300),
 chapter VARCHAR(10), active TINYINT(1) NOT NULL DEFAULT 1,
 source_system VARCHAR(40) NOT NULL DEFAULT 'DEMO', source_url VARCHAR(1000),
 is_demo TINYINT(1) NOT NULL DEFAULT 1,
 FULLTEXT KEY ft_icd(name_vi,name_en)
);
CREATE TABLE encounter_diagnoses (
 encounter_id BIGINT NOT NULL, icd_code VARCHAR(10) NOT NULL, is_primary TINYINT(1) NOT NULL DEFAULT 0,
 PRIMARY KEY(encounter_id,icd_code),
 FOREIGN KEY(encounter_id) REFERENCES encounters(id), FOREIGN KEY(icd_code) REFERENCES icd10_codes(code)
);
CREATE TABLE patient_allergies (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, patient_id BIGINT NOT NULL,
 ingredient VARCHAR(200) NOT NULL, reaction VARCHAR(300), severity VARCHAR(20) NOT NULL DEFAULT 'MODERATE',
 revision INT NOT NULL DEFAULT 0,
 FOREIGN KEY(patient_id) REFERENCES patient_profiles(id), INDEX ix_allergies_patient(patient_id),
 CONSTRAINT chk_allergy_severity CHECK(severity IN ('MILD','MODERATE','MAJOR'))
);
CREATE TABLE drug_interactions (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, ingredient_a VARCHAR(200) NOT NULL, ingredient_b VARCHAR(200) NOT NULL,
 severity VARCHAR(20) NOT NULL, note VARCHAR(500) NOT NULL,
 source_url VARCHAR(1000), is_demo TINYINT(1) NOT NULL DEFAULT 1,
 UNIQUE KEY uk_pair(ingredient_a,ingredient_b),
 CONSTRAINT chk_interaction_severity CHECK(severity IN ('MILD','MODERATE','MAJOR'))
);
ALTER TABLE prescriptions ADD COLUMN safety_override_reason VARCHAR(500) NULL,
 ADD COLUMN safety_warnings_json JSON NULL;
ALTER TABLE medications ADD COLUMN reorder_level INT NOT NULL DEFAULT 0,
 ADD CONSTRAINT chk_medication_reorder CHECK(reorder_level>=0);
ALTER TABLE clinic_settings ADD COLUMN near_expiry_days INT NOT NULL DEFAULT 60,
 ADD CONSTRAINT chk_near_expiry_days CHECK(near_expiry_days BETWEEN 0 AND 3650);
