-- Add the missing workflow FAQ without replacing administrator-authored documents.
INSERT INTO knowledge_documents(slug,kind)
SELECT 'sau-khi-dat-lich','FAQ'
WHERE NOT EXISTS (SELECT 1 FROM knowledge_documents WHERE slug='sau-khi-dat-lich');

INSERT INTO knowledge_versions(document_id,title,content,status,published_at)
SELECT d.id,
       'Sau khi đặt lịch khám thành công, tôi cần làm gì?',
       'Lịch được xác nhận ngay khi đặt thành công; bạn không cần đặt lại. Đăng nhập và mở Lịch hẹn của tôi trong cổng bệnh nhân để kiểm tra ngày, giờ, bác sĩ, dịch vụ và lưu mã lịch khám hoặc mã QR. Chuẩn bị giấy tờ tùy thân, đơn thuốc và kết quả khám hoặc xét nghiệm gần đây nếu có. Đến cơ sở sớm 10–15 phút để làm thủ tục; cung cấp mã lịch hẹn hoặc mã QR cho lễ tân để được tiếp nhận. Phí khám được lập khi tiếp nhận và thanh toán trước khi bắt đầu khám theo hướng dẫn của thu ngân. Nếu cần đổi hoặc hủy, thực hiện trong Lịch hẹn của tôi trước giờ khám và trước khi được tiếp nhận.',
       'PUBLISHED',CURRENT_TIMESTAMP(6)
FROM knowledge_documents d
WHERE d.slug='sau-khi-dat-lich'
  AND NOT EXISTS (SELECT 1 FROM knowledge_versions v WHERE v.document_id=d.id);

UPDATE knowledge_documents d
JOIN knowledge_versions v ON v.document_id=d.id AND v.status='PUBLISHED'
SET d.published_version_id=v.id
WHERE d.slug='sau-khi-dat-lich' AND d.published_version_id IS NULL;
