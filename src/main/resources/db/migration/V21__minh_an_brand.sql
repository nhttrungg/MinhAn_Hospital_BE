-- Keep earlier migration checksums intact for existing installations.
UPDATE clinic_settings
SET clinic_name = 'Bệnh viện Minh An'
WHERE id = 1 AND clinic_name IN ('Bệnh viện An Tâm', 'An Tâm Clinic', 'An Tâm', 'Phòng khám An Tâm');

-- Refresh public demo copy seeded before the hospital was renamed.
UPDATE doctor_profiles SET bio = REPLACE(bio, 'An Tâm', 'Minh An') WHERE bio LIKE '%An Tâm%';
UPDATE departments SET description = REPLACE(description, 'An Tâm', 'Minh An') WHERE description LIKE '%An Tâm%';
UPDATE services SET description = REPLACE(description, 'An Tâm', 'Minh An') WHERE description LIKE '%An Tâm%';
UPDATE rooms SET description = REPLACE(description, 'An Tâm', 'Minh An') WHERE description LIKE '%An Tâm%';

-- Change branding in the currently published public guides and their passages.
-- Keep retired versions and private clinical/payment records unchanged.
UPDATE knowledge_versions v JOIN knowledge_documents d ON d.published_version_id = v.id
SET v.title = REPLACE(v.title, 'An Tâm', 'Minh An'), v.content = REPLACE(v.content, 'An Tâm', 'Minh An')
WHERE v.title LIKE '%An Tâm%' OR v.content LIKE '%An Tâm%';
UPDATE knowledge_chunks c JOIN knowledge_documents d ON d.published_version_id = c.version_id
SET c.content = REPLACE(c.content, 'An Tâm', 'Minh An') WHERE c.content LIKE '%An Tâm%';
