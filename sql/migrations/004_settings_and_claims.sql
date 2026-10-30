CREATE TABLE IF NOT EXISTS settings (
    setting_key VARCHAR(80) PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
INSERT IGNORE INTO settings(setting_key,setting_value) VALUES
('store_name','MediCare Store'),('tax_mode','NO_TAX'),('session_timeout_minutes','15');

CREATE TABLE IF NOT EXISTS insurance_claims (
    claim_id INT AUTO_INCREMENT PRIMARY KEY,
    sale_id INT NOT NULL UNIQUE,
    policy_number VARCHAR(100) NOT NULL,
    insurer VARCHAR(150) NOT NULL,
    coverage_percent DECIMAL(5,2) NOT NULL,
    invoice_total DECIMAL(10,2) NOT NULL,
    covered_amount DECIMAL(10,2) NOT NULL,
    patient_copay DECIMAL(10,2) NOT NULL,
    status ENUM('RECORDED','CANCELLED') NOT NULL DEFAULT 'RECORDED',
    created_by INT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(sale_id) REFERENCES sales(sale_id),
    FOREIGN KEY(created_by) REFERENCES users(user_id)
);
