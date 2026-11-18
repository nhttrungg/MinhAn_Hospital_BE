SET NAMES utf8mb4;

-- Mật khẩu demo của mọi tài khoản mẫu: Demo@12345
SET @pwd = '$2b$12$AwlQLTrpYIBkfm8.um4JO.K4v2ynfT43C3/Q1PWUe09VETjlPaHdy';

INSERT INTO roles (id, code, name) VALUES
    (1, 'ADMIN', 'Quản trị viên'),
    (2, 'RECEPTIONIST', 'Lễ tân'),
    (3, 'DOCTOR', 'Bác sĩ'),
    (4, 'NURSE', 'Điều dưỡng'),
    (5, 'LAB_TECH', 'Kỹ thuật viên xét nghiệm'),
    (6, 'PHARMACIST', 'Dược sĩ'),
    (7, 'CASHIER', 'Thu ngân'),
    (8, 'PATIENT', 'Bệnh nhân');

INSERT INTO departments (id, code, name, description) VALUES
    (1, 'KB', 'Khoa Khám bệnh', 'Tiếp nhận và khám ngoại trú'),
    (2, 'NOI', 'Khoa Nội', 'Điều trị nội khoa và nội trú'),
    (3, 'NGOAI', 'Khoa Ngoại', 'Khám và điều trị ngoại khoa'),
    (4, 'XN', 'Khoa Xét nghiệm', 'Lấy mẫu và trả kết quả xét nghiệm'),
    (5, 'DUOC', 'Khoa Dược', 'Kho thuốc và cấp phát'),
    (6, 'TC', 'Phòng Tài chính', 'Viện phí và thu ngân'),
    (7, 'QT', 'Phòng Quản trị', 'Quản trị hệ thống và danh mục');

INSERT INTO users (id, username, password_hash, full_name, email, phone, user_type, status) VALUES
    (1, 'admin', @pwd, 'Quản trị hệ thống', 'admin@demo.antam.vn', '0901000001', 'STAFF', 'ACTIVE'),
    (2, 'letan01', @pwd, 'Phạm Thu Hà', 'letan01@demo.antam.vn', '0901000002', 'STAFF', 'ACTIVE'),
    (3, 'bacsi01', @pwd, 'Nguyễn Minh Khoa', 'bacsi01@demo.antam.vn', '0901000003', 'STAFF', 'ACTIVE'),
    (4, 'bacsi02', @pwd, 'Lê Quốc Bảo', 'bacsi02@demo.antam.vn', '0901000004', 'STAFF', 'ACTIVE'),
    (5, 'dieuduong01', @pwd, 'Trần Ngọc Ánh', 'dieuduong01@demo.antam.vn', '0901000005', 'STAFF', 'ACTIVE'),
    (6, 'ktv01', @pwd, 'Đỗ Gia Huy', 'ktv01@demo.antam.vn', '0901000006', 'STAFF', 'ACTIVE'),
    (7, 'duocsi01', @pwd, 'Võ Thanh Tâm', 'duocsi01@demo.antam.vn', '0901000007', 'STAFF', 'ACTIVE'),
    (8, 'thungan01', @pwd, 'Bùi Khánh Linh', 'thungan01@demo.antam.vn', '0901000008', 'STAFF', 'ACTIVE'),
    (9, 'benhnhan01', @pwd, 'Trần Thị Mai', 'benhnhan01@demo.antam.vn', '0901000009', 'PATIENT', 'ACTIVE');

INSERT INTO user_roles (user_id, role_id) VALUES
    (1, 1),
    (2, 2),
    (3, 3),
    (4, 3),
    (5, 4),
    (6, 5),
    (7, 6),
    (8, 7),
    (9, 8);

INSERT INTO staff (user_id, employee_code, department_id, title) VALUES
    (1, 'QT001', 7, 'Quản trị viên'),
    (2, 'LT001', 1, 'Lễ tân'),
    (3, 'BS001', 2, 'Bác sĩ nội'),
    (4, 'BS002', 3, 'Bác sĩ ngoại'),
    (5, 'DD001', 2, 'Điều dưỡng'),
    (6, 'KX001', 4, 'Kỹ thuật viên'),
    (7, 'DS001', 5, 'Dược sĩ'),
    (8, 'TN001', 6, 'Thu ngân');

INSERT INTO rooms (id, department_id, code, name, room_type) VALUES
    (1, 1, 'P101', 'Phòng khám 01', 'CLINIC'),
    (2, 1, 'P102', 'Phòng khám 02', 'CLINIC'),
    (3, 2, 'N201', 'Buồng Nội 201', 'WARD'),
    (4, 2, 'N202', 'Buồng Nội 202', 'WARD'),
    (5, 3, 'G301', 'Buồng Ngoại 301', 'WARD'),
    (6, 4, 'XN01', 'Phòng lấy mẫu', 'LAB'),
    (7, 5, 'DUOC01', 'Quầy thuốc', 'PHARMACY'),
    (8, 6, 'TC01', 'Quầy thu ngân', 'CASHIER');

INSERT INTO beds (room_id, code, status) VALUES
    (3, 'G01', 'AVAILABLE'),
    (3, 'G02', 'AVAILABLE'),
    (3, 'G03', 'MAINTENANCE'),
    (4, 'G01', 'AVAILABLE'),
    (5, 'G01', 'AVAILABLE'),
    (5, 'G02', 'AVAILABLE');

INSERT INTO services (id, code, name, category, unit) VALUES
    (1, 'KB-NOI', 'Khám nội tổng quát', 'EXAMINATION', 'lượt'),
    (2, 'KB-NGOAI', 'Khám ngoại tổng quát', 'EXAMINATION', 'lượt'),
    (3, 'XN-CTM', 'Công thức máu', 'LAB', 'mẫu'),
    (4, 'XN-SH', 'Sinh hóa máu cơ bản', 'LAB', 'mẫu'),
    (5, 'GB-THUONG', 'Giường nội trú thường', 'BED', 'ngày'),
    (6, 'GB-VIP', 'Giường nội trú VIP', 'BED', 'ngày');

INSERT INTO service_prices (service_id, unit_price, effective_from) VALUES
    (1, 150000.00, '2026-01-01'),
    (2, 150000.00, '2026-01-01'),
    (3, 80000.00, '2026-01-01'),
    (4, 180000.00, '2026-01-01'),
    (5, 300000.00, '2026-01-01'),
    (6, 800000.00, '2026-01-01');
