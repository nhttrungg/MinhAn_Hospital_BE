ALTER TABLE users
 ADD COLUMN totp_secret VARBINARY(512) NULL,
 ADD COLUMN totp_enabled TINYINT(1) NOT NULL DEFAULT 0,
 ADD COLUMN totp_last_step BIGINT NOT NULL DEFAULT -1,
 ADD COLUMN failed_login_count INT NOT NULL DEFAULT 0,
 ADD COLUMN locked_until DATETIME(6) NULL;
CREATE TABLE user_recovery_codes (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,
 code_hash CHAR(64) NOT NULL, used_at DATETIME(6) NULL,
 UNIQUE KEY uk_recovery_user_hash(user_id,code_hash),
 FOREIGN KEY(user_id) REFERENCES users(id)
) ENGINE=InnoDB;
CREATE TABLE payment_intents (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, invoice_id BIGINT NOT NULL,
 reference_code VARCHAR(20) NOT NULL UNIQUE, amount DECIMAL(15,2) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING', expires_at DATETIME(6) NOT NULL,
 provider_txn_id VARCHAR(100) NULL UNIQUE, paid_at DATETIME(6) NULL, receipt_id BIGINT NULL,
 created_by BIGINT NOT NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 qr_payload TEXT NOT NULL,
 CONSTRAINT chk_intent_status CHECK(status IN ('PENDING','PAID','EXPIRED','MISMATCH')),
 CONSTRAINT chk_intent_amount CHECK(amount>0),
 INDEX ix_intent_invoice(invoice_id,status), INDEX ix_intent_expiry(status,expires_at),
 FOREIGN KEY(invoice_id) REFERENCES invoices(id), FOREIGN KEY(created_by) REFERENCES users(id),
 FOREIGN KEY(receipt_id) REFERENCES receipts(id)
) ENGINE=InnoDB;
-- One ledger serializes provider transaction claims across invoices and retains unmatched transfers.
CREATE TABLE online_payment_transactions (
 provider_txn_id VARCHAR(100) PRIMARY KEY, request_hash CHAR(64) NOT NULL,
 intent_id BIGINT NULL, amount DECIMAL(15,2) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
 received_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(intent_id) REFERENCES payment_intents(id),
 CHECK(status IN ('RECEIVED','PAID','MISMATCH','UNMATCHED'))
) ENGINE=InnoDB;
ALTER TABLE receipts MODIFY method VARCHAR(30) NOT NULL;
ALTER TABLE receipts DROP CHECK chk_receipt_method;
ALTER TABLE receipts ADD CONSTRAINT chk_receipt_method CHECK(method IN ('CASH','TRANSFER','BANK_TRANSFER_ONLINE'));
INSERT INTO users(username,password_hash,full_name,user_type,status)
 VALUES('online-payment','!NO_INTERACTIVE_LOGIN','Thanh toán trực tuyến','STAFF','DISABLED');
INSERT INTO user_roles(user_id,role_id)
 SELECT u.id,r.id FROM users u JOIN roles r ON r.code='CASHIER' WHERE u.username='online-payment';
