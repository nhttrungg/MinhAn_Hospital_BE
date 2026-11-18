ALTER TABLE diagnostic_results ADD COLUMN sample_id BIGINT NULL,
 ADD CONSTRAINT fk_result_sample FOREIGN KEY(sample_id) REFERENCES diagnostic_samples(id);
UPDATE diagnostic_results r JOIN diagnostic_orders o ON o.id=r.order_id
SET r.sample_id=(SELECT MAX(s.id) FROM diagnostic_samples s WHERE s.order_id=o.id AND s.status='COLLECTED')
WHERE o.category='LAB';
