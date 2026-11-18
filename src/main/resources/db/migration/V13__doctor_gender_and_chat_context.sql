ALTER TABLE doctor_profiles
    ADD COLUMN gender VARCHAR(10) NULL,
    ADD CONSTRAINT chk_doctor_profiles_gender CHECK (gender IS NULL OR gender IN ('MALE','FEMALE','OTHER'));

ALTER TABLE chat_sessions ADD COLUMN lookup_context_json JSON NULL;
