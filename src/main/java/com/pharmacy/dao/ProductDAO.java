package com.pharmacy.dao;

import com.pharmacy.model.Medicine;
import com.pharmacy.model.Product;
import com.pharmacy.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;

/** Shared catalog access for medicine and general retail products. */
public class ProductDAO {
    public record CategoryOption(String code, String name, boolean requiresExpiry, boolean requiresPrescription) {}
    public record ExpiringProduct(String name, String category, String batchNumber, int quantity,
                                  LocalDate expiryDate, double unitCost) {}

    private static final String SELECT_PRODUCTS = """
            SELECT p.*, pc.code AS category_code, pc.name AS category_name,
                   COALESCE(NULLIF(p.tax_percent, 0), pc.default_tax_percent) AS effective_tax_percent,
                   COALESCE(NULLIF(p.discount_percent, 0), pc.default_discount_percent) AS effective_discount_percent,
                   m.med_id, m.generic_name, m.is_scheduled, m.is_narcotic,
                   m.dosage_form, m.strength, m.unit_price AS medicine_unit_price,
                   m.unit_cost AS medicine_unit_cost, m.stock_qty AS medicine_stock_qty,
                   m.reorder_level AS medicine_reorder_level, m.category AS medicine_category,
                   m.allergy_class, m.drug_interaction_groups, m.therapeutic_class
            FROM products p
            JOIN product_categories pc ON pc.category_id = p.category_id
            LEFT JOIN medicines m ON m.med_id = p.legacy_med_id
            """;

    public List<Product> search(String text) throws SQLException {
        String search = "%" + text.trim() + "%";
        String sql = SELECT_PRODUCTS + """
                 WHERE p.is_active = 1
                   AND (p.name LIKE ? OR p.brand LIKE ? OR p.size_or_weight LIKE ?
                        OR p.flavor LIKE ? OR p.barcode LIKE ? OR pc.name LIKE ?
                        OR m.generic_name LIKE ? OR m.category LIKE ?)
                 ORDER BY p.name
                """;
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 1; i <= 8; i++) {
                ps.setString(i, search);
            }
            return readProducts(ps.executeQuery());
        }
    }

    public List<Product> getLowStock() throws SQLException {
        return query(SELECT_PRODUCTS
                + " WHERE p.is_active = 1 AND p.stock_qty <= p.reorder_level ORDER BY p.stock_qty, p.name");
    }

    public List<CategoryOption> getCategories() throws SQLException {
        List<CategoryOption> categories = new ArrayList<>();
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT code, name, requires_expiry, requires_prescription "
                             + "FROM product_categories WHERE is_active = 1 ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) categories.add(new CategoryOption(rs.getString("code"), rs.getString("name"),
                    rs.getBoolean("requires_expiry"), rs.getBoolean("requires_prescription")));
        }
        return categories;
    }


    public List<Product> getFiltered(String text, String categoryCode, String brand, String stockStatus,
                                     String expiryStatus) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT_PRODUCTS).append(" WHERE p.is_active = 1 ");
        List<String> values = new ArrayList<>();
        if (text != null && !text.isBlank()) {
            sql.append("AND (p.name LIKE ? OR p.brand LIKE ? OR p.size_or_weight LIKE ? OR p.flavor LIKE ? ")
                    .append("OR p.barcode LIKE ? OR pc.name LIKE ? OR m.generic_name LIKE ? OR m.category LIKE ?) ");
            String like = "%" + text.trim() + "%";
            for (int i = 0; i < 8; i++) values.add(like);
        }

        if (categoryCode != null && !categoryCode.isBlank() && !"ALL".equals(categoryCode)) {
            sql.append("AND pc.code = ? ");
            values.add(categoryCode);
        }
        if (brand != null && !brand.isBlank()) {
            sql.append("AND p.brand LIKE ? ");
            values.add("%" + brand.trim() + "%");
        }
        if ("LOW".equals(stockStatus)) sql.append("AND p.stock_qty > 0 AND p.stock_qty <= p.reorder_level ");
        else if ("OUT".equals(stockStatus)) sql.append("AND p.stock_qty <= 0 ");
        else if ("IN_STOCK".equals(stockStatus)) sql.append("AND p.stock_qty > 0 ");
        if ("EXPIRING".equals(expiryStatus) || "EXPIRED".equals(expiryStatus)) {
            boolean expired = "EXPIRED".equals(expiryStatus);
            sql.append("AND p.tracks_expiry = 1 AND (")
                    .append("EXISTS (SELECT 1 FROM product_batches pb WHERE pb.product_id=p.product_id ")
                    .append(expired ? "AND pb.expiry_date < CURRENT_DATE " : "AND pb.expiry_date BETWEEN CURRENT_DATE AND DATE_ADD(CURRENT_DATE, INTERVAL 90 DAY) ")
                    .append("AND pb.qty_remaining > 0 AND pb.is_quarantined = 0) OR ")
                    .append("EXISTS (SELECT 1 FROM batches b WHERE b.med_id=p.legacy_med_id ")
                    .append(expired ? "AND b.expiry_date < CURRENT_DATE " : "AND b.expiry_date BETWEEN CURRENT_DATE AND DATE_ADD(CURRENT_DATE, INTERVAL 90 DAY) ")
                    .append("AND b.qty_remaining > 0 AND COALESCE(b.is_quarantined,0)=0)) ");
        }
        sql.append("ORDER BY p.name");
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < values.size(); i++) ps.setString(i + 1, values.get(i));
            return readProducts(ps.executeQuery());
        }
    }

    public List<ExpiringProduct> getExpiringRetailProducts(LocalDate throughDate) throws SQLException {
        String sql = """
                SELECT p.name, pc.name AS category, pb.batch_number, pb.qty_remaining,
                       pb.expiry_date, pb.unit_cost
                FROM product_batches pb
                JOIN products p ON p.product_id = pb.product_id
                JOIN product_categories pc ON pc.category_id = p.category_id
                WHERE p.is_active = 1 AND p.tracks_expiry = 1 AND p.legacy_med_id IS NULL
                  AND pb.qty_remaining > 0 AND pb.expiry_date <= ?
                  AND pb.is_quarantined = 0
                ORDER BY pb.expiry_date, p.name
                """;
        List<ExpiringProduct> rows = new ArrayList<>();
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, java.sql.Date.valueOf(throughDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new ExpiringProduct(rs.getString("name"), rs.getString("category"),
                            rs.getString("batch_number"), rs.getInt("qty_remaining"),
                            rs.getDate("expiry_date").toLocalDate(), rs.getDouble("unit_cost")));
                }
            }
        }
        return rows;
    }

    /** Receives retail stock through the single stock path. */
    public void receiveStock(int productId, int quantity, double unitCost, String batchNumber,
                             LocalDate expiryDate) throws SQLException {
        try (Connection con = DatabaseConnection.getInstance().getConnection()) {
            con.setAutoCommit(false);
            try {
                new com.pharmacy.service.StockService().receive(con, new com.pharmacy.service.StockService.ReceiveRequest(
                        productId, quantity, unitCost, 0, batchNumber, expiryDate, "MANUAL", null, null));
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    public List<Product> getBySupplier(int supplierId) throws SQLException {
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_PRODUCTS
                     + " WHERE p.is_active = 1 AND p.supplier_id = ? ORDER BY p.name")) {
            ps.setInt(1, supplierId);
            return readProducts(ps.executeQuery());
        }
    }

    public Product getById(Connection con, int productId, boolean lock) throws SQLException {
        String sql = SELECT_PRODUCTS + " WHERE p.product_id = ?" + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapProduct(rs) : null;
            }
        }
    }

    public Product getByMedicineId(int medicineId) throws SQLException {
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_PRODUCTS + " WHERE p.legacy_med_id = ?")) {
            ps.setInt(1, medicineId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapProduct(rs) : null;
            }
        }
    }

    public int insert(Product product) throws SQLException {
        String sql = """
                INSERT INTO products
                    (category_id, name, brand, size_or_weight, flavor, barcode, unit_price, unit_cost,
                     stock_qty, reorder_level, tax_percent, discount_percent, tracks_expiry,
                     requires_prescription, is_active)
                SELECT category_id, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, (? OR requires_expiry),
                       (? OR requires_prescription), ?
                FROM product_categories WHERE code = ?
                """;
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setProductParams(ps, product);
            ps.setString(15, product.getCategoryCode());
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Product category does not exist: " + product.getCategoryCode());
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Product could not be created.");
    }

    public void update(Product product) throws SQLException {
        String sql = """
                UPDATE products p
                JOIN product_categories pc ON pc.code = ?
                SET p.category_id = pc.category_id, p.name = ?, p.brand = ?, p.size_or_weight = ?,
                    p.flavor = ?, p.barcode = ?, p.unit_price = ?, p.unit_cost = ?,
                    p.reorder_level = ?, p.tax_percent = ?, p.discount_percent = ?,
                    p.tracks_expiry = (? OR pc.requires_expiry),
                    p.requires_prescription = (? OR pc.requires_prescription)
                WHERE p.product_id = ?
                """;
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, product.getCategoryCode());
            ps.setString(2, product.getName());
            ps.setString(3, product.getBrand());
            ps.setString(4, product.getSizeOrWeight());
            ps.setString(5, product.getFlavor());
            ps.setString(6, product.getBarcode());
            ps.setDouble(7, product.getUnitPrice());
            ps.setDouble(8, product.getUnitCost());
            ps.setInt(9, product.getReorderLevel());
            ps.setDouble(10, product.getTaxPercent());
            ps.setDouble(11, product.getDiscountPercent());
            ps.setBoolean(12, product.isTracksExpiry());
            ps.setBoolean(13, product.isRequiresPrescription());
            ps.setInt(14, product.getProductId());
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Product not found or category does not exist.");
            }
        }
    }

    public void updateStock(Connection con, int productId, int delta) throws SQLException {
        String sql = "UPDATE products SET stock_qty = stock_qty + ? "
                + "WHERE product_id = ? AND stock_qty + ? >= 0";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, delta);
            ps.setInt(2, productId);
            ps.setInt(3, delta);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Insufficient stock or unknown product ID " + productId);
            }
        }
    }

    private List<Product> query(String sql) throws SQLException {
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return readProducts(rs);
        }
    }

    private List<Product> readProducts(ResultSet rs) throws SQLException {
        List<Product> products = new ArrayList<>();
        try (rs) {
            while (rs.next()) {
                products.add(mapProduct(rs));
            }
        }
        return products;
    }

    private Product mapProduct(ResultSet rs) throws SQLException {
        Product product = new Product();
        product.setProductId(rs.getInt("product_id"));
        int medId = rs.getInt("med_id");
        product.setMedicineId(rs.wasNull() ? null : medId);
        product.setCategoryCode(rs.getString("category_code"));
        product.setCategoryName(rs.getString("category_name"));
        product.setName(rs.getString("name"));
        product.setBrand(rs.getString("brand"));
        product.setSizeOrWeight(rs.getString("size_or_weight"));
        product.setFlavor(rs.getString("flavor"));
        product.setBarcode(rs.getString("barcode"));
        product.setUnitPrice(rs.getDouble("unit_price"));
        product.setUnitCost(rs.getDouble("unit_cost"));
        product.setStockQty(rs.getInt("stock_qty"));
        product.setReorderLevel(rs.getInt("reorder_level"));
        product.setTaxPercent(rs.getDouble("effective_tax_percent"));
        product.setDiscountPercent(rs.getDouble("effective_discount_percent"));
        product.setTracksExpiry(rs.getBoolean("tracks_expiry"));
        product.setRequiresPrescription(rs.getBoolean("requires_prescription"));
        product.setActive(rs.getBoolean("is_active"));
        product.setSupplierId(rs.getInt("supplier_id"));
        if (product.isMedicine()) {
            Medicine medicine = new Medicine();
            medicine.setMedId(medId);
            medicine.setName(product.getName());
            medicine.setGenericName(rs.getString("generic_name"));
            medicine.setCategory(rs.getString("medicine_category"));
            medicine.setBarcode(product.getBarcode());
            medicine.setUnitPrice(product.getUnitPrice());
            medicine.setUnitCost(product.getUnitCost());
            medicine.setStockQty(product.getStockQty());
            medicine.setReorderLevel(product.getReorderLevel());
            medicine.setScheduled(rs.getBoolean("is_scheduled"));
            medicine.setNarcotics(rs.getBoolean("is_narcotic"));
            medicine.setDosageForm(rs.getString("dosage_form"));
            medicine.setStrength(rs.getString("strength"));
            medicine.setAllergyClass(rs.getString("allergy_class"));
            medicine.setDrugInteractionGroups(rs.getString("drug_interaction_groups"));
            medicine.setTherapeuticClass(rs.getString("therapeutic_class"));
            product.setMedicine(medicine);
            product.setRequiresPrescription(product.isRequiresPrescription() || medicine.isScheduled());
        }
        return product;
    }

    private void setProductParams(PreparedStatement ps, Product product) throws SQLException {
        ps.setString(1, product.getName());
        ps.setString(2, product.getBrand());
        ps.setString(3, product.getSizeOrWeight());
        ps.setString(4, product.getFlavor());
        ps.setString(5, product.getBarcode());
        ps.setDouble(6, product.getUnitPrice());
        ps.setDouble(7, product.getUnitCost());
        ps.setInt(8, product.getStockQty());
        ps.setInt(9, product.getReorderLevel());
        ps.setDouble(10, product.getTaxPercent());
        ps.setDouble(11, product.getDiscountPercent());
        ps.setBoolean(12, product.isTracksExpiry());
        ps.setBoolean(13, product.isRequiresPrescription());
        ps.setBoolean(14, product.isActive());
    }
}
