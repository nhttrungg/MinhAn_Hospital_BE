CREATE TABLE pharmacy_online_orders (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, prescription_id BIGINT NOT NULL, encounter_id BIGINT NOT NULL,
 created_by BIGINT NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), expires_at DATETIME(6) NOT NULL,
 FOREIGN KEY(prescription_id) REFERENCES prescriptions(id), FOREIGN KEY(encounter_id) REFERENCES encounters(id),
 FOREIGN KEY(created_by) REFERENCES users(id), CHECK(status IN ('OPEN','CANCELLED','EXPIRED')),
 INDEX ix_online_order_encounter(encounter_id,status), INDEX ix_online_order_expiry(status,expires_at)
) ENGINE=InnoDB;
ALTER TABLE invoices ADD COLUMN online_order_id BIGINT NULL,
 ADD CONSTRAINT fk_invoice_online_order FOREIGN KEY(online_order_id) REFERENCES pharmacy_online_orders(id),
 ADD INDEX ix_invoice_online_order(online_order_id,counter_id);
ALTER TABLE payment_intents ADD COLUMN bank_account_no VARCHAR(19) NULL;
CREATE TABLE pharmacy_counter_settlements (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, counter_id BIGINT NOT NULL, amount DECIMAL(15,2) NOT NULL,
 transaction_reference VARCHAR(100) NOT NULL UNIQUE, note VARCHAR(500) NOT NULL,
 bank_bin VARCHAR(6) NOT NULL, account_no VARCHAR(19) NOT NULL, account_name VARCHAR(100) NOT NULL,
 recorded_by BIGINT NOT NULL, recorded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(counter_id) REFERENCES pharmacy_counters(id), FOREIGN KEY(recorded_by) REFERENCES users(id),
 CHECK(amount>0), INDEX ix_settlement_counter(counter_id,id)
) ENGINE=InnoDB;