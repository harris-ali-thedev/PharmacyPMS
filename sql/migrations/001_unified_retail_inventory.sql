-- Additive retail inventory migration. Run once against an existing PharmacyPMS database.
-- Legacy medicine IDs, medicine rows, batch rows, and sale history are preserved.

CREATE TABLE IF NOT EXISTS product_categories (
    category_id INT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL,
    default_tax_percent DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    default_discount_percent DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    requires_expiry TINYINT(1) NOT NULL DEFAULT 0,
    requires_prescription TINYINT(1) NOT NULL DEFAULT 0,
    is_active TINYINT(1) NOT NULL DEFAULT 1
);

INSERT IGNORE INTO product_categories
    (code, name, default_tax_percent, default_discount_percent, requires_expiry, requires_prescription)
VALUES
    ('MEDICINE', 'Medicines', 0.00, 0.00, 1, 0),
    ('BEVERAGE', 'Beverages', 0.00, 0.00, 0, 0),
    ('ICE_CREAM', 'Ice Cream', 0.00, 0.00, 1, 0),
    ('COSMETIC', 'Cosmetics', 0.00, 0.00, 0, 0),
    ('GENERAL', 'General', 0.00, 0.00, 0, 0);

CREATE TABLE IF NOT EXISTS products (
    product_id INT AUTO_INCREMENT PRIMARY KEY,
    legacy_med_id INT NULL UNIQUE,
    category_id INT NOT NULL,
    name VARCHAR(150) NOT NULL,
    brand VARCHAR(100),
    size_or_weight VARCHAR(80),
    flavor VARCHAR(80),
    barcode VARCHAR(50) UNIQUE,
    unit_price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    unit_cost DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    stock_qty INT NOT NULL DEFAULT 0,
    reorder_level INT NOT NULL DEFAULT 20,
    tax_percent DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    discount_percent DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    tracks_expiry TINYINT(1) NOT NULL DEFAULT 0,
    requires_prescription TINYINT(1) NOT NULL DEFAULT 0,
    supplier_id INT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_products_legacy_medicine
        FOREIGN KEY (legacy_med_id) REFERENCES medicines(med_id) ON DELETE SET NULL,
    CONSTRAINT fk_products_category
        FOREIGN KEY (category_id) REFERENCES product_categories(category_id),
    CONSTRAINT fk_products_supplier
        FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id) ON DELETE SET NULL,
    INDEX idx_products_name (name),
    INDEX idx_products_category_stock (category_id, stock_qty),
    INDEX idx_products_expiry (tracks_expiry, is_active)
);

-- Keep existing medicine IDs as product IDs during the initial backfill for easy reconciliation.
INSERT INTO products
    (product_id, legacy_med_id, category_id, name, barcode, unit_price, unit_cost,
     stock_qty, reorder_level, tracks_expiry, requires_prescription, supplier_id, is_active)
SELECT
    m.med_id, m.med_id, pc.category_id, m.name, m.barcode, m.unit_price, m.unit_cost,
    m.stock_qty, m.reorder_level, 1, COALESCE(m.is_scheduled, 0), m.supplier_id, m.is_active
FROM medicines m
JOIN product_categories pc ON pc.code = 'MEDICINE'
ON DUPLICATE KEY UPDATE
    legacy_med_id = VALUES(legacy_med_id),
    category_id = VALUES(category_id),
    name = VALUES(name),
    barcode = VALUES(barcode),
    unit_price = VALUES(unit_price),
    unit_cost = VALUES(unit_cost),
    stock_qty = VALUES(stock_qty),
    reorder_level = VALUES(reorder_level),
    supplier_id = VALUES(supplier_id),
    is_active = VALUES(is_active);

CREATE TABLE IF NOT EXISTS product_batches (
    batch_id INT AUTO_INCREMENT PRIMARY KEY,
    product_id INT NOT NULL,
    batch_number VARCHAR(80),
    qty_received INT NOT NULL DEFAULT 0,
    qty_remaining INT NOT NULL DEFAULT 0,
    expiry_date DATE NOT NULL,
    unit_cost DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    is_quarantined TINYINT(1) NOT NULL DEFAULT 0,
    received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_batches_product
        FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE,
    INDEX idx_product_batches_fifo (product_id, expiry_date, qty_remaining),
    INDEX idx_product_batches_expiry (expiry_date, qty_remaining)
);

ALTER TABLE sale_items
    MODIFY COLUMN med_id INT NULL,
    ADD COLUMN product_id INT NULL AFTER med_id,
    ADD COLUMN line_discount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN tax_percent DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN tax_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN product_batch_id INT NULL,
    ADD CONSTRAINT fk_sale_items_product
        FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_sale_items_product_batch
        FOREIGN KEY (product_batch_id) REFERENCES product_batches(batch_id) ON DELETE SET NULL,
    ADD INDEX idx_sale_items_product (product_id);

UPDATE sale_items si
JOIN products p ON p.legacy_med_id = si.med_id
SET si.product_id = p.product_id;

-- Allocate historical sale-level discounts and tax to their lines for category reporting.
UPDATE sale_items si
JOIN sales s ON s.sale_id = si.sale_id
JOIN (
    SELECT sale_id, SUM(subtotal) AS line_subtotal
    FROM sale_items
    GROUP BY sale_id
) totals ON totals.sale_id = si.sale_id
SET si.line_discount = CASE
        WHEN totals.line_subtotal > 0 THEN s.discount_amount * si.subtotal / totals.line_subtotal
        ELSE 0
    END,
    si.tax_percent = COALESCE(s.tax_percent, 0),
    si.tax_amount = CASE
        WHEN totals.line_subtotal > 0
        THEN (si.subtotal - CASE
            WHEN totals.line_subtotal > 0 THEN s.discount_amount * si.subtotal / totals.line_subtotal
            ELSE 0
        END) * COALESCE(s.tax_percent, 0) / 100
        ELSE 0
    END;

ALTER TABLE po_items
    MODIFY COLUMN med_id INT NULL,
    ADD COLUMN product_id INT NULL AFTER med_id,
    ADD CONSTRAINT fk_po_items_product
        FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE SET NULL,
    ADD INDEX idx_po_items_product (product_id);

UPDATE po_items pi
JOIN products p ON p.legacy_med_id = pi.med_id
SET pi.product_id = p.product_id;
