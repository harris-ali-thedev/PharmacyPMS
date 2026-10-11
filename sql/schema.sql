-- =============================================================
--              FILE NAME: sechema.sql
-- =============================================================

-- ============================================================
-- Pharmacy Management System - Database Schema
-- Optimized for MySQL / MariaDB
-- ============================================================

DROP DATABASE IF EXISTS pharmacy_db;

CREATE DATABASE IF NOT EXISTS pharmacy_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE pharmacy_db;

-- 1. USERS
CREATE TABLE IF NOT EXISTS users (
                                     user_id             INT AUTO_INCREMENT PRIMARY KEY,
                                     username            VARCHAR(50) NOT NULL UNIQUE,
                                     password_hash       VARCHAR(255) NOT NULL,
                                     full_name           VARCHAR(100) NOT NULL,
                                     role                ENUM('MAIN_ADMIN','ADMIN','PHARMACIST','TECHNICIAN','CASHIER') NOT NULL DEFAULT 'CASHIER',
                                     email               VARCHAR(100),
                                     phone               VARCHAR(20),
                                     license_number      VARCHAR(50), -- PMC license for pharmacists
                                     is_active           TINYINT(1) NOT NULL DEFAULT 1,
                                     must_change_password TINYINT(1) NOT NULL DEFAULT 0,
                                     failed_login_count  INT DEFAULT 0,
                                     is_locked           TINYINT(1) DEFAULT 0,
                                     locked_until        DATETIME,
                                     last_login_at       DATETIME,
                                     last_login_ip       VARCHAR(45),
                                     created_by          INT,
                                     created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                     FOREIGN KEY (created_by) REFERENCES users(user_id) ON DELETE SET NULL
);

-- Default admin user (password: admin123)
INSERT IGNORE INTO users (username, password_hash, full_name, role)
VALUES ('admin', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQyCjFYXfIp6xhq6oTb.X7Zce', 'Main Admin', 'MAIN_ADMIN');

-- 2. SUPPLIERS
CREATE TABLE IF NOT EXISTS suppliers (
                                         supplier_id         INT AUTO_INCREMENT PRIMARY KEY,
                                         name                VARCHAR(100) NOT NULL,
                                         contact_name        VARCHAR(100),
                                         phone               VARCHAR(20),
                                         email               VARCHAR(100),
                                         address             TEXT,
                                         lead_days           INT DEFAULT 3,
                                         is_active           TINYINT(1) NOT NULL DEFAULT 1,
                                         created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. MEDICINES
CREATE TABLE IF NOT EXISTS medicines (
                                         med_id              INT AUTO_INCREMENT PRIMARY KEY,
                                         name                VARCHAR(150) NOT NULL,
                                         generic_name        VARCHAR(150),
                                         category            VARCHAR(80),
                                         barcode             VARCHAR(50) UNIQUE,
                                         unit_price          DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                         unit_cost           DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                         pack_cost           DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                         stock_qty           INT NOT NULL DEFAULT 0,
                                         reorder_level       INT NOT NULL DEFAULT 20,
                                         is_scheduled        TINYINT(1) NOT NULL DEFAULT 0, -- Rx-only
                                         is_narcotic         TINYINT(1) NOT NULL DEFAULT 0, -- Schedule II+
                                         is_active           TINYINT(1) NOT NULL DEFAULT 1,

    -- Clinical Details
                                         dosage_form         VARCHAR(50), -- Tablet, Syrup, etc.
                                         strength            VARCHAR(50), -- 500mg, etc.
                                         route_of_admin      VARCHAR(50), -- Oral, IV, etc.
                                         manufacturer        VARCHAR(100),
                                         storage_condition   VARCHAR(100),
                                         generic_substitution_allowed TINYINT(1) DEFAULT 1,
                                         therapeutic_class   VARCHAR(150),
                                         allergy_class       VARCHAR(100), -- e.g. Penicillin
                                         drug_interaction_groups TEXT,     -- Comma-separated codes

                                         supplier_id         INT,
                                         created_by          INT,
                                         created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                         updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                         FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id) ON DELETE SET NULL,
                                         FOREIGN KEY (created_by) REFERENCES users(user_id) ON DELETE SET NULL
);

-- 4. BATCHES (tracks expiry per batch)
CREATE TABLE IF NOT EXISTS batches (
                                       batch_id            INT AUTO_INCREMENT PRIMARY KEY,
                                       med_id              INT NOT NULL,
                                       batch_number        VARCHAR(50) NOT NULL,
                                       qty_received        INT NOT NULL DEFAULT 0,
                                       qty_remaining       INT NOT NULL DEFAULT 0,
                                       expiry_date         DATE NOT NULL,
                                       unit_cost           DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                       pack_cost           DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                       received_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       FOREIGN KEY (med_id) REFERENCES medicines(med_id) ON DELETE CASCADE
);

-- 5. PATIENTS
CREATE TABLE IF NOT EXISTS patients (
                                        patient_id          INT AUTO_INCREMENT PRIMARY KEY,
                                        patient_code        VARCHAR(20) UNIQUE,        -- PAT-000001
                                        first_name          VARCHAR(50) NOT NULL,
                                        last_name           VARCHAR(50),
                                        full_name           VARCHAR(100),
                                        date_of_birth       DATE,
                                        gender              ENUM('MALE','FEMALE','OTHER'),
                                        blood_group         ENUM('A_POS','A_NEG','B_POS','B_NEG','AB_POS','AB_NEG',
                                            'O_POS','O_NEG','UNKNOWN'),
                                        phone               VARCHAR(20),
                                        email               VARCHAR(100),
                                        nic_number          VARCHAR(20),               -- National ID — required for narcotics
                                        address             TEXT,
                                        emergency_contact   VARCHAR(100),
                                        emergency_phone     VARCHAR(20),
    -- Clinical (pipe-separated lists stored as text for simplicity)
                                        allergies           TEXT,
                                        chronic_conditions  TEXT,
                                        clinical_notes      TEXT,
    -- Insurance
                                        insurer_name        VARCHAR(100),
                                        policy_number       VARCHAR(50),
                                        insurer_phone       VARCHAR(20),
    -- Loyalty
                                        loyalty_points      INT NOT NULL DEFAULT 0,
                                        loyalty_tier        ENUM('BRONZE','SILVER','GOLD') NOT NULL DEFAULT 'BRONZE',
    -- Audit
                                        is_active           TINYINT(1) NOT NULL DEFAULT 1,
                                        created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                        updated_at          DATETIME ON UPDATE CURRENT_TIMESTAMP,
                                        created_by          INT,
                                        INDEX idx_patient_phone (phone),
                                        INDEX idx_patient_nic   (nic_number),
                                        INDEX idx_patient_name  (last_name, first_name)
);

-- 6. PRESCRIPTIONS
CREATE TABLE IF NOT EXISTS prescriptions (
                                             prescription_id     INT AUTO_INCREMENT PRIMARY KEY,
                                             prescription_number VARCHAR(30) UNIQUE, -- RX-2024-000001
                                             patient_id          INT,
                                             patient_name        VARCHAR(100), -- Denormalized for quick access
                                             patient_nic         VARCHAR(20),

    -- Prescriber
                                             doctor_name         VARCHAR(100) NOT NULL,
                                             doctor_reg_no       VARCHAR(50), -- PMC / Council No
                                             doctor_phone        VARCHAR(20),
                                             clinic_name         VARCHAR(100),

    -- Rx Details
                                             issued_date         DATE,
                                             expiry_date         DATE,
                                             validity_days       INT DEFAULT 30,
                                             max_refills         INT DEFAULT 0,
                                             refills_used        INT DEFAULT 0,
                                             diagnosis           TEXT,
                                             notes               TEXT,

    -- Verification
                                             status              ENUM('PENDING', 'VERIFIED', 'PARTIAL', 'DISPENSED', 'EXPIRED', 'CANCELLED') DEFAULT 'PENDING',
                                             e_prescription      TINYINT(1) DEFAULT 0,
                                             image_path          VARCHAR(255),
                                             verified_by         INT,
                                             verified_at         DATETIME,

                                             created_by          INT,
                                             created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                             updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                             FOREIGN KEY (patient_id) REFERENCES patients(patient_id) ON DELETE SET NULL,
                                             FOREIGN KEY (verified_by) REFERENCES users(user_id) ON DELETE SET NULL,
                                             FOREIGN KEY (created_by) REFERENCES users(user_id) ON DELETE SET NULL
);

-- 7. PRESCRIPTION ITEMS
CREATE TABLE IF NOT EXISTS prescription_items (
                                                  item_id             INT AUTO_INCREMENT PRIMARY KEY,
                                                  prescription_id     INT NOT NULL,
                                                  med_id              INT NOT NULL,
                                                  medicine_name       VARCHAR(150),
                                                  dosage              VARCHAR(100), -- "1 tablet"
                                                  frequency           VARCHAR(100), -- "Twice daily"
                                                  duration            VARCHAR(100), -- "7 days"
                                                  qty_prescribed      INT NOT NULL,
                                                  qty_dispensed       INT DEFAULT 0,
                                                  instructions        TEXT,
                                                  is_scheduled        TINYINT(1) DEFAULT 0,
                                                  is_narcotic         TINYINT(1) DEFAULT 0,
                                                  FOREIGN KEY (prescription_id) REFERENCES prescriptions(prescription_id) ON DELETE CASCADE,
                                                  FOREIGN KEY (med_id) REFERENCES medicines(med_id)
);

-- 8. SHIFT SESSIONS (Cash Reconciliation)
CREATE TABLE IF NOT EXISTS shift_sessions (
                                              session_id          INT AUTO_INCREMENT PRIMARY KEY,
                                              user_id             INT NOT NULL,
                                              cashier_name        VARCHAR(100),
                                              opening_float       DECIMAL(10,2) DEFAULT 0.00,
                                              closing_float       DECIMAL(10,2) DEFAULT 0.00,
                                              total_cash_sales    DECIMAL(10,2) DEFAULT 0.00,
                                              total_card_sales    DECIMAL(10,2) DEFAULT 0.00,
                                              total_digital_sales DECIMAL(10,2) DEFAULT 0.00,
                                              total_sales         DECIMAL(10,2) DEFAULT 0.00,
                                              total_refunds       DECIMAL(10,2) DEFAULT 0.00,
                                              transaction_count   INT DEFAULT 0,
                                              expected_cash       DECIMAL(10,2) DEFAULT 0.00,
                                              variance            DECIMAL(10,2) DEFAULT 0.00,
                                              variance_notes      TEXT,
                                              status              ENUM('OPEN', 'CLOSED') DEFAULT 'OPEN',
                                              opened_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                              closed_at           DATETIME,
                                              FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- 9. SALES
CREATE TABLE IF NOT EXISTS sales (
                                     sale_id             INT AUTO_INCREMENT PRIMARY KEY,
                                     user_id             INT NOT NULL,
                                     patient_id          INT,
                                     session_id          INT,
                                     prescription_id     INT,
                                     sale_date           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                     subtotal            DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                     discount_type       ENUM('NONE','PERCENT','FIXED') DEFAULT 'NONE',
                                     discount_value      DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                     discount_amount     DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                     tax_percent         DECIMAL(5,2) NOT NULL DEFAULT 0.00,
                                     tax_amount          DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                     grand_total         DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                     amount_paid         DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                     change_returned     DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                     payment_method      ENUM('CASH','CARD','DIGITAL_WALLET') NOT NULL DEFAULT 'CASH',
                                     status              ENUM('COMPLETED','VOIDED') NOT NULL DEFAULT 'COMPLETED',

    -- Void Details
                                     void_reason         VARCHAR(255),
                                     voided_by           INT,
                                     voided_at           DATETIME,

                                     FOREIGN KEY (user_id) REFERENCES users(user_id),
                                     FOREIGN KEY (patient_id) REFERENCES patients(patient_id) ON DELETE SET NULL,
                                     FOREIGN KEY (session_id) REFERENCES shift_sessions(session_id) ON DELETE SET NULL,
                                     FOREIGN KEY (prescription_id) REFERENCES prescriptions(prescription_id) ON DELETE SET NULL,
                                     FOREIGN KEY (voided_by) REFERENCES users(user_id) ON DELETE SET NULL
);

-- 10. SALE ITEMS
CREATE TABLE IF NOT EXISTS sale_items (
                                          sale_item_id        INT AUTO_INCREMENT PRIMARY KEY,
                                          sale_id             INT NOT NULL,
                                          med_id              INT NOT NULL,
                                          batch_id            INT,
                                          quantity            INT NOT NULL,
                                          unit_price          DECIMAL(10,2) NOT NULL,
                                          purchase_cost       DECIMAL(10,2) NOT NULL DEFAULT 0.00, -- Captured at time of sale for profit reporting
                                          subtotal            DECIMAL(10,2) NOT NULL,
                                          FOREIGN KEY (sale_id) REFERENCES sales(sale_id) ON DELETE CASCADE,
                                          FOREIGN KEY (med_id) REFERENCES medicines(med_id),
                                          FOREIGN KEY (batch_id) REFERENCES batches(batch_id) ON DELETE SET NULL
);

-- 11. PURCHASE ORDERS
CREATE TABLE IF NOT EXISTS purchase_orders (
                                               po_id               INT AUTO_INCREMENT PRIMARY KEY,
                                               supplier_id         INT NOT NULL,
                                               created_by          INT NOT NULL,
                                               order_date          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                               status              ENUM('PENDING','RECEIVED','CANCELLED') NOT NULL DEFAULT 'PENDING',
                                               total_cost          DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                               notes               TEXT,
                                               FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id),
                                               FOREIGN KEY (created_by) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS po_items (
                                        po_item_id          INT AUTO_INCREMENT PRIMARY KEY,
                                        po_id               INT NOT NULL,
                                        med_id              INT NOT NULL,
                                        qty_ordered         INT NOT NULL,
                                        unit_cost           DECIMAL(10,2) NOT NULL,
                                        pack_cost           DECIMAL(10,2) NOT NULL,
                                        FOREIGN KEY (po_id) REFERENCES purchase_orders(po_id) ON DELETE CASCADE,
                                        FOREIGN KEY (med_id) REFERENCES medicines(med_id)
);

-- 12. NARCOTIC REGISTER (Government requirement)
CREATE TABLE IF NOT EXISTS narcotic_register (
                                                 entry_id            INT AUTO_INCREMENT PRIMARY KEY,
                                                 med_id              INT NOT NULL,
                                                 medicine_name       VARCHAR(150),
                                                 batch_number        VARCHAR(50),
                                                 transaction_type    ENUM('IN', 'OUT') NOT NULL,
                                                 qty_in              INT DEFAULT 0,
                                                 qty_out             INT DEFAULT 0,
                                                 running_balance     INT NOT NULL,

                                                 prescription_id     INT,
                                                 patient_id          INT,
                                                 patient_name        VARCHAR(100),
                                                 patient_nic         VARCHAR(20),
                                                 doctor_name         VARCHAR(100),
                                                 doctor_reg_no       VARCHAR(50),

                                                 sale_id             INT,
                                                 witness_name        VARCHAR(100), -- Optional
                                                 witness_designation VARCHAR(100),
                                                 dispensed_by        INT,
                                                 dispensed_by_name   VARCHAR(100),

                                                 notes               TEXT,
                                                 entry_date          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                                 FOREIGN KEY (med_id) REFERENCES medicines(med_id),
                                                 FOREIGN KEY (prescription_id) REFERENCES prescriptions(prescription_id) ON DELETE SET NULL,
                                                 FOREIGN KEY (patient_id) REFERENCES patients(patient_id) ON DELETE SET NULL,
                                                 FOREIGN KEY (sale_id) REFERENCES sales(sale_id) ON DELETE SET NULL,
                                                 FOREIGN KEY (dispensed_by) REFERENCES users(user_id) ON DELETE SET NULL
);

-- 13. AUDIT LOG
CREATE TABLE IF NOT EXISTS audit_log (
                                         id              INT AUTO_INCREMENT PRIMARY KEY,
                                         user_id             INT,
                                         username            VARCHAR(50), -- Captured in case user is deleted
                                         action              VARCHAR(100) NOT NULL, -- e.g. LOGIN_SUCCESS, SALE_COMPLETED
                                         entity_type         VARCHAR(50), -- e.g. sales, prescriptions
                                         entity_id           INT,
                                         description         TEXT,
                                         ip_address          VARCHAR(45),
                                         logged_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                         FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL
);

-- ============================================================
-- SEED DATA
-- ============================================================

INSERT IGNORE INTO suppliers (name, contact_name, phone, email, address) VALUES
                                                                             ('MedPak Ltd',    'Ali Hassan',   '051-1234567', 'ali@medpak.pk',    'Blue Area, Islamabad'),
                                                                             ('PharmaLogix',   'Sara Khan',    '042-7654321', 'sara@pharmalogix.pk', 'Gulberg III, Lahore'),
                                                                             ('HealthPlus Dist','Zara Malik',  '021-9876543', 'zara@healthplus.pk', 'SITE, Karachi'),
                                                                             ('Sanofi',         'M. Rizwan',    '0315-9990001', 'dist@sanofi.pk',    'Multan, Pakistan'),
                                                                             ('Getz Pharma',    'Usman Sheikh', '0333-1234567', 'orders@getz.pk',    'Faisalabad, Pakistan');

INSERT IGNORE INTO medicines (name, generic_name, category, barcode, unit_price, unit_cost, pack_cost, stock_qty, reorder_level, is_scheduled, supplier_id, dosage_form, strength) VALUES
                                                                                                                                                                                  ('Panadol 500mg',      'Paracetamol',    'Analgesic',     '89640001', 30.00,  21.00, 504.00, 142, 50, 0, 1, 'Tablet', '500mg'),
                                                                                                                                                                                  ('Amoxicillin 250mg',  'Amoxicillin',    'Antibiotic',    '89640012', 110.00,  77.00, 924.00, 38,  30, 1, 2, 'Capsule', '250mg'),
                                                                                                                                                                                  ('Augmentin 625mg',    'Amoxicillin+CA', 'Antibiotic',    '89640010', 220.00, 154.00, 1848.00, 4,   20, 1, 1, 'Tablet', '625mg'),
                                                                                                                                                                                  ('Insulin Glargine',   'Insulin Glarg.', 'Antidiabetic',  'INS-GLA-001', 950.00, 600.00, 3000.00,60,  15, 1, 2, 'Injection', '100 IU/ml'),
                                                                                                                                                                                  ('Metformin 500mg',    'Metformin HCl',  'Antidiabetic',  '89640046', 150.00,  105.00, 1260.00, 210, 40, 1, 3, 'Tablet', '500mg'),
                                                                                                                                                                                  ('Diazepam 5mg',       'Diazepam',       'Anxiolytic',    'DZP-005-001', 95.00,  55.00, 660.00, 40,  10, 1, 1, 'Tablet', '5mg');

INSERT IGNORE INTO batches (med_id, batch_number, qty_received, qty_remaining, expiry_date, unit_cost, pack_cost) VALUES
                                                                                                                         (1, 'P-2024-08',  200, 142, '2027-08-31', 21.00, 504.00),
                                                                                                                         (2, 'A-2023-11',   60,  38, '2025-11-30', 77.00, 924.00),
                                                                                                                         (6, 'D-2024-02',   60,  40, '2027-02-28', 55.00, 660.00);
