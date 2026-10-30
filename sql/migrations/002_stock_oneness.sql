-- =====================================================================
-- 002_stock_oneness.sql
-- Run AFTER 001_unified_retail_inventory.sql, once. Take a backup first.
--
-- RULE: products.stock_qty is the ONE authoritative on-hand figure.
--   * on-hand = units physically held and NOT quarantined
--   * medicines.stock_qty becomes a read-only mirror (trigger below)
--   * for expiry-tracked products, SUM(non-quarantined lot qty) must equal
--     products.stock_qty (checked by v_stock_reconciliation)
--   * every change goes through StockService and leaves a stock_movements row
-- =====================================================================

-- 1. Quarantine metadata for retail lots (medicine batches already have it)
ALTER TABLE product_batches
    ADD COLUMN quarantine_reason VARCHAR(255) NULL,
    ADD COLUMN quarantined_by    INT NULL,
    ADD COLUMN quarantined_at    DATETIME NULL;

-- 2. Stock ledger: one table for every product, every movement
CREATE TABLE IF NOT EXISTS stock_movements (
    movement_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id    INT NOT NULL,
    movement_type ENUM('OPENING','PURCHASE_RECEIPT','SALE','VOID_RETURN','ADJUSTMENT',
                       'QUARANTINE','DISPOSAL','SUPPLIER_RETURN') NOT NULL,
    lot_type      ENUM('NONE','MEDICINE_BATCH','PRODUCT_BATCH') NOT NULL DEFAULT 'NONE',
    lot_id        INT NULL,
    qty_delta     INT NOT NULL,
    balance_after INT NOT NULL,
    ref_type      VARCHAR(30) NULL,          -- SALE, PURCHASE_ORDER, STOCK_TAKE ...
    ref_id        INT NULL,
    user_id       INT NULL,
    notes         VARCHAR(255) NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stock_mov_product FOREIGN KEY (product_id) REFERENCES products(product_id),
    INDEX idx_stock_mov_product (product_id, created_at),
    INDEX idx_stock_mov_ref (ref_type, ref_id)
);

-- Opening balance for stock that existed before the ledger
INSERT INTO stock_movements (product_id, movement_type, qty_delta, balance_after, ref_type, notes)
SELECT p.product_id, 'OPENING', p.stock_qty, p.stock_qty, 'MIGRATION', 'Opening balance at migration 002'
FROM products p
WHERE p.stock_qty > 0
  AND NOT EXISTS (SELECT 1 FROM stock_movements sm WHERE sm.product_id = p.product_id);

-- 3. products is the authority; medicines.stock_qty mirrors it automatically
DROP TRIGGER IF EXISTS trg_products_mirror_medicine_stock;
DELIMITER //
CREATE TRIGGER trg_products_mirror_medicine_stock
AFTER UPDATE ON products
FOR EACH ROW
BEGIN
    IF NEW.legacy_med_id IS NOT NULL AND NEW.stock_qty <> OLD.stock_qty THEN
        UPDATE medicines SET stock_qty = NEW.stock_qty WHERE med_id = NEW.legacy_med_id;
    END IF;
END//
DELIMITER ;

-- 4. Stock can never go negative at the database level (MySQL 8.0.16+ / MariaDB 10.2+)
--    If this fails, fix negative rows first (see v_stock_reconciliation).
ALTER TABLE products        ADD CONSTRAINT chk_products_stock_nonneg CHECK (stock_qty >= 0);
ALTER TABLE batches         ADD CONSTRAINT chk_batches_qty_nonneg    CHECK (qty_remaining >= 0);
ALTER TABLE product_batches ADD CONSTRAINT chk_pbatches_qty_nonneg   CHECK (qty_remaining >= 0);

-- 5. Reconciliation view: any row whose status <> 'OK' is a stock-integrity bug
CREATE OR REPLACE VIEW v_stock_reconciliation AS
SELECT p.product_id,
       p.name,
       pc.name              AS category,
       p.tracks_expiry,
       p.stock_qty          AS product_qty,
       m.stock_qty          AS medicine_qty,
       CASE WHEN p.tracks_expiry = 1 THEN COALESCE(l.qty, 0) END AS lot_qty,
       CASE
           WHEN m.med_id IS NOT NULL AND m.stock_qty <> p.stock_qty        THEN 'MEDICINE_MIRROR_MISMATCH'
           WHEN p.tracks_expiry = 1 AND COALESCE(l.qty, 0) <> p.stock_qty  THEN 'LOT_MISMATCH'
           ELSE 'OK'
       END                  AS status
FROM products p
JOIN product_categories pc ON pc.category_id = p.category_id
LEFT JOIN medicines m      ON m.med_id = p.legacy_med_id
LEFT JOIN (
    SELECT product_id, SUM(qty) AS qty FROM (
        SELECT product_id, qty_remaining AS qty
        FROM product_batches WHERE is_quarantined = 0
        UNION ALL
        SELECT pr.product_id, b.qty_remaining
        FROM batches b JOIN products pr ON pr.legacy_med_id = b.med_id
        WHERE COALESCE(b.is_quarantined, 0) = 0
    ) lots GROUP BY product_id
) l ON l.product_id = p.product_id
WHERE p.is_active = 1;

-- 6. One-time repair (REVIEW the view output first, then run if you accept lots as truth):
-- UPDATE products p
-- JOIN (SELECT product_id, lot_qty FROM v_stock_reconciliation WHERE status = 'LOT_MISMATCH') x
--   ON x.product_id = p.product_id
-- SET p.stock_qty = x.lot_qty;   -- trigger mirrors it into medicines
