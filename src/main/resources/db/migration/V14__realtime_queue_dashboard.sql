ALTER TABLE appointments
    ADD COLUMN called_at DATETIME(6) NULL,
    ADD COLUMN call_count INT NOT NULL DEFAULT 0;
ALTER TABLE clinic_settings
    ADD COLUMN queue_display_enabled TINYINT(1) NOT NULL DEFAULT 1,
    ADD COLUMN queue_display_mask_names TINYINT(1) NOT NULL DEFAULT 1;
CREATE INDEX ix_appointments_queue ON appointments(status, checked_in_at);
