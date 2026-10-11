-- =============================================================
--   MediCare PMS — Final Database Schema
--   Based on: schema_pro.sql (structure)
--   Seed data merged from: schema.sql
--   MySQL / MariaDB compatible  |  utf8mb4
-- =============================================================

DROP DATABASE IF EXISTS pharmacy_db;

CREATE DATABASE IF NOT EXISTS pharmacy_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE pharmacy_db;

-- =============================================================
-- 1. USERS
-- =============================================================
CREATE TABLE IF NOT EXISTS users (
                                     user_id              INT AUTO_INCREMENT PRIMARY KEY,
                                     username             VARCHAR(50)  NOT NULL UNIQUE,
                                     password_hash        VARCHAR(255) NOT NULL,
                                     full_name            VARCHAR(100) NOT NULL,
                                     role                 ENUM('MAIN_ADMIN','ADMIN','PHARMACIST','TECHNICIAN','CASHIER')
                                                                       NOT NULL DEFAULT 'CASHIER',
                                     email                VARCHAR(100),
                                     phone                VARCHAR(20),
                                     license_number       VARCHAR(50),          -- PMC license for pharmacists
                                     is_active            TINYINT(1) NOT NULL DEFAULT 1,
                                     must_change_password TINYINT(1) NOT NULL DEFAULT 0,
                                     failed_login_count   INT         NOT NULL DEFAULT 0,
                                     is_locked            TINYINT(1)  NOT NULL DEFAULT 0,
                                     locked_until         DATETIME,
                                     last_login_at        DATETIME,
                                     last_login_ip        VARCHAR(45),
                                     last_activity_at     DATETIME,
                                     created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     updated_at           DATETIME ON UPDATE CURRENT_TIMESTAMP,
                                     created_by           INT
);

-- Default admin (password: Admin@123 — bcrypt)
INSERT IGNORE INTO users (username, password_hash, full_name, role, must_change_password)
VALUES ('admin',
        '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQyCjFYXfIp6xhq6oTb.X7Zce',
        'System Administrator', 'MAIN_ADMIN', 1);

-- =============================================================
-- 2. PATIENTS
-- =============================================================
CREATE TABLE IF NOT EXISTS patients (
                                        patient_id        INT AUTO_INCREMENT PRIMARY KEY,
                                        patient_code      VARCHAR(20) UNIQUE,           -- PAT-000001
                                        first_name        VARCHAR(50)  NOT NULL,
                                        last_name         VARCHAR(50),
                                        full_name         VARCHAR(100),
                                        date_of_birth     DATE,
                                        gender            ENUM('MALE','FEMALE','OTHER'),
                                        blood_group       ENUM('A_POS','A_NEG','B_POS','B_NEG',
                                            'AB_POS','AB_NEG','O_POS','O_NEG','UNKNOWN'),
                                        phone             VARCHAR(20),
                                        email             VARCHAR(100),
                                        nic_number        VARCHAR(20),                  -- National ID — required for narcotics
                                        address           TEXT,
                                        emergency_contact VARCHAR(100),
                                        emergency_phone   VARCHAR(20),
    -- Clinical (pipe-separated lists stored as text)
                                        allergies         TEXT,
                                        chronic_conditions TEXT,
                                        clinical_notes    TEXT,
    -- Insurance
                                        insurer_name      VARCHAR(100),
                                        policy_number     VARCHAR(50),
                                        insurer_phone     VARCHAR(20),
    -- Loyalty
                                        loyalty_points    INT         NOT NULL DEFAULT 0,
                                        loyalty_tier      ENUM('BRONZE','SILVER','GOLD') NOT NULL DEFAULT 'BRONZE',
    -- Audit
                                        is_active         TINYINT(1)  NOT NULL DEFAULT 1,
                                        created_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                        updated_at        DATETIME    ON UPDATE CURRENT_TIMESTAMP,
                                        created_by        INT,
                                        INDEX idx_patient_phone (phone),
                                        INDEX idx_patient_nic   (nic_number),
                                        INDEX idx_patient_name  (last_name, first_name)
);

-- Sample patients from real-world PK context
INSERT IGNORE INTO patients
(patient_code, first_name, last_name, full_name, date_of_birth,
 gender, blood_group, phone, nic_number, allergies, chronic_conditions, loyalty_tier)
VALUES
    ('PAT-000001', 'Ahmed',   'Raza',    'Ahmed Raza',    '1985-04-12',
     'MALE',   'B_POS',  '0321-1234567', '35202-1234567-1', NULL,          'Hypertension',   'SILVER'),
    ('PAT-000002', 'Fatima',  'Malik',   'Fatima Malik',  '1992-09-23',
     'FEMALE', 'A_POS',  '0333-9876543', '35202-9876543-2', 'Penicillin',  'Diabetes Type 2','BRONZE'),
    ('PAT-000003', 'Usman',   'Sheikh',  'Usman Sheikh',  '1978-01-05',
     'MALE',   'O_POS',  '0300-1112233', '35202-1112233-3', NULL,          'Asthma',         'BRONZE'),
    ('PAT-000004', 'Ayesha',  'Khan',    'Ayesha Khan',   '2001-07-30',
     'FEMALE', 'AB_NEG', '0311-4445566', '35202-4445566-4', 'Sulfonamide', NULL,             'BRONZE'),
    ('PAT-000005', 'Bilal',   'Hussain', 'Bilal Hussain', '1965-11-18',
     'MALE',   'A_NEG',  '0345-7778899', '35202-7778899-5', NULL,          'Hypertension|Diabetes Type 2', 'GOLD');

-- =============================================================
-- 3. SUPPLIERS
-- =============================================================
CREATE TABLE IF NOT EXISTS suppliers (
                                         supplier_id   INT AUTO_INCREMENT PRIMARY KEY,
                                         name          VARCHAR(100) NOT NULL,
                                         contact_name  VARCHAR(100),
                                         phone         VARCHAR(20),
                                         email         VARCHAR(100),
                                         address       TEXT,
                                         lead_days     INT         NOT NULL DEFAULT 3,
                                         payment_terms VARCHAR(50),                    -- "Net 30", "COD", etc.
                                         is_active     TINYINT(1)  NOT NULL DEFAULT 1,
                                         created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                         updated_at    DATETIME    ON UPDATE CURRENT_TIMESTAMP
);

INSERT IGNORE INTO suppliers (name, contact_name, phone, email, address, lead_days, payment_terms) VALUES
                                                                                                       ('MedPak Ltd',      'Ali Hassan',    '051-1234567',  'ali@medpak.pk',      'Blue Area, Islamabad',     3,  'Net 30'),
                                                                                                       ('PharmaLogix',     'Sara Khan',     '042-7654321',  'sara@pharmalogix.pk','Gulberg III, Lahore',       5,  'Net 15'),
                                                                                                       ('HealthPlus Dist', 'Zara Malik',    '021-9876543',  'zara@healthplus.pk', 'SITE, Karachi',             4,  'COD'),
                                                                                                       ('Sanofi',          'M. Rizwan',     '0315-9990001', 'dist@sanofi.pk',     'Multan, Pakistan',          7,  'Net 45'),
                                                                                                       ('Getz Pharma',     'Usman Sheikh',  '0333-1234567', 'orders@getz.pk',     'Faisalabad, Pakistan',      5,  'Net 30');

-- =============================================================
-- 4. MEDICINES
-- =============================================================
CREATE TABLE IF NOT EXISTS medicines (
                                         med_id                  INT AUTO_INCREMENT PRIMARY KEY,
                                         name                    VARCHAR(150) NOT NULL,
                                         generic_name            VARCHAR(150),
                                         category                VARCHAR(80),
                                         therapeutic_class       VARCHAR(100),          -- ATC class
                                         dosage_form             VARCHAR(50),           -- Tablet, Capsule, Syrup, etc.
                                         strength                VARCHAR(50),           -- "500mg", "250mg/5ml"
                                         route_of_admin          VARCHAR(50),           -- Oral, Topical, IV, etc.
                                         manufacturer            VARCHAR(100),
                                         storage_condition       VARCHAR(100),
                                         barcode                 VARCHAR(50) UNIQUE,
                                         unit_price              DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                         unit_cost               DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                         pack_cost               DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                         stock_qty               INT           NOT NULL DEFAULT 0,
                                         reorder_level           INT           NOT NULL DEFAULT 20,
                                         is_scheduled            TINYINT(1)    NOT NULL DEFAULT 0,  -- Rx-only
                                         is_narcotic             TINYINT(1)    NOT NULL DEFAULT 0,  -- Schedule II+
                                         generic_substitution    TINYINT(1)    NOT NULL DEFAULT 1,  -- Allow generic sub
                                         allergy_class           VARCHAR(100),          -- e.g. "Penicillin" for cross-allergy checks
                                         drug_interaction_groups VARCHAR(255),          -- Comma-separated interaction group codes
                                         supplier_id             INT,
                                         is_active               TINYINT(1)    NOT NULL DEFAULT 1,
                                         created_at              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                         updated_at              DATETIME      ON UPDATE CURRENT_TIMESTAMP,
                                         created_by              INT,
                                         FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id) ON DELETE SET NULL,
                                         INDEX idx_medicine_name    (name),
                                         INDEX idx_medicine_generic (generic_name),
                                         FULLTEXT INDEX ft_medicine  (name, generic_name, category)
);

INSERT IGNORE INTO medicines
(name, generic_name, category, therapeutic_class, dosage_form, strength,
 route_of_admin, manufacturer, storage_condition,
 barcode, unit_price, unit_cost, pack_cost,
 stock_qty, reorder_level, is_scheduled, is_narcotic,
 allergy_class, supplier_id)
VALUES
-- Analgesic
('Panadol 500mg',        'Paracetamol',         'Analgesic',    'N02BE01',
 'Tablet',    '500mg',   'Oral', 'GSK Pakistan',       'Store below 25°C',
 '89640001',   30.00,  21.00,   504.00,  142, 50,  0, 0, NULL,          1),

-- Antibiotics
('Amoxicillin 250mg',    'Amoxicillin',         'Antibiotic',   'J01CA04',
 'Capsule',   '250mg',   'Oral', 'Getz Pharma',        'Store below 25°C',
 '89640012',  110.00,  77.00,   924.00,   38, 30,  1, 0, 'Penicillin',  2),

('Augmentin 625mg',      'Amoxicillin+Clav.',   'Antibiotic',   'J01CR02',
 'Tablet',    '625mg',   'Oral', 'GSK Pakistan',       'Store below 25°C',
 '89640010',  220.00, 154.00,  1848.00,    4, 20,  1, 0, 'Penicillin',  1),

('Ciprofloxacin 500mg',  'Ciprofloxacin',       'Antibiotic',   'J01MA02',
 'Tablet',    '500mg',   'Oral', 'Searle Pakistan',    'Store below 30°C',
 '89640020',  180.00, 126.00,  1512.00,   85, 30,  1, 0, NULL,          3),

('Azithromycin 250mg',   'Azithromycin',        'Antibiotic',   'J01FA10',
 'Capsule',   '250mg',   'Oral', 'Highnoon Labs',      'Store below 25°C',
 '89640025',  250.00, 175.00,  2100.00,   52, 25,  1, 0, NULL,          2),

-- Antidiabetics
('Insulin Glargine',     'Insulin Glargine',    'Antidiabetic', 'A10AE04',
 'Injection', '100 IU/ml','SC', 'Sanofi Pakistan',    'Refrigerate 2-8°C',
 'INS-GLA-001', 950.00, 600.00, 3000.00,  60, 15,  1, 0, NULL,          4),

('Metformin 500mg',      'Metformin HCl',       'Antidiabetic', 'A10BA02',
 'Tablet',    '500mg',   'Oral', 'Getz Pharma',        'Store below 30°C',
 '89640046',  150.00, 105.00,  1260.00,  210, 40,  1, 0, NULL,          3),

('Glibenclamide 5mg',    'Glibenclamide',       'Antidiabetic', 'A10BB01',
 'Tablet',    '5mg',     'Oral', 'Abbott Pakistan',    'Store below 25°C',
 '89640050',   75.00,  52.50,   630.00,   90, 30,  1, 0, 'Sulfonylurea',3),

-- Antihypertensives
('Amlodipine 5mg',       'Amlodipine',          'Antihypertensive','C08CA01',
 'Tablet',    '5mg',     'Oral', 'Pfizer Pakistan',    'Store below 30°C',
 '89640060',  120.00,  84.00,  1008.00,  175, 40,  1, 0, NULL,          5),

('Lisinopril 10mg',      'Lisinopril',          'Antihypertensive','C09AA03',
 'Tablet',    '10mg',    'Oral', 'Searle Pakistan',    'Store below 25°C',
 '89640065',  135.00,  94.50,  1134.00,   63, 30,  1, 0, NULL,          3),

('Atenolol 50mg',        'Atenolol',            'Antihypertensive','C07AB03',
 'Tablet',    '50mg',    'Oral', 'ICI Pakistan',       'Store below 25°C',
 '89640070',   55.00,  38.50,   462.00,  120, 35,  1, 0, NULL,          1),

-- Anxiolytic / Narcotic
('Diazepam 5mg',         'Diazepam',            'Anxiolytic',   'N05BA01',
 'Tablet',    '5mg',     'Oral', 'Roche Pakistan',     'Store below 25°C',
 'DZP-005-001', 95.00,  55.00,   660.00,  40, 10,  1, 1, NULL,          1),

-- Respiratory
('Salbutamol 100mcg',    'Salbutamol',          'Bronchodilator','R03AC02',
 'Inhaler',   '100mcg',  'Inhalation','GSK Pakistan', 'Store below 30°C',
 '89640080',  280.00, 196.00,  2352.00,   47, 20,  0, 0, NULL,          1),

('Prednisolone 5mg',     'Prednisolone',        'Corticosteroid','H02AB06',
 'Tablet',    '5mg',     'Oral', 'Sanofi Pakistan',   'Store below 25°C',
 '89640085',   65.00,  45.50,   546.00,   88, 25,  1, 0, NULL,          4),

-- GI
('Omeprazole 20mg',      'Omeprazole',          'Proton Pump Inhibitor','A02BC01',
 'Capsule',   '20mg',    'Oral', 'Getz Pharma',        'Store below 25°C',
 '89640090',   95.00,  66.50,   798.00,  155, 40,  0, 0, NULL,          5),

('Metoclopramide 10mg',  'Metoclopramide',      'Antiemetic',   'A03FA01',
 'Tablet',    '10mg',    'Oral', 'ICI Pakistan',       'Store below 25°C',
 '89640095',   40.00,  28.00,   336.00,   70, 25,  0, 0, NULL,          1),

-- Lipid-lowering
('Atorvastatin 20mg',    'Atorvastatin',        'Lipid Lowering','C10AA05',
 'Tablet',    '20mg',    'Oral', 'Pfizer Pakistan',    'Store below 25°C',
 '89640100',  210.00, 147.00,  1764.00,   95, 30,  1, 0, NULL,          5),

-- Antihistamine
('Cetirizine 10mg',      'Cetirizine HCl',      'Antihistamine', 'R06AE07',
 'Tablet',    '10mg',    'Oral', 'UCB Pakistan',       'Store below 30°C',
 '89640105',   45.00,  31.50,   378.00,  200, 50,  0, 0, NULL,          2),

-- Vitamins/Supplements
('Vitamin D3 5000IU',    'Cholecalciferol',     'Supplement',   'A11CC05',
 'Softgel',   '5000 IU', 'Oral', 'Highnoon Labs',      'Store below 25°C',
 '89640110',  350.00, 245.00,  2940.00,  110, 30,  0, 0, NULL,          2),

('Calcium + D3',         'Calcium Carbonate + Vit D3','Supplement','A12AX',
 'Tablet',    '500mg/200IU','Oral','Abbott Pakistan',  'Store below 25°C',
 '89640115',  280.00, 196.00,  2352.00,   65, 25,  0, 0, NULL,          3);

-- =============================================================
-- 5. BATCHES
-- =============================================================
CREATE TABLE IF NOT EXISTS batches (
                                       batch_id          INT AUTO_INCREMENT PRIMARY KEY,
                                       med_id            INT NOT NULL,
                                       batch_number      VARCHAR(50) NOT NULL,
                                       qty_received      INT         NOT NULL DEFAULT 0,
                                       qty_remaining     INT         NOT NULL DEFAULT 0,
                                       expiry_date       DATE        NOT NULL,
                                       unit_cost         DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                       pack_cost         DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                       is_quarantined    TINYINT(1)  NOT NULL DEFAULT 0,
                                       quarantine_reason VARCHAR(255),
                                       quarantined_by    INT,
                                       quarantined_at    DATETIME,
                                       received_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       FOREIGN KEY (med_id) REFERENCES medicines(med_id) ON DELETE CASCADE,
                                       INDEX idx_batch_med_expiry (med_id, expiry_date)
);

INSERT IGNORE INTO batches (med_id, batch_number, qty_received, qty_remaining, expiry_date, unit_cost, pack_cost) VALUES
-- med_id 1  Panadol 500mg
(1,  'P-2024-08',   200, 142, '2027-08-31',  21.00,   504.00),
-- med_id 2  Amoxicillin 250mg
(2,  'A-2023-11',    60,  38, '2025-11-30',  77.00,   924.00),
-- med_id 3  Augmentin 625mg
(3,  'AU-2024-03',   20,   4, '2026-03-31', 154.00,  1848.00),
-- med_id 4  Ciprofloxacin 500mg
(4,  'CIP-2024-05', 100,  85, '2026-05-31', 126.00,  1512.00),
-- med_id 5  Azithromycin 250mg
(5,  'AZ-2024-06',   60,  52, '2026-06-30', 175.00,  2100.00),
-- med_id 6  Insulin Glargine
(6,  'INS-2024-04',  80,  60, '2025-04-30', 600.00,  3000.00),
-- med_id 7  Metformin 500mg
(7,  'MET-2024-01', 240, 210, '2027-01-31', 105.00,  1260.00),
-- med_id 8  Glibenclamide 5mg
(8,  'GLI-2024-02', 100,  90, '2026-02-28',  52.50,   630.00),
-- med_id 9  Amlodipine 5mg
(9,  'AML-2024-07', 200, 175, '2027-07-31',  84.00,  1008.00),
-- med_id 10 Lisinopril 10mg
(10, 'LIS-2024-06',  80,  63, '2026-06-30',  94.50,  1134.00),
-- med_id 11 Atenolol 50mg
(11, 'ATE-2024-09', 150, 120, '2027-09-30',  38.50,   462.00),
-- med_id 12 Diazepam 5mg
(12, 'D-2024-02',    60,  40, '2027-02-28',  55.00,   660.00),
-- med_id 13 Salbutamol inhaler
(13, 'SAL-2024-08',  50,  47, '2026-08-31', 196.00,  2352.00),
-- med_id 14 Prednisolone 5mg
(14, 'PRE-2024-05', 100,  88, '2026-05-31',  45.50,   546.00),
-- med_id 15 Omeprazole 20mg  — NEAR EXPIRY (triggers expiry alert)
(15, 'OME-2023-12', 200, 155, '2025-06-30',  66.50,   798.00),
-- med_id 16 Metoclopramide 10mg
(16, 'MET2-2024-03', 80,  70, '2026-03-31',  28.00,   336.00),
-- med_id 17 Atorvastatin 20mg
(17, 'ATO-2024-06', 120,  95, '2026-06-30', 147.00,  1764.00),
-- med_id 18 Cetirizine 10mg
(18, 'CET-2024-08', 250, 200, '2027-08-31',  31.50,   378.00),
-- med_id 19 Vitamin D3
(19, 'VD3-2024-07', 120, 110, '2027-07-31', 245.00,  2940.00),
-- med_id 20 Calcium + D3
(20, 'CAL-2024-06',  80,  65, '2026-06-30', 196.00,  2352.00);

-- =============================================================
-- 6. PRESCRIPTIONS
-- =============================================================
CREATE TABLE IF NOT EXISTS prescriptions (
                                             prescription_id     INT AUTO_INCREMENT PRIMARY KEY,
                                             prescription_number VARCHAR(20) UNIQUE,            -- RX-2024-000001
                                             patient_id          INT,
                                             patient_name        VARCHAR(100) NOT NULL,
                                             patient_nic         VARCHAR(20),
                                             doctor_name         VARCHAR(100) NOT NULL,
                                             doctor_reg_no       VARCHAR(50),
                                             doctor_phone        VARCHAR(20),
                                             clinic_name         VARCHAR(100),
                                             issued_date         DATE,
                                             expiry_date         DATE,
                                             validity_days       INT         NOT NULL DEFAULT 30,
                                             max_refills         INT         NOT NULL DEFAULT 0,
                                             refills_used        INT         NOT NULL DEFAULT 0,
                                             diagnosis           VARCHAR(255),
                                             notes               TEXT,
                                             image_path          VARCHAR(255),
                                             is_e_prescription   TINYINT(1)  NOT NULL DEFAULT 0,
                                             status              ENUM('PENDING','VERIFIED','PARTIAL','DISPENSED','EXPIRED','CANCELLED')
                                                                              NOT NULL DEFAULT 'PENDING',
                                             verified_by         INT,
                                             verified_at         DATETIME,
                                             created_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                             updated_at          DATETIME    ON UPDATE CURRENT_TIMESTAMP,
                                             created_by          INT,
                                             FOREIGN KEY (patient_id)  REFERENCES patients(patient_id)  ON DELETE SET NULL,
                                             FOREIGN KEY (verified_by) REFERENCES users(user_id)         ON DELETE SET NULL,
                                             INDEX idx_rx_patient (patient_id),
                                             INDEX idx_rx_status  (status)
);

INSERT IGNORE INTO prescriptions
(prescription_number, patient_id, patient_name, patient_nic,
 doctor_name, doctor_reg_no, doctor_phone, clinic_name,
 issued_date, expiry_date, validity_days, max_refills,
 diagnosis, status, verified_by, verified_at, created_by)
VALUES
    ('RX-2024-000001', 1, 'Ahmed Raza',   '35202-1234567-1',
     'Dr. Naeem Butt',   'PMC-12345', '051-1110001', 'City Medical Center',
     '2024-11-01', '2024-12-01', 30, 2,
     'Hypertension', 'VERIFIED', 1, '2024-11-01 09:30:00', 1),

    ('RX-2024-000002', 2, 'Fatima Malik', '35202-9876543-2',
     'Dr. Sana Qureshi', 'PMC-67890', '042-2220002', 'Lahore Diabetes Clinic',
     '2024-11-10', '2024-12-10', 30, 1,
     'Diabetes Type 2', 'DISPENSED', 1, '2024-11-10 10:00:00', 1),

    ('RX-2024-000003', 3, 'Usman Sheikh', '35202-1112233-3',
     'Dr. Ali Farooq',   'PMC-11111', '051-3330003', 'Shifa Clinic',
     '2024-11-15', '2024-12-15', 30, 0,
     'Respiratory Tract Infection', 'PENDING', NULL, NULL, 1);

-- =============================================================
-- 7. PRESCRIPTION ITEMS
-- =============================================================
CREATE TABLE IF NOT EXISTS prescription_items (
                                                  item_id         INT AUTO_INCREMENT PRIMARY KEY,
                                                  prescription_id INT NOT NULL,
                                                  med_id          INT NOT NULL,
                                                  medicine_name   VARCHAR(150),
                                                  dosage          VARCHAR(100),
                                                  frequency       VARCHAR(100),
                                                  duration        VARCHAR(100),
                                                  qty_prescribed  INT        NOT NULL DEFAULT 1,
                                                  qty_dispensed   INT        NOT NULL DEFAULT 0,
                                                  is_scheduled    TINYINT(1) NOT NULL DEFAULT 0,
                                                  is_narcotic     TINYINT(1) NOT NULL DEFAULT 0,
                                                  instructions    TEXT,
                                                  FOREIGN KEY (prescription_id) REFERENCES prescriptions(prescription_id) ON DELETE CASCADE,
                                                  FOREIGN KEY (med_id)          REFERENCES medicines(med_id)
);

INSERT IGNORE INTO prescription_items
(prescription_id, med_id, medicine_name, dosage, frequency, duration,
 qty_prescribed, qty_dispensed, is_scheduled)
VALUES
-- RX-2024-000001 (Hypertension)
(1, 9,  'Amlodipine 5mg',   '1 tablet', 'Once daily',  '30 days', 30, 30, 1),
(1, 10, 'Lisinopril 10mg',  '1 tablet', 'Once daily',  '30 days', 30, 30, 1),
-- RX-2024-000002 (Diabetes)
(2, 7,  'Metformin 500mg',  '1 tablet', 'Twice daily', '30 days', 60, 60, 1),
(2, 6,  'Insulin Glargine', '10 units', 'Once daily at bedtime', '30 days', 30, 30, 1),
-- RX-2024-000003 (RTI — pending)
(3, 3,  'Augmentin 625mg',  '1 tablet', 'Twice daily', '7 days',  14, 0, 1),
(3, 1,  'Panadol 500mg',    '2 tablets','Three times daily','5 days', 30, 0, 0);

-- =============================================================
-- 8. SHIFT SESSIONS
-- =============================================================
CREATE TABLE IF NOT EXISTS shift_sessions (
                                              session_id          INT AUTO_INCREMENT PRIMARY KEY,
                                              user_id             INT         NOT NULL,
                                              cashier_name        VARCHAR(100),
                                              opening_float       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                              closing_float       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                              total_cash_sales    DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                              total_card_sales    DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                              total_digital_sales DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                              total_sales         DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                              total_refunds       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                              transaction_count   INT           NOT NULL DEFAULT 0,
                                              expected_cash       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                              variance            DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                              variance_notes      TEXT,
                                              status              ENUM('OPEN','CLOSED') NOT NULL DEFAULT 'OPEN',
                                              opened_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                              closed_at           DATETIME,
                                              FOREIGN KEY (user_id) REFERENCES users(user_id),
                                              INDEX idx_shift_user   (user_id),
                                              INDEX idx_shift_status (status)
);

INSERT IGNORE INTO shift_sessions
(session_id, user_id, cashier_name, opening_float, closing_float,
 total_cash_sales, total_card_sales, total_sales,
 transaction_count, expected_cash, variance, status,
 opened_at, closed_at)
VALUES
    (1, 1, 'System Administrator', 5000.00, 13200.00,
     8200.00, 0.00, 8200.00, 5, 13200.00, 0.00,
     'CLOSED', '2024-11-01 08:00:00', '2024-11-01 20:00:00');

-- =============================================================
-- 9. SALES
-- =============================================================
CREATE TABLE IF NOT EXISTS sales (
                                     sale_id         INT AUTO_INCREMENT PRIMARY KEY,
                                     user_id         INT         NOT NULL,
                                     patient_id      INT,
                                     session_id      INT,
                                     prescription_id INT,
                                     sale_date       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     subtotal        DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                     discount_type   ENUM('NONE','PERCENT','FIXED') DEFAULT 'NONE',
                                     discount_value  DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                     discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                     tax_percent     DECIMAL(5,2)  NOT NULL DEFAULT 0.00,
                                     tax_amount      DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                     grand_total     DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                     amount_paid     DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                     change_returned DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                     payment_method  ENUM('CASH','CARD','DIGITAL_WALLET') NOT NULL DEFAULT 'CASH',
                                     status          ENUM('COMPLETED','VOIDED') NOT NULL DEFAULT 'COMPLETED',
                                     void_reason     VARCHAR(255),
                                     voided_by       INT,
                                     voided_at       DATETIME,
                                     FOREIGN KEY (user_id)         REFERENCES users(user_id),
                                     FOREIGN KEY (patient_id)      REFERENCES patients(patient_id)      ON DELETE SET NULL,
                                     FOREIGN KEY (session_id)      REFERENCES shift_sessions(session_id) ON DELETE SET NULL,
                                     FOREIGN KEY (prescription_id) REFERENCES prescriptions(prescription_id) ON DELETE SET NULL,
                                     FOREIGN KEY (voided_by)       REFERENCES users(user_id)            ON DELETE SET NULL,
                                     INDEX idx_sale_date    (sale_date),
                                     INDEX idx_sale_patient (patient_id)
);

INSERT IGNORE INTO sales
(sale_id, user_id, patient_id, session_id, prescription_id, sale_date,
 subtotal, discount_type, discount_value, discount_amount,
 grand_total, amount_paid, change_returned, payment_method, status)
VALUES
    (1, 1, 1, 1, 1, '2024-11-01 09:45:00',
     4200.00, 'PERCENT', 5.00, 210.00, 3990.00, 4000.00, 10.00, 'CASH', 'COMPLETED'),

    (2, 1, 2, 1, 2, '2024-11-01 11:00:00',
     9950.00, 'NONE', 0.00, 0.00, 9950.00, 10000.00, 50.00, 'CASH', 'COMPLETED'),

    (3, 1, 3, 1, NULL, '2024-11-01 13:30:00',
     1800.00, 'NONE', 0.00, 0.00, 1800.00, 2000.00, 200.00, 'CASH', 'COMPLETED'),

    (4, 1, NULL, 1, NULL, '2024-11-01 15:10:00',
     465.00, 'NONE', 0.00, 0.00,  465.00,  500.00,  35.00, 'CASH', 'COMPLETED'),

    (5, 1, 5, 1, NULL, '2024-11-01 17:45:00',
     2310.00, 'FIXED', 200.00, 200.00, 2110.00, 2200.00, 90.00, 'CASH', 'COMPLETED');

-- =============================================================
-- 10. SALE ITEMS
-- =============================================================
CREATE TABLE IF NOT EXISTS sale_items (
                                          sale_item_id  INT AUTO_INCREMENT PRIMARY KEY,
                                          sale_id       INT           NOT NULL,
                                          med_id        INT           NOT NULL,
                                          batch_id      INT,
                                          quantity      INT           NOT NULL,
                                          unit_price    DECIMAL(10,2) NOT NULL,
                                          purchase_cost DECIMAL(10,2) NOT NULL DEFAULT 0.00,  -- Captured at time of sale
                                          subtotal      DECIMAL(10,2) NOT NULL,
                                          is_narcotic   TINYINT(1)    NOT NULL DEFAULT 0,
                                          FOREIGN KEY (sale_id)  REFERENCES sales(sale_id)    ON DELETE CASCADE,
                                          FOREIGN KEY (med_id)   REFERENCES medicines(med_id),
                                          FOREIGN KEY (batch_id) REFERENCES batches(batch_id) ON DELETE SET NULL
);

INSERT IGNORE INTO sale_items
(sale_id, med_id, batch_id, quantity, unit_price, purchase_cost, subtotal, is_narcotic)
VALUES
-- Sale 1 (Ahmed Raza — Hypertension meds)
(1,  9,  9,  30, 120.00,  84.00,  3600.00, 0),
(1, 10, 10,   4, 135.00,  94.50,   540.00, 0),
-- Sale 2 (Fatima Malik — Diabetes meds)
(2,  7,  7,  60, 150.00, 105.00,  9000.00, 0),
(2,  6,  6,   1, 950.00, 600.00,   950.00, 0),
-- Sale 3 (Usman Sheikh — OTC)
(3, 15, 15,  10,  95.00,  66.50,   950.00, 0),
(3, 18, 18,  20,  45.00,  31.50,   900.00, 0),
-- Sale 4 (Walk-in — OTC)
(4,  1,  1,   6,  30.00,  21.00,   180.00, 0),
(4, 16, 16,   3,  40.00,  28.00,   120.00, 0),
(4, 18, 18,   3,  45.00,  31.50,   135.00, 0),
-- Sale 5 (Bilal Hussain — chronic meds)
(5,  9,  9,  30, 120.00,  84.00,  3600.00, 0),
(5, 17, 17,  30, 210.00, 147.00,  6300.00, 0);

-- =============================================================
-- 11. PURCHASE ORDERS
-- =============================================================
CREATE TABLE IF NOT EXISTS purchase_orders (
                                               po_id         INT AUTO_INCREMENT PRIMARY KEY,
                                               supplier_id   INT         NOT NULL,
                                               created_by    INT         NOT NULL,
                                               order_date    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                               expected_date DATE,
                                               received_date DATE,
                                               status        ENUM('PENDING','ORDERED','RECEIVED','CANCELLED') NOT NULL DEFAULT 'PENDING',
                                               total_cost    DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                                               notes         TEXT,
                                               FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id),
                                               FOREIGN KEY (created_by)  REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS po_items (
                                        po_item_id    INT AUTO_INCREMENT PRIMARY KEY,
                                        po_id         INT           NOT NULL,
                                        med_id        INT           NOT NULL,
                                        qty_ordered   INT           NOT NULL,
                                        qty_received  INT           NOT NULL DEFAULT 0,
                                        unit_cost     DECIMAL(10,2) NOT NULL,
                                        pack_cost     DECIMAL(10,2) NOT NULL,
                                        expiry_date   DATE,
                                        batch_number  VARCHAR(50),
                                        FOREIGN KEY (po_id)  REFERENCES purchase_orders(po_id) ON DELETE CASCADE,
                                        FOREIGN KEY (med_id) REFERENCES medicines(med_id)
);

INSERT IGNORE INTO purchase_orders
(po_id, supplier_id, created_by, order_date, expected_date, received_date, status, total_cost, notes)
VALUES
    (1, 2, 1, '2024-10-25 10:00:00', '2024-10-30', '2024-10-29',
     'RECEIVED', 8316.00, 'Monthly antibiotic restock'),

    (2, 1, 1, '2024-11-05 09:00:00', '2024-11-10', NULL,
     'PENDING', 6048.00, 'Low stock replenishment — Panadol and Augmentin');

INSERT IGNORE INTO po_items (po_id, med_id, qty_ordered, qty_received, unit_cost, pack_cost, expiry_date, batch_number) VALUES
-- PO 1 (received)
(1, 2, 60, 60,  77.00,  924.00, '2026-10-31', 'A-2024-10'),
(1, 5, 40, 40, 175.00, 2100.00, '2026-10-31', 'AZ-2024-10'),
-- PO 2 (pending)
(2, 1, 200, 0,  21.00,  504.00, '2027-11-30', NULL),
(2, 3,  20, 0, 154.00, 1848.00, '2027-11-30', NULL);

-- =============================================================
-- 12. NARCOTIC REGISTER
-- =============================================================
CREATE TABLE IF NOT EXISTS narcotic_register (
                                                 entry_id            INT AUTO_INCREMENT PRIMARY KEY,
                                                 med_id              INT          NOT NULL,
                                                 medicine_name       VARCHAR(150) NOT NULL,
                                                 batch_number        VARCHAR(50),
                                                 transaction_type    ENUM('DISPENSED','RECEIVED','RETURNED','DESTROYED','ADJUSTMENT') NOT NULL,
                                                 qty_in              INT NOT NULL DEFAULT 0,
                                                 qty_out             INT NOT NULL DEFAULT 0,
                                                 running_balance     INT NOT NULL,
                                                 prescription_id     INT,
                                                 patient_id          INT,
                                                 patient_name        VARCHAR(100),
                                                 patient_nic         VARCHAR(20),
                                                 doctor_name         VARCHAR(100),
                                                 doctor_reg_no       VARCHAR(50),
                                                 sale_id             INT,
                                                 witness_name        VARCHAR(100),
                                                 witness_designation VARCHAR(100),
                                                 dispensed_by        INT NOT NULL,
                                                 dispensed_by_name   VARCHAR(100),
                                                 notes               TEXT,
                                                 entry_date          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                 FOREIGN KEY (med_id)           REFERENCES medicines(med_id),
                                                 FOREIGN KEY (prescription_id)  REFERENCES prescriptions(prescription_id) ON DELETE SET NULL,
                                                 FOREIGN KEY (patient_id)       REFERENCES patients(patient_id)           ON DELETE SET NULL,
                                                 INDEX idx_narcotic_med  (med_id),
                                                 INDEX idx_narcotic_date (entry_date)
);

-- Opening balance entry for Diazepam
INSERT IGNORE INTO narcotic_register
(entry_id, med_id, medicine_name, batch_number, transaction_type,
 qty_in, qty_out, running_balance,
 dispensed_by, dispensed_by_name, notes, entry_date)
VALUES
    (1, 12, 'Diazepam 5mg', 'D-2024-02', 'RECEIVED',
     60, 0, 60,
     1, 'System Administrator', 'Opening stock received', '2024-02-01 08:00:00');

-- =============================================================
-- 13. NOTIFICATIONS
-- =============================================================
CREATE TABLE IF NOT EXISTS notifications (
                                             notification_id INT AUTO_INCREMENT PRIMARY KEY,
                                             type            VARCHAR(50)  NOT NULL,
                                             priority        ENUM('LOW','NORMAL','HIGH','URGENT') NOT NULL DEFAULT 'NORMAL',
                                             title           VARCHAR(255) NOT NULL,
                                             message         TEXT,
                                             target_user_id  INT,           -- NULL = broadcast to all users
                                             entity_id       INT,
                                             is_read         TINYINT(1)   NOT NULL DEFAULT 0,
                                             read_at         DATETIME,
                                             created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                             FOREIGN KEY (target_user_id) REFERENCES users(user_id) ON DELETE CASCADE,
                                             INDEX idx_notif_user   (target_user_id),
                                             INDEX idx_notif_unread (is_read, created_at)
);

INSERT IGNORE INTO notifications (type, priority, title, message, entity_id, is_read) VALUES
                                                                                          ('LOW_STOCK',  'HIGH',   'Low Stock: Augmentin 625mg',  'Stock at 4 units — below reorder level of 20.',  3, 0),
                                                                                          ('EXPIRY',     'URGENT', 'Expiring Soon: Amoxicillin 250mg','Batch A-2023-11 expires 2025-11-30.',        2, 0),
                                                                                          ('EXPIRY',     'HIGH',   'Expiring Soon: Omeprazole 20mg', 'Batch OME-2023-12 expires 2025-06-30.',      15, 0),
                                                                                          ('LOW_STOCK',  'NORMAL', 'Low Stock: Insulin Glargine',  'Stock at 60 units — approaching reorder level.',6, 0);

-- =============================================================
-- 14. DRUG INTERACTIONS
-- =============================================================
CREATE TABLE IF NOT EXISTS drug_interactions (
                                                 interaction_id INT AUTO_INCREMENT PRIMARY KEY,
                                                 drug1_group    VARCHAR(100) NOT NULL,
                                                 drug2_group    VARCHAR(100) NOT NULL,
                                                 severity       ENUM('CONTRAINDICATED','MAJOR','MODERATE','MINOR','INFO') NOT NULL,
                                                 description    TEXT NOT NULL,
                                                 clinical_effect TEXT,
                                                 management      TEXT,
                                                 source          VARCHAR(100),
                                                 INDEX idx_ddi_groups (drug1_group, drug2_group)
);

INSERT IGNORE INTO drug_interactions
(drug1_group, drug2_group, severity, description, clinical_effect, management, source)
VALUES
    ('Benzodiazepine', 'Opioid', 'CONTRAINDICATED',
     'Concurrent use of benzodiazepines and opioids may result in profound sedation, respiratory depression, coma, and death.',
     'Respiratory depression, loss of consciousness.',
     'Avoid combination. If necessary, limit doses and duration. Monitor closely.',
     'FDA Drug Safety Communication'),

    ('ACE_Inhibitor', 'Potassium_Sparing_Diuretic', 'MAJOR',
     'Concurrent use may lead to hyperkalaemia.',
     'Elevated serum potassium, cardiac arrhythmias.',
     'Monitor serum potassium levels closely. Adjust doses as needed.',
     'British National Formulary'),

    ('Statin', 'CYP3A4_Inhibitor', 'MODERATE',
     'CYP3A4 inhibitors increase statin plasma levels, raising myopathy risk.',
     'Myopathy, rhabdomyolysis.',
     'Use lowest effective statin dose. Consider statin not metabolised by CYP3A4.',
     'ACC/AHA Guidelines'),

    ('Sulfonylurea', 'Fluoroquinolone', 'MODERATE',
     'Fluoroquinolones can potentiate hypoglycaemic effect of sulfonylureas.',
     'Hypoglycaemia.',
     'Monitor blood glucose closely when co-administering.',
     'WHO Drug Information');

-- =============================================================
-- 15. DRUG ALLERGY CROSS-REFS
-- =============================================================
CREATE TABLE IF NOT EXISTS drug_allergy_cross_refs (
                                                       ref_id         INT AUTO_INCREMENT PRIMARY KEY,
                                                       allergen_class VARCHAR(100) NOT NULL,
                                                       drug_class     VARCHAR(100) NOT NULL,
                                                       description    TEXT,
                                                       severity       ENUM('CONTRAINDICATED','MAJOR','MODERATE') NOT NULL DEFAULT 'MAJOR'
);

INSERT IGNORE INTO drug_allergy_cross_refs (allergen_class, drug_class, description, severity) VALUES
                                                                                                   ('Penicillin',  'Cephalosporin', 'Cross-reactivity risk between penicillins and cephalosporins (3-7%). Monitor closely.',   'MODERATE'),
                                                                                                   ('Penicillin',  'Carbapenem',    'Rare cross-reactivity with carbapenems in penicillin-allergic patients.',                  'MODERATE'),
                                                                                                   ('Sulfonamide', 'Sulfonylurea',  'Possible cross-reactivity in sulfonamide-allergic patients.',                              'MODERATE'),
                                                                                                   ('NSAID',       'Aspirin',       'Aspirin/NSAID cross-reactivity in aspirin-sensitive individuals.',                         'MAJOR');

-- =============================================================
-- 16. AUDIT LOG
-- =============================================================
CREATE TABLE IF NOT EXISTS audit_log (
                                         log_id      INT AUTO_INCREMENT PRIMARY KEY,
                                         user_id     INT,
                                         username    VARCHAR(50),
                                         action      VARCHAR(100) NOT NULL,
                                         entity_type VARCHAR(50),
                                         entity_id   INT,
                                         description TEXT,
                                         ip_address  VARCHAR(45),
                                         logged_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                         FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL,
                                         INDEX idx_audit_action (action),
                                         INDEX idx_audit_date   (logged_at),
                                         INDEX idx_audit_user   (user_id)
);

INSERT IGNORE INTO audit_log (user_id, username, action, entity_type, entity_id, description, ip_address) VALUES
                                                                                                              (1, 'admin', 'LOGIN_SUCCESS',   'users',  1, 'Admin logged in successfully.',      '127.0.0.1'),
                                                                                                              (1, 'admin', 'SALE_COMPLETED',  'sales',  1, 'Sale #1 completed — Rs 3,990.',      '127.0.0.1'),
                                                                                                              (1, 'admin', 'SALE_COMPLETED',  'sales',  2, 'Sale #2 completed — Rs 9,950.',      '127.0.0.1'),
                                                                                                              (1, 'admin', 'SHIFT_OPENED',    'shift_sessions', 1, 'Shift opened. Float: Rs 5,000.', '127.0.0.1'),
                                                                                                              (1, 'admin', 'SHIFT_CLOSED',    'shift_sessions', 1, 'Shift closed. Variance: Rs 0.',  '127.0.0.1');

-- =============================================================
-- 17. LOYALTY TRANSACTIONS
-- =============================================================
CREATE TABLE IF NOT EXISTS loyalty_transactions (
                                                    txn_id          INT AUTO_INCREMENT PRIMARY KEY,
                                                    patient_id      INT NOT NULL,
                                                    sale_id         INT,
                                                    points_earned   INT NOT NULL DEFAULT 0,
                                                    points_redeemed INT NOT NULL DEFAULT 0,
                                                    balance_after   INT NOT NULL,
                                                    txn_date        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                    notes           VARCHAR(255),
                                                    FOREIGN KEY (patient_id) REFERENCES patients(patient_id),
                                                    FOREIGN KEY (sale_id)    REFERENCES sales(sale_id) ON DELETE SET NULL
);

INSERT IGNORE INTO loyalty_transactions
(patient_id, sale_id, points_earned, points_redeemed, balance_after, notes)
VALUES
    (1, 1,  40, 0,  40, 'Earned on sale #1 (Rs 3,990 × 1pt/Rs100)'),
    (2, 2, 100, 0, 100, 'Earned on sale #2 (Rs 9,950 × 1pt/Rs100)'),
    (5, 5,  21, 0,  21, 'Earned on sale #5 (Rs 2,110 × 1pt/Rs100)');

-- Update patient loyalty points to match transactions
UPDATE patients SET loyalty_points =  40 WHERE patient_id = 1;
UPDATE patients SET loyalty_points = 100 WHERE patient_id = 2;
UPDATE patients SET loyalty_points =  21 WHERE patient_id = 5;

-- =============================================================
-- 18. VIEWS
-- =============================================================

CREATE OR REPLACE VIEW v_low_stock AS
SELECT
    m.med_id, m.name, m.generic_name, m.category,
    m.stock_qty, m.reorder_level,
    (m.reorder_level - m.stock_qty) AS deficit,
    s.name AS supplier_name, s.phone AS supplier_phone
FROM medicines m
         LEFT JOIN suppliers s ON m.supplier_id = s.supplier_id
WHERE m.stock_qty <= m.reorder_level
  AND m.is_active = 1
ORDER BY m.stock_qty ASC;

CREATE OR REPLACE VIEW v_expiring_batches AS
SELECT
    b.batch_id, b.batch_number, b.expiry_date, b.qty_remaining,
    m.med_id, m.name AS medicine_name, m.category,
    DATEDIFF(b.expiry_date, CURDATE()) AS days_until_expiry,
    ROUND(b.qty_remaining * b.unit_cost, 2) AS cost_at_risk
FROM batches b
         JOIN medicines m ON b.med_id = m.med_id
WHERE b.expiry_date <= DATE_ADD(CURDATE(), INTERVAL 60 DAY)
  AND b.qty_remaining > 0
  AND b.is_quarantined = 0
ORDER BY b.expiry_date ASC;

CREATE OR REPLACE VIEW v_daily_sales_summary AS
SELECT
    DATE(sale_date)                                                       AS sale_date,
    COUNT(*)                                                              AS transactions,
    SUM(grand_total)                                                      AS revenue,
    SUM(discount_amount)                                                  AS discounts,
    SUM(tax_amount)                                                       AS tax,
    SUM(CASE WHEN payment_method = 'CASH'           THEN grand_total ELSE 0 END) AS cash,
    SUM(CASE WHEN payment_method = 'CARD'           THEN grand_total ELSE 0 END) AS card,
    SUM(CASE WHEN payment_method = 'DIGITAL_WALLET' THEN grand_total ELSE 0 END) AS digital
FROM sales
WHERE status = 'COMPLETED'
GROUP BY DATE(sale_date)
ORDER BY sale_date DESC;

CREATE OR REPLACE VIEW v_profit_by_medicine AS
SELECT
    m.med_id, m.name, m.category,
    SUM(si.quantity)                             AS units_sold,
    SUM(si.subtotal)                             AS revenue,
    SUM(si.purchase_cost * si.quantity)          AS cost_of_goods,
    SUM(si.subtotal - (si.purchase_cost * si.quantity)) AS gross_profit,
    ROUND(
            SUM(si.subtotal - (si.purchase_cost * si.quantity))
                / NULLIF(SUM(si.subtotal), 0) * 100, 2
    )                                            AS margin_pct
FROM sale_items si
         JOIN medicines m ON si.med_id = m.med_id
         JOIN sales s ON si.sale_id = s.sale_id
WHERE s.status = 'COMPLETED'
GROUP BY m.med_id, m.name, m.category
ORDER BY gross_profit DESC;

-- =============================================================
-- END OF SCHEMA
-- =============================================================





# -- =============================================================
# --              FILE NAME: sechema_pro.sql
# -- =============================================================
#
# -- =============================================================
# -- MediCare PMS — Professional Edition Database Schema
# -- MySQL / MariaDB compatible  |  utf8mb4
# --
# -- Run this script on a fresh database to create all tables.
# -- All tables from v1 are preserved (backward compatible).
# -- New tables are added with IF NOT EXISTS.
# -- =============================================================
#
# DROP DATABASE IF EXISTS pharmacy_db;
#
# CREATE DATABASE IF NOT EXISTS pharmacy_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
#
# USE pharmacy_db;
#
# -- =============================================================
# -- USERS (enhanced)
# -- =============================================================
# CREATE TABLE IF NOT EXISTS users (
#     user_id             INT AUTO_INCREMENT PRIMARY KEY,
#     username            VARCHAR(50)  NOT NULL UNIQUE,
#     password_hash       VARCHAR(255) NOT NULL,
#     full_name           VARCHAR(100) NOT NULL,
#     role                ENUM('MAIN_ADMIN','ADMIN','PHARMACIST','TECHNICIAN','CASHIER')
#                         NOT NULL DEFAULT 'CASHIER',
#     email               VARCHAR(100),
#     phone               VARCHAR(20),
#     license_number      VARCHAR(50),        -- PMC license for pharmacists
#     is_active           TINYINT(1) NOT NULL DEFAULT 1,
#     must_change_password TINYINT(1) NOT NULL DEFAULT 0,
#     failed_login_count  INT NOT NULL DEFAULT 0,
#     is_locked           TINYINT(1) NOT NULL DEFAULT 0,
#     locked_until        DATETIME,
#     last_login_at       DATETIME,
#     last_login_ip       VARCHAR(45),
#     last_activity_at    DATETIME,
#     created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
#     updated_at          DATETIME ON UPDATE CURRENT_TIMESTAMP,
#     created_by          INT
# );
#
# -- Default admin user (password: Admin@123 — bcrypt hash)
# INSERT IGNORE INTO users (username, password_hash, full_name, role, must_change_password)
# VALUES ('admin',
#         '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQyCjFYXfIp6xhq6oTb.X7Zce',
#         'System Administrator', 'MAIN_ADMIN', 1);
#
# -- =============================================================
# -- PATIENTS (NEW — critical missing entity from v1)
# -- =============================================================
# CREATE TABLE IF NOT EXISTS patients (
#     patient_id          INT AUTO_INCREMENT PRIMARY KEY,
#     patient_code        VARCHAR(20) UNIQUE,        -- PAT-000001
#     first_name          VARCHAR(50) NOT NULL,
#     last_name           VARCHAR(50),
#     full_name           VARCHAR(100),
#     date_of_birth       DATE,
#     gender              ENUM('MALE','FEMALE','OTHER'),
#     blood_group         ENUM('A_POS','A_NEG','B_POS','B_NEG','AB_POS','AB_NEG',
#                              'O_POS','O_NEG','UNKNOWN'),
#     phone               VARCHAR(20),
#     email               VARCHAR(100),
#     nic_number          VARCHAR(20),               -- National ID — required for narcotics
#     address             TEXT,
#     emergency_contact   VARCHAR(100),
#     emergency_phone     VARCHAR(20),
#     -- Clinical (pipe-separated lists stored as text for simplicity)
#     allergies           TEXT,
#     chronic_conditions  TEXT,
#     clinical_notes      TEXT,
#     -- Insurance
#     insurer_name        VARCHAR(100),
#     policy_number       VARCHAR(50),
#     insurer_phone       VARCHAR(20),
#     -- Loyalty
#     loyalty_points      INT NOT NULL DEFAULT 0,
#     loyalty_tier        ENUM('BRONZE','SILVER','GOLD') NOT NULL DEFAULT 'BRONZE',
#     -- Audit
#     is_active           TINYINT(1) NOT NULL DEFAULT 1,
#     created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
#     updated_at          DATETIME ON UPDATE CURRENT_TIMESTAMP,
#     created_by          INT,
#     INDEX idx_patient_phone (phone),
#     INDEX idx_patient_nic   (nic_number),
#     INDEX idx_patient_name  (last_name, first_name)
# );
#
# -- =============================================================
# -- SUPPLIERS (enhanced)
# -- =============================================================
# CREATE TABLE IF NOT EXISTS suppliers (
#     supplier_id     INT AUTO_INCREMENT PRIMARY KEY,
#     name            VARCHAR(100) NOT NULL,
#     contact_name    VARCHAR(100),
#     phone           VARCHAR(20),
#     email           VARCHAR(100),
#     address         TEXT,
#     lead_days       INT NOT NULL DEFAULT 3,
#     payment_terms   VARCHAR(50),   -- "Net 30", "COD", etc.
#     is_active       TINYINT(1) NOT NULL DEFAULT 1,
#     created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
#     updated_at      DATETIME ON UPDATE CURRENT_TIMESTAMP
# );
#
# INSERT IGNORE INTO suppliers (name, contact_name, phone, email, address) VALUES
# ('MedPak Ltd',     'Ali Hassan',    '051-1234567', 'ali@medpak.pk',     'Blue Area, Islamabad'),
# ('PharmaLogix',    'Sara Khan',     '042-7654321', 'sara@pharmalogix.pk','Gulberg III, Lahore'),
# ('HealthPlus Dist','Zara Malik',    '021-9876543', 'zara@healthplus.pk', 'SITE, Karachi'),
# ('Sanofi',         'M. Rizwan',     '0315-9990001','dist@sanofi.pk',     'Multan, Pakistan'),
# ('Getz Pharma',    'Usman Sheikh',  '0333-1234567','orders@getz.pk',     'Faisalabad, Pakistan');
#
# -- =============================================================
# -- MEDICINES (enhanced)
# -- =============================================================
# CREATE TABLE IF NOT EXISTS medicines (
#     med_id                  INT AUTO_INCREMENT PRIMARY KEY,
#     name                    VARCHAR(150) NOT NULL,
#     generic_name            VARCHAR(150),
#     category                VARCHAR(80),
#     therapeutic_class       VARCHAR(100),   -- ATC class
#     dosage_form             VARCHAR(50),    -- Tablet, Capsule, Syrup, etc.
#     strength                VARCHAR(50),    -- "500mg", "250mg/5ml"
#     route_of_admin          VARCHAR(50),    -- Oral, Topical, IV, etc.
#     manufacturer            VARCHAR(100),
#     storage_condition       VARCHAR(100),
#     barcode                 VARCHAR(50) UNIQUE,
#     unit_price              DECIMAL(10,2) NOT NULL DEFAULT 0.00,
#     unit_cost               DECIMAL(10,2) NOT NULL DEFAULT 0.00,
#     pack_cost               DECIMAL(10,2) NOT NULL DEFAULT 0.00,
#     stock_qty               INT NOT NULL DEFAULT 0,
#     reorder_level           INT NOT NULL DEFAULT 20,
#     is_scheduled            TINYINT(1) NOT NULL DEFAULT 0,   -- Rx-only
#     is_narcotic             TINYINT(1) NOT NULL DEFAULT 0,   -- Schedule II+
#     generic_substitution    TINYINT(1) NOT NULL DEFAULT 1,   -- Allow generic sub
#     allergy_class           VARCHAR(100),   -- e.g. "Penicillin" for cross-allergy checks
#     drug_interaction_groups VARCHAR(255),   -- Comma-separated interaction group codes
#     supplier_id             INT,
#     is_active               TINYINT(1) NOT NULL DEFAULT 1,
#     created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
#     updated_at              DATETIME ON UPDATE CURRENT_TIMESTAMP,
#     created_by              INT,
#     FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id) ON DELETE SET NULL,
#     INDEX idx_medicine_name    (name),
#     INDEX idx_medicine_generic (generic_name),
#     FULLTEXT INDEX ft_medicine  (name, generic_name, category)
# );
#
# -- =============================================================
# -- BATCHES
# -- =============================================================
# CREATE TABLE IF NOT EXISTS batches (
#     batch_id        INT AUTO_INCREMENT PRIMARY KEY,
#     med_id          INT NOT NULL,
#     batch_number    VARCHAR(50) NOT NULL,
#     qty_received    INT NOT NULL DEFAULT 0,
#     qty_remaining   INT NOT NULL DEFAULT 0,
#     expiry_date     DATE NOT NULL,
#     unit_cost       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
#     pack_cost       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
#     is_quarantined  TINYINT(1) NOT NULL DEFAULT 0,
#     quarantine_reason VARCHAR(255),
#     quarantined_by  INT,
#     quarantined_at  DATETIME,
#     received_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
#     FOREIGN KEY (med_id) REFERENCES medicines(med_id) ON DELETE CASCADE,
#     INDEX idx_batch_med_expiry (med_id, expiry_date)
# );
#
# -- =============================================================
# -- PRESCRIPTIONS (enhanced)
# -- =============================================================
# CREATE TABLE IF NOT EXISTS prescriptions (
#     prescription_id     INT AUTO_INCREMENT PRIMARY KEY,
#     prescription_number VARCHAR(20) UNIQUE,        -- RX-2024-000001
#     patient_id          INT,
#     patient_name        VARCHAR(100) NOT NULL,
#     patient_nic         VARCHAR(20),
#     doctor_name         VARCHAR(100) NOT NULL,
#     doctor_reg_no       VARCHAR(50),
#     doctor_phone        VARCHAR(20),
#     clinic_name         VARCHAR(100),
#     issued_date         DATE,
#     expiry_date         DATE,
#     validity_days       INT NOT NULL DEFAULT 30,
#     max_refills         INT NOT NULL DEFAULT 0,
#     refills_used        INT NOT NULL DEFAULT 0,
#     diagnosis           VARCHAR(255),
#     notes               TEXT,
#     image_path          VARCHAR(255),
#     is_e_prescription   TINYINT(1) NOT NULL DEFAULT 0,
#     status              ENUM('PENDING','VERIFIED','PARTIAL','DISPENSED','EXPIRED','CANCELLED')
#                         NOT NULL DEFAULT 'PENDING',
#     verified_by         INT,
#     verified_at         DATETIME,
#     created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
#     updated_at          DATETIME ON UPDATE CURRENT_TIMESTAMP,
#     created_by          INT,
#     FOREIGN KEY (patient_id)  REFERENCES patients(patient_id)  ON DELETE SET NULL,
#     FOREIGN KEY (verified_by) REFERENCES users(user_id)         ON DELETE SET NULL,
#     INDEX idx_rx_patient (patient_id),
#     INDEX idx_rx_status  (status)
# );
#
# -- =============================================================
# -- PRESCRIPTION ITEMS
# -- =============================================================
# CREATE TABLE IF NOT EXISTS prescription_items (
#     item_id         INT AUTO_INCREMENT PRIMARY KEY,
#     prescription_id INT NOT NULL,
#     med_id          INT NOT NULL,
#     medicine_name   VARCHAR(150),
#     dosage          VARCHAR(100),
#     frequency       VARCHAR(100),
#     duration        VARCHAR(100),
#     qty_prescribed  INT NOT NULL DEFAULT 1,
#     qty_dispensed   INT NOT NULL DEFAULT 0,
#     is_scheduled    TINYINT(1) NOT NULL DEFAULT 0,
#     is_narcotic     TINYINT(1) NOT NULL DEFAULT 0,
#     instructions    TEXT,
#     FOREIGN KEY (prescription_id) REFERENCES prescriptions(prescription_id) ON DELETE CASCADE,
#     FOREIGN KEY (med_id)          REFERENCES medicines(med_id)
# );
#
# -- =============================================================
# -- SALES (enhanced — added patient_id and session_id)
# -- =============================================================
# CREATE TABLE IF NOT EXISTS sales (
#     sale_id         INT AUTO_INCREMENT PRIMARY KEY,
#     user_id         INT NOT NULL,
#     patient_id      INT,                            -- NEW: patient linkage
#     session_id      INT,                            -- NEW: shift session
#     prescription_id INT,
#     sale_date       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
#     subtotal        DECIMAL(10,2) NOT NULL DEFAULT 0.00,
#     discount_type   ENUM('NONE','PERCENT','FIXED') DEFAULT 'NONE',
#     discount_value  DECIMAL(10,2) NOT NULL DEFAULT 0.00,
#     discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
#     tax_percent     DECIMAL(5,2)  NOT NULL DEFAULT 0.00,
#     tax_amount      DECIMAL(10,2) NOT NULL DEFAULT 0.00,
#     grand_total     DECIMAL(10,2) NOT NULL DEFAULT 0.00,
#     amount_paid     DECIMAL(10,2) NOT NULL DEFAULT 0.00,
#     change_returned DECIMAL(10,2) NOT NULL DEFAULT 0.00,
#     payment_method  ENUM('CASH','CARD','DIGITAL_WALLET') NOT NULL DEFAULT 'CASH',
#     status          ENUM('COMPLETED','VOIDED') NOT NULL DEFAULT 'COMPLETED',
#     void_reason     VARCHAR(255),
#     voided_by       INT,
#     voided_at       DATETIME,
#     FOREIGN KEY (user_id)         REFERENCES users(user_id),
#     FOREIGN KEY (patient_id)      REFERENCES patients(patient_id) ON DELETE SET NULL,
#     FOREIGN KEY (prescription_id) REFERENCES prescriptions(prescription_id) ON DELETE SET NULL,
#     INDEX idx_sale_date   (sale_date),
#     INDEX idx_sale_patient(patient_id)
# );
#
# -- =============================================================
# -- SALE ITEMS (enhanced — added is_narcotic)
# -- =============================================================
# CREATE TABLE IF NOT EXISTS sale_items (
#     sale_item_id    INT AUTO_INCREMENT PRIMARY KEY,
#     sale_id         INT NOT NULL,
#     med_id          INT NOT NULL,
#     batch_id        INT,
#     quantity        INT NOT NULL,
#     unit_price      DECIMAL(10,2) NOT NULL,
#     purchase_cost   DECIMAL(10,2) NOT NULL DEFAULT 0.00,   -- NEW: for margin reporting
#     subtotal        DECIMAL(10,2) NOT NULL,
#     is_narcotic     TINYINT(1) NOT NULL DEFAULT 0,          -- NEW: for narcotic register
#     FOREIGN KEY (sale_id)  REFERENCES sales(sale_id)   ON DELETE CASCADE,
#     FOREIGN KEY (med_id)   REFERENCES medicines(med_id),
#     FOREIGN KEY (batch_id) REFERENCES batches(batch_id) ON DELETE SET NULL
# );
#
# -- =============================================================
# -- PURCHASE ORDERS
# -- =============================================================
# CREATE TABLE IF NOT EXISTS purchase_orders (
#     po_id           INT AUTO_INCREMENT PRIMARY KEY,
#     supplier_id     INT NOT NULL,
#     created_by      INT NOT NULL,
#     order_date      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
#     expected_date   DATE,
#     received_date   DATE,
#     status          ENUM('PENDING','ORDERED','RECEIVED','CANCELLED') NOT NULL DEFAULT 'PENDING',
#     total_cost      DECIMAL(10,2) NOT NULL DEFAULT 0.00,
#     notes           TEXT,
#     FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id),
#     FOREIGN KEY (created_by)  REFERENCES users(user_id)
# );
#
# CREATE TABLE IF NOT EXISTS po_items (
#     po_item_id      INT AUTO_INCREMENT PRIMARY KEY,
#     po_id           INT NOT NULL,
#     med_id          INT NOT NULL,
#     qty_ordered     INT NOT NULL,
#     qty_received    INT NOT NULL DEFAULT 0,
#     unit_cost       DECIMAL(10,2) NOT NULL,
#     pack_cost       DECIMAL(10,2) NOT NULL,
#     expiry_date     DATE,
#     batch_number    VARCHAR(50),
#     FOREIGN KEY (po_id)  REFERENCES purchase_orders(po_id) ON DELETE CASCADE,
#     FOREIGN KEY (med_id) REFERENCES medicines(med_id)
# );
#
# -- =============================================================
# -- NARCOTIC REGISTER (NEW — legally required)
# -- =============================================================
# CREATE TABLE IF NOT EXISTS narcotic_register (
#     entry_id            INT AUTO_INCREMENT PRIMARY KEY,
#     med_id              INT NOT NULL,
#     medicine_name       VARCHAR(150) NOT NULL,
#     batch_number        VARCHAR(50),
#     transaction_type    ENUM('DISPENSED','RECEIVED','RETURNED','DESTROYED','ADJUSTMENT')
#                         NOT NULL,
#     qty_in              INT NOT NULL DEFAULT 0,
#     qty_out             INT NOT NULL DEFAULT 0,
#     running_balance     INT NOT NULL,
#     prescription_id     INT,
#     patient_id          INT,
#     patient_name        VARCHAR(100),
#     patient_nic         VARCHAR(20),
#     doctor_name         VARCHAR(100),
#     doctor_reg_no       VARCHAR(50),
#     sale_id             INT,
#     witness_name        VARCHAR(100),
#     witness_designation VARCHAR(100),
#     dispensed_by        INT NOT NULL,
#     dispensed_by_name   VARCHAR(100),
#     notes               TEXT,
#     entry_date          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
#     FOREIGN KEY (med_id)         REFERENCES medicines(med_id),
#     FOREIGN KEY (prescription_id) REFERENCES prescriptions(prescription_id) ON DELETE SET NULL,
#     FOREIGN KEY (patient_id)     REFERENCES patients(patient_id) ON DELETE SET NULL,
#     INDEX idx_narcotic_med  (med_id),
#     INDEX idx_narcotic_date (entry_date)
# );
#
# -- =============================================================
# -- SHIFT SESSIONS (NEW — cash reconciliation)
# -- =============================================================
# CREATE TABLE IF NOT EXISTS shift_sessions (
#     session_id          INT AUTO_INCREMENT PRIMARY KEY,
#     user_id             INT NOT NULL,
#     cashier_name        VARCHAR(100),
#     opening_float       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
#     closing_float       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
#     total_cash_sales    DECIMAL(10,2) NOT NULL DEFAULT 0.00,
#     total_card_sales    DECIMAL(10,2) NOT NULL DEFAULT 0.00,
#     total_digital_sales DECIMAL(10,2) NOT NULL DEFAULT 0.00,
#     total_sales         DECIMAL(10,2) NOT NULL DEFAULT 0.00,
#     total_refunds       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
#     transaction_count   INT NOT NULL DEFAULT 0,
#     expected_cash       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
#     variance            DECIMAL(10,2) NOT NULL DEFAULT 0.00,
#     variance_notes      TEXT,
#     status              ENUM('OPEN','CLOSED') NOT NULL DEFAULT 'OPEN',
#     opened_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
#     closed_at           DATETIME,
#     FOREIGN KEY (user_id) REFERENCES users(user_id),
#     INDEX idx_shift_user   (user_id),
#     INDEX idx_shift_status (status)
# );
#
# -- =============================================================
# -- NOTIFICATIONS (NEW — in-app alert system)
# -- =============================================================
# CREATE TABLE IF NOT EXISTS notifications (
#     notification_id INT AUTO_INCREMENT PRIMARY KEY,
#     type            VARCHAR(50) NOT NULL,
#     priority        ENUM('LOW','NORMAL','HIGH','URGENT') NOT NULL DEFAULT 'NORMAL',
#     title           VARCHAR(255) NOT NULL,
#     message         TEXT,
#     target_user_id  INT,          -- NULL = all users
#     entity_id       INT,          -- Batch/Medicine/Patient being referenced
#     is_read         TINYINT(1) NOT NULL DEFAULT 0,
#     read_at         DATETIME,
#     created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
#     FOREIGN KEY (target_user_id) REFERENCES users(user_id) ON DELETE CASCADE,
#     INDEX idx_notif_user   (target_user_id),
#     INDEX idx_notif_unread (is_read, created_at)
# );
#
# -- =============================================================
# -- DRUG INTERACTIONS (NEW — DDI safety database)
# -- =============================================================
# CREATE TABLE IF NOT EXISTS drug_interactions (
#     interaction_id  INT AUTO_INCREMENT PRIMARY KEY,
#     drug1_group     VARCHAR(100) NOT NULL,
#     drug2_group     VARCHAR(100) NOT NULL,
#     severity        ENUM('CONTRAINDICATED','MAJOR','MODERATE','MINOR','INFO') NOT NULL,
#     description     TEXT NOT NULL,
#     clinical_effect TEXT,
#     management      TEXT,
#     source          VARCHAR(100),
#     INDEX idx_ddi_groups (drug1_group, drug2_group)
# );
#
# -- =============================================================
# -- DRUG ALLERGY CROSS-REFS (NEW — cross-class allergy checking)
# -- =============================================================
# CREATE TABLE IF NOT EXISTS drug_allergy_cross_refs (
#     ref_id          INT AUTO_INCREMENT PRIMARY KEY,
#     allergen_class  VARCHAR(100) NOT NULL,
#     drug_class      VARCHAR(100) NOT NULL,
#     description     TEXT,
#     severity        ENUM('CONTRAINDICATED','MAJOR','MODERATE') NOT NULL DEFAULT 'MAJOR'
# );
#
# -- Seed: Penicillin allergy → cross-reacts with Cephalosporins
# INSERT IGNORE INTO drug_allergy_cross_refs (allergen_class, drug_class, description, severity) VALUES
# ('Penicillin', 'Cephalosporin', 'Cross-reactivity risk between penicillins and cephalosporins (3-7%). Monitor closely.', 'MODERATE'),
# ('Sulfonamide', 'Sulfonylurea', 'Possible cross-reactivity in sulfonamide-allergic patients.', 'MODERATE'),
# ('NSAID',       'Aspirin',      'Aspirin/NSAID cross-reactivity in sensitive individuals.', 'MAJOR');
#
# -- =============================================================
# -- AUDIT LOG (enhanced — added username column)
# -- =============================================================
# CREATE TABLE IF NOT EXISTS audit_log (
#     log_id          INT AUTO_INCREMENT PRIMARY KEY,
#     user_id         INT,
#     username        VARCHAR(50),
#     action          VARCHAR(100) NOT NULL,
#     entity_type     VARCHAR(50),
#     entity_id       INT,
#     description     TEXT,
#     ip_address      VARCHAR(45),
#     logged_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
#     FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL,
#     INDEX idx_audit_action (action),
#     INDEX idx_audit_date   (logged_at),
#     INDEX idx_audit_user   (user_id)
# );
#
# -- =============================================================
# -- LOYALTY TRANSACTIONS (NEW)
# -- =============================================================
# CREATE TABLE IF NOT EXISTS loyalty_transactions (
#     txn_id          INT AUTO_INCREMENT PRIMARY KEY,
#     patient_id      INT NOT NULL,
#     sale_id         INT,
#     points_earned   INT NOT NULL DEFAULT 0,
#     points_redeemed INT NOT NULL DEFAULT 0,
#     balance_after   INT NOT NULL,
#     txn_date        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
#     notes           VARCHAR(255),
#     FOREIGN KEY (patient_id) REFERENCES patients(patient_id),
#     FOREIGN KEY (sale_id)    REFERENCES sales(sale_id) ON DELETE SET NULL
# );
#
# -- =============================================================
# -- VIEWS — for common report queries
# -- =============================================================
#
# CREATE OR REPLACE VIEW v_low_stock AS
# SELECT m.med_id, m.name, m.generic_name, m.category,
#        m.stock_qty, m.reorder_level,
#        (m.reorder_level - m.stock_qty) AS deficit,
#        s.name AS supplier_name, s.phone AS supplier_phone
# FROM medicines m
# LEFT JOIN suppliers s ON m.supplier_id = s.supplier_id
# WHERE m.stock_qty <= m.reorder_level AND m.is_active = 1
# ORDER BY m.stock_qty ASC;
#
# CREATE OR REPLACE VIEW v_expiring_batches AS
# SELECT b.batch_id, b.batch_number, b.expiry_date, b.qty_remaining,
#        m.name AS medicine_name, m.category,
#        DATEDIFF(b.expiry_date, CURDATE()) AS days_until_expiry,
#        b.qty_remaining * b.unit_cost AS cost_at_risk
# FROM batches b
# JOIN medicines m ON b.med_id = m.med_id
# WHERE b.expiry_date <= DATE_ADD(CURDATE(), INTERVAL 60 DAY)
#   AND b.qty_remaining > 0
#   AND COALESCE(b.is_quarantined, 0) = 0
# ORDER BY b.expiry_date ASC;
#
# CREATE OR REPLACE VIEW v_daily_sales_summary AS
# SELECT
#     DATE(sale_date) AS sale_date,
#     COUNT(*) AS transactions,
#     SUM(grand_total) AS revenue,
#     SUM(discount_amount) AS discounts,
#     SUM(tax_amount) AS tax,
#     SUM(CASE WHEN payment_method='CASH' THEN grand_total ELSE 0 END) AS cash,
#     SUM(CASE WHEN payment_method='CARD' THEN grand_total ELSE 0 END) AS card,
#     SUM(CASE WHEN payment_method='DIGITAL_WALLET' THEN grand_total ELSE 0 END) AS digital
# FROM sales
# WHERE status = 'COMPLETED'
# GROUP BY DATE(sale_date)
# ORDER BY sale_date DESC;
