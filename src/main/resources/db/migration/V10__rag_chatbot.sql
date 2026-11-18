CREATE TABLE chatbot_config (
    id INT PRIMARY KEY,
    enabled TINYINT NOT NULL DEFAULT 0,
    chat_base_url VARCHAR(500) NOT NULL,
    chat_key BLOB NULL,
    chat_model VARCHAR(200) NOT NULL DEFAULT '',
    embedding_share_chat TINYINT NOT NULL DEFAULT 1,
    embedding_base_url VARCHAR(500) NOT NULL,
    embedding_key BLOB NULL,
    embedding_model VARCHAR(200) NOT NULL DEFAULT '',
    revision BIGINT NOT NULL DEFAULT 1,
    embedding_revision BIGINT NOT NULL DEFAULT 1,
    chat_tested_revision BIGINT NULL,
    embedding_tested_revision BIGINT NULL,
    dimensions INT NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
INSERT INTO chatbot_config(id,chat_base_url,embedding_base_url)
VALUES(1,'http://127.0.0.1:20128/v1','http://127.0.0.1:20128/v1');

CREATE TABLE knowledge_documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    slug VARCHAR(180) NOT NULL UNIQUE,
    kind VARCHAR(30) NOT NULL DEFAULT 'GUIDE',
    published_version_id BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
CREATE TABLE knowledge_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    embedding_revision BIGINT NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at TIMESTAMP(6) NULL,
    FOREIGN KEY(document_id) REFERENCES knowledge_documents(id),
    INDEX(document_id,status)
);
CREATE TABLE knowledge_chunks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id BIGINT NOT NULL,
    ordinal INT NOT NULL,
    content TEXT NOT NULL,
    point_id CHAR(36) NOT NULL UNIQUE,
    FOREIGN KEY(version_id) REFERENCES knowledge_versions(id) ON DELETE CASCADE,
    UNIQUE(version_id,ordinal)
);
CREATE TABLE knowledge_index_jobs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id BIGINT NOT NULL,
    embedding_revision BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'QUEUED',
    attempts INT NOT NULL DEFAULT 0,
    progress INT NOT NULL DEFAULT 0,
    total INT NOT NULL DEFAULT 0,
    error VARCHAR(500) NULL,
    available_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    lease_until TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY(version_id) REFERENCES knowledge_versions(id),
    INDEX(status,available_at)
);
CREATE TABLE chat_sessions (
    id CHAR(36) PRIMARY KEY,
    expires_at TIMESTAMP(6) NOT NULL,
    rate_window BIGINT NOT NULL DEFAULT 0,
    rate_count INT NOT NULL DEFAULT 0
);
CREATE TABLE chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id CHAR(36) NOT NULL,
    client_message_id CHAR(36) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    question TEXT NOT NULL,
    response_json MEDIUMTEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY(session_id) REFERENCES chat_sessions(id) ON DELETE CASCADE,
    UNIQUE(session_id,client_message_id)
);

INSERT INTO knowledge_documents(slug,kind) VALUES
('cach-dat-lich','FAQ'),('chon-bac-si','FAQ'),('doi-huy-lich','FAQ'),
('lich-lam-viec','FAQ'),('chuan-bi-kham','FAQ'),('tai-khoan-dat-lich','FAQ'),('thanh-toan-phi-kham','FAQ');
INSERT INTO knowledge_versions(document_id,title,content,status,published_at)
SELECT id,
 CASE slug
 WHEN 'cach-dat-lich' THEN 'Tôi đặt lịch khám như thế nào?'
 WHEN 'chon-bac-si' THEN 'Tôi có thể chọn bác sĩ khám cho mình không?'
 WHEN 'doi-huy-lich' THEN 'Tôi có thể đổi hoặc hủy lịch không?'
 WHEN 'lich-lam-viec' THEN 'Làm sao xem thời gian làm việc?'
 WHEN 'chuan-bi-kham' THEN 'Tôi cần chuẩn bị gì trước buổi khám?'
 WHEN 'tai-khoan-dat-lich' THEN 'Tôi chưa có tài khoản thì có đặt lịch được không?'
 ELSE 'Tôi thanh toán phí khám khi nào?' END,
 CASE slug
 WHEN 'cach-dat-lich' THEN 'Chọn chuyên khoa hoặc bác sĩ, mở lịch khám và chọn khoảng thời gian còn trống. Đăng nhập tài khoản bệnh nhân để đặt lịch. Mỗi khoảng dành riêng cho một bệnh nhân; đặt thành công là được xác nhận ngay.'
 WHEN 'chon-bac-si' THEN 'Có. Bạn có thể xem hồ sơ bác sĩ, dịch vụ và giá khám trước khi chọn lịch. Lịch trống được cập nhật theo các khoảng khám bác sĩ đã công bố.'
 WHEN 'doi-huy-lich' THEN 'Mở Lịch hẹn của tôi trong cổng bệnh nhân để đổi hoặc hủy trước giờ bắt đầu và trước khi được tiếp nhận. Khi đổi lịch không thành công, lịch cũ vẫn được giữ. Nếu bác sĩ nghỉ, lịch bị ảnh hưởng sẽ được đánh dấu cần đổi lịch để bạn chọn lại.'
 WHEN 'lich-lam-viec' THEN 'Lịch làm việc có thể khác nhau giữa các bác sĩ. Trang đặt lịch hiển thị các khoảng đã công bố theo ngày; hãy chọn ngày hoặc bác sĩ khác nếu chưa có lịch phù hợp.'
 WHEN 'chuan-bi-kham' THEN 'Mang theo giấy tờ tùy thân, kết quả khám trước đây và thông tin thuốc đang dùng nếu có. Kiểm tra thông tin lịch hẹn và làm theo hướng dẫn riêng của cơ sở đối với dịch vụ đã đặt.'
 WHEN 'tai-khoan-dat-lich' THEN 'Bạn có thể xem bác sĩ và lịch trống trước khi đăng nhập. Đăng ký và kích hoạt tài khoản qua email để đặt trực tuyến, hoặc nhờ lễ tân tạo hồ sơ và đặt hộ.'
 ELSE 'Phí khám được lập khi tiếp nhận và thanh toán trước khi bắt đầu khám. Bạn thanh toán tại cơ sở bằng tiền mặt hoặc chuyển khoản theo hướng dẫn của thu ngân.' END,
 'PUBLISHED',CURRENT_TIMESTAMP(6)
FROM knowledge_documents WHERE kind='FAQ';
UPDATE knowledge_documents d JOIN knowledge_versions v ON v.document_id=d.id
SET d.published_version_id=v.id WHERE d.kind='FAQ';
