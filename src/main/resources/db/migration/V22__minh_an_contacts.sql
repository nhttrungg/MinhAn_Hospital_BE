-- Public contact information verified at https://benhvienminhan.com/ on 2026-10-05.
ALTER TABLE clinic_settings ADD COLUMN website VARCHAR(500);

CREATE TABLE clinic_contact_channels (
    clinic_id BIGINT NOT NULL,
    code VARCHAR(50) NOT NULL,
    label VARCHAR(200) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    sort_order INT NOT NULL,
    PRIMARY KEY (clinic_id, code),
    CONSTRAINT fk_contact_clinic FOREIGN KEY (clinic_id) REFERENCES clinic_settings(id)
) ENGINE=InnoDB;

UPDATE clinic_settings
SET clinic_name = 'Bệnh viện đa khoa Minh An',
    address = 'Quốc lộ 1A, Quỳnh Giang, Quỳnh Lưu, Nghệ An',
    phone = '1900 866 823',
    email = 'benhvienminhan@gmail.com',
    website = 'https://benhvienminhan.com/',
    working_hours = '07:00–17:00, thứ Hai–Chủ nhật. Khám BHYT: 06:45–17:30, kể cả ngày lễ, Tết.'
WHERE id = 1 AND clinic_name IN ('Bệnh viện Minh An', 'Bệnh viện đa khoa Minh An');

INSERT INTO clinic_contact_channels (clinic_id, code, label, phone, sort_order)
SELECT id, 'EMERGENCY', 'Cấp cứu (24/24)', '0981 154 115', 1 FROM clinic_settings WHERE id = 1 AND website = 'https://benhvienminhan.com/'
UNION ALL SELECT id, 'SURGERY', 'Khoa Ngoại', '0868 546 066', 2 FROM clinic_settings WHERE id = 1 AND website = 'https://benhvienminhan.com/'
UNION ALL SELECT id, 'OBSTETRICS', 'Khoa Sản', '0868 546 024', 3 FROM clinic_settings WHERE id = 1 AND website = 'https://benhvienminhan.com/'
UNION ALL SELECT id, 'INTERNAL_PEDIATRICS', 'Khoa Nội - Nhi', '0868 546 026', 4 FROM clinic_settings WHERE id = 1 AND website = 'https://benhvienminhan.com/'
UNION ALL SELECT id, 'TRADITIONAL_REHAB', 'Khoa YHCT & PHCN', '0365 483 801', 5 FROM clinic_settings WHERE id = 1 AND website = 'https://benhvienminhan.com/'
UNION ALL SELECT id, 'MULTISPECIALTY', 'Khoa Liên chuyên khoa', '0868 546 025', 6 FROM clinic_settings WHERE id = 1 AND website = 'https://benhvienminhan.com/';
