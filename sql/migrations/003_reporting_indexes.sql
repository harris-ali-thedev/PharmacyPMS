-- Apply once after 002_stock_oneness.sql. Product and stock reference indexes already exist.
CREATE INDEX idx_sales_status_date ON sales(status, sale_date);
CREATE INDEX idx_stock_mov_type_date ON stock_movements(movement_type, created_at);
CREATE INDEX idx_rx_items_prescription_medicine ON prescription_items(prescription_id, med_id);

-- Inspect plans against representative data after upgrading:
EXPLAIN SELECT SUM(grand_total) FROM sales
WHERE status='COMPLETED' AND sale_date >= CURRENT_DATE;
EXPLAIN SELECT * FROM stock_movements WHERE product_id=1 ORDER BY created_at;
