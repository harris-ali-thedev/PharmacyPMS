package com.pharmacy.service;

import com.pharmacy.dao.BatchDAO;
import com.pharmacy.dao.NarcoticRegisterDAO;
import com.pharmacy.dao.ProductBatchDAO;
import com.pharmacy.model.Batch;
import com.pharmacy.model.NarcoticRegisterEntry;
import com.pharmacy.model.User;
import com.pharmacy.util.AuditLogger;
import com.pharmacy.util.DatabaseConnection;
import com.pharmacy.util.SessionManager;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * ONE place that changes stock.
 *
 * Authority: products.stock_qty (= on-hand, excluding quarantined units).
 * medicines.stock_qty is mirrored by DB trigger trg_products_mirror_medicine_stock.
 * Every method takes the caller's Connection so stock changes commit or roll back
 * together with the sale / purchase order / stock-take that caused them.
 */
public class StockService {

    public enum MovementType {
        OPENING, PURCHASE_RECEIPT, SALE, VOID_RETURN, ADJUSTMENT, QUARANTINE, DISPOSAL, SUPPLIER_RETURN
    }

    public record ReceiveRequest(int productId, int quantity, double unitCost, double packCost,
                                 String batchNumber, LocalDate expiryDate,
                                 String refType, Integer refId, String notes) {}

    public record StockCheck(int productId, String name, String category, boolean tracksExpiry,
                             int productQty, Integer medicineQty, Integer lotQty, String status) {
        public boolean ok() { return "OK".equals(status); }
    }

    private record ProductRow(int id, String name, boolean tracksExpiry, int stock,
                              Integer medId, boolean narcotic) {}

    private final NarcoticRegisterDAO narcoticDAO = new NarcoticRegisterDAO();
    private final ProductBatchDAO productBatchDAO = new ProductBatchDAO();
    private final BatchDAO batchDAO = new BatchDAO();

    // ── Receiving (purchase orders, add-stock screens, opening stock) ────────

    /** Adds stock. Creates a lot for expiry-tracked products. Returns the lot id (0 if none). */
    public int receive(Connection con, ReceiveRequest r) throws SQLException {
        if (r.quantity() <= 0) throw new IllegalArgumentException("Quantity must be positive.");
        if (!Double.isFinite(r.unitCost()) || !Double.isFinite(r.packCost()) || r.unitCost() < 0 || r.packCost() < 0)
            throw new IllegalArgumentException("Costs must be finite and non-negative.");
        ProductRow p = lock(con, r.productId());

        int lotId = 0;
        String lotType = "NONE";
        if (p.tracksExpiry()) {
            if (r.expiryDate() == null || r.expiryDate().isBefore(LocalDate.now())) {
                throw new IllegalArgumentException("A current or future expiry date is required for " + p.name());
            }
            if (p.medId() != null) {
                lotId = insertMedicineBatch(con, p.medId(), r);
                lotType = "MEDICINE_BATCH";
            } else {
                lotId = productBatchDAO.insert(con, p.id(), r.batchNumber(), r.quantity(),
                        r.expiryDate(), r.unitCost());
                lotType = "PRODUCT_BATCH";
            }
        } else if (r.expiryDate() != null) {
            throw new IllegalArgumentException(p.name() + " does not track expiry.");
        }

        applyDelta(con, p, r.quantity(), MovementType.PURCHASE_RECEIPT, lotType, lotId == 0 ? null : lotId,
                r.refType(), r.refId(), r.notes());

        if (p.medId() == null) { // retail cost follows latest receipt; medicine cost lives on the batch
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE products SET unit_cost = ? WHERE product_id = ?")) {
                ps.setDouble(1, r.unitCost());
                ps.setInt(2, p.id());
                ps.executeUpdate();
            }
        }
        if (p.narcotic()) {
            narcoticEntry(con, p, NarcoticRegisterEntry.TYPE_RECEIVED, r.quantity(), 0,
                    r.batchNumber(), null, "Stock received");
        }
        return lotId;
    }

    // ── Sales ledger entry (SaleDAO already deducted lot + products) ─────────

    public void recordSaleMovement(Connection con, int productId, int qty, String lotType,
                                   Integer lotId, int saleId) throws SQLException {
        insertMovement(con, productId, MovementType.SALE, lotType, lotId, -qty, readStock(con, productId),
                "SALE", saleId, "Sale #" + saleId);
    }

    // ── Void / return ────────────────────────────────────────────────────────

    /** Voids a completed sale and puts every unit back into the SAME lot it came from. */
    public void voidSale(Connection con, int saleId, String reason) throws SQLException {
        User user = SessionManager.getInstance().getCurrentUser();
        if (user == null || !user.isAdmin() && !user.isPharmacist()) {
            throw new SecurityException("Only a pharmacist or administrator can void a sale.");
        }
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT status FROM sales WHERE sale_id = ? FOR UPDATE")) {
            ps.setInt(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Sale not found: " + saleId);
                if ("VOIDED".equals(rs.getString(1))) throw new SQLException("Sale is already voided.");
            }
        }

        String itemSql = "SELECT product_id, batch_id, product_batch_id, quantity FROM sale_items WHERE sale_id = ?";
        try (PreparedStatement ps = con.prepareStatement(itemSql)) {
            ps.setInt(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int productId = rs.getInt("product_id");
                    Integer batchId = (Integer) rs.getObject("batch_id");
                    Integer productBatchId = (Integer) rs.getObject("product_batch_id");
                    int qty = rs.getInt("quantity");
                    ProductRow p = lock(con, productId);

                    boolean lotQuarantined = false;
                    String lotType = "NONE";
                    Integer lotId = null;
                    if (batchId != null) {
                        lotType = "MEDICINE_BATCH"; lotId = batchId;
                        lotQuarantined = restoreLot(con, "batches", batchId, qty);
                    } else if (productBatchId != null) {
                        lotType = "PRODUCT_BATCH"; lotId = productBatchId;
                        lotQuarantined = restoreLot(con, "product_batches", productBatchId, qty);
                    }
                    if (lotQuarantined) {
                        // units return to a quarantined lot: they are not sellable on-hand stock
                        insertMovement(con, productId, MovementType.VOID_RETURN, lotType, lotId, 0,
                                p.stock(), "SALE", saleId, "Returned into quarantined lot");
                    } else {
                        applyDelta(con, p, qty, MovementType.VOID_RETURN, lotType, lotId, "SALE", saleId, reason);
                    }
                    if (p.narcotic()) {
                        narcoticEntry(con, p, NarcoticRegisterEntry.TYPE_RETURNED, qty, 0, null, saleId,
                                "Sale voided: " + reason);
                    }
                }
            }
        }
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE sales SET status='VOIDED', void_reason=?, voided_by=?, voided_at=NOW() WHERE sale_id=?")) {
            ps.setString(1, reason);
            ps.setInt(2, user.getUserId());
            ps.setInt(3, saleId);
            ps.executeUpdate();
        }
        AuditLogger.logVoid(saleId, reason);
        reversePrescriptionAndLoyalty(con, saleId);
        // Claims are optional on installations that have not yet applied migration 004.
        try (ResultSet tables = con.getMetaData().getTables(con.getCatalog(), null, "insurance_claims", new String[]{"TABLE"})) {
            if (tables.next()) {
                try (PreparedStatement ps = con.prepareStatement("UPDATE insurance_claims SET status='CANCELLED' WHERE sale_id=?")) {
                    ps.setInt(1, saleId); ps.executeUpdate();
                }
            }
        }
    }

    private void reversePrescriptionAndLoyalty(Connection con, int saleId) throws SQLException {
        Integer rxId = null;
        try (PreparedStatement ps = con.prepareStatement("SELECT prescription_id FROM sales WHERE sale_id=?")) {
            ps.setInt(1, saleId);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) rxId = (Integer) rs.getObject(1); }
        }
        if (rxId != null) {
            String previousStatus;
            try (PreparedStatement ps = con.prepareStatement("SELECT status FROM prescriptions WHERE prescription_id=? FOR UPDATE")) {
                ps.setInt(1, rxId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new SQLException("Sale prescription not found.");
                    previousStatus = rs.getString(1);
                }
            }
            try (PreparedStatement ps = con.prepareStatement("UPDATE prescription_items pi JOIN "
                    + "(SELECT med_id,SUM(quantity) qty FROM sale_items WHERE sale_id=? GROUP BY med_id) si ON si.med_id=pi.med_id "
                    + "SET pi.qty_dispensed=GREATEST(0,pi.qty_dispensed-si.qty) WHERE pi.prescription_id=?")) {
                ps.setInt(1, saleId); ps.setInt(2, rxId); ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement("UPDATE prescriptions SET "
                    + "refills_used=GREATEST(0,refills_used-?), "
                    + "status=CASE WHEN status IN ('CANCELLED','EXPIRED') THEN status "
                    + "WHEN EXISTS(SELECT 1 FROM prescription_items WHERE prescription_id=? AND qty_dispensed>0) THEN 'PARTIAL' "
                    + "WHEN verified_by IS NOT NULL THEN 'VERIFIED' ELSE 'PENDING' END, updated_at=NOW() WHERE prescription_id=?")) {
                ps.setInt(1, "DISPENSED".equals(previousStatus) ? 1 : 0); ps.setInt(2, rxId); ps.setInt(3, rxId); ps.executeUpdate();
            }
        }
        try (PreparedStatement ps = con.prepareStatement("SELECT patient_id,SUM(points_earned-points_redeemed) delta "
                + "FROM loyalty_transactions WHERE sale_id=? GROUP BY patient_id")) {
            ps.setInt(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int patientId = rs.getInt("patient_id"), delta = rs.getInt("delta");
                    if (delta == 0) continue;
                    int balance;
                    try (PreparedStatement patient = con.prepareStatement("SELECT loyalty_points FROM patients WHERE patient_id=? FOR UPDATE")) {
                        patient.setInt(1, patientId);
                        try (ResultSet points = patient.executeQuery()) {
                            if (!points.next()) throw new SQLException("Loyalty patient not found.");
                            balance = points.getInt(1) - delta;
                        }
                    }
                    try (PreparedStatement update = con.prepareStatement("UPDATE patients SET loyalty_points=?, loyalty_tier=CASE WHEN ?>=5000 THEN 'GOLD' WHEN ?>=2000 THEN 'SILVER' ELSE 'BRONZE' END WHERE patient_id=?")) {
                        update.setInt(1, balance); update.setInt(2, balance); update.setInt(3, balance); update.setInt(4, patientId); update.executeUpdate();
                    }
                    try (PreparedStatement entry = con.prepareStatement("INSERT INTO loyalty_transactions(patient_id,sale_id,points_earned,points_redeemed,balance_after,notes) VALUES(?,?,?,?,?,?)")) {
                        entry.setInt(1, patientId); entry.setInt(2, saleId); entry.setInt(3, Math.max(0, -delta));
                        entry.setInt(4, Math.max(0, delta)); entry.setInt(5, balance); entry.setString(6, "Sale void reversal"); entry.executeUpdate();
                    }
                }
            }
        }
    }

    // ── Quarantine / expiry write-off ────────────────────────────────────────

    /** Removes a lot from sellable stock AND from products.stock_qty in one step. */
    public void quarantineLot(Connection con, String lotType, int lotId, String reason, int userId)
            throws SQLException {
        SessionManager.getInstance().requireRole("ADMIN","MAIN_ADMIN","PHARMACIST");
        if ((!"MEDICINE_BATCH".equals(lotType) && !"PRODUCT_BATCH".equals(lotType)) || reason==null || reason.isBlank())
            throw new IllegalArgumentException("Choose a valid lot and enter a quarantine reason.");
        boolean medicine = "MEDICINE_BATCH".equals(lotType);
        String sql = medicine
                ? "SELECT l.qty_remaining, l.is_quarantined, pr.product_id, l.batch_number FROM batches l "
                  + "JOIN products pr ON pr.legacy_med_id = l.med_id WHERE l.batch_id = ? FOR UPDATE"
                : "SELECT l.qty_remaining, l.is_quarantined, l.product_id, l.batch_number FROM product_batches l "
                  + "WHERE l.batch_id = ? FOR UPDATE";
        int qty, productId;
        String batchNumber;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, lotId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Lot not found: " + lotId);
                if (rs.getBoolean("is_quarantined")) return; // idempotent
                qty = rs.getInt("qty_remaining");
                productId = rs.getInt("product_id");
                batchNumber = rs.getString("batch_number");
            }
        }
        String table = medicine ? "batches" : "product_batches";
        try (PreparedStatement ps = con.prepareStatement("UPDATE " + table
                + " SET is_quarantined = 1, quarantine_reason = ?, quarantined_by = ?, quarantined_at = NOW()"
                + " WHERE batch_id = ?")) {
            ps.setString(1, reason);
            ps.setInt(2, userId);
            ps.setInt(3, lotId);
            ps.executeUpdate();
        }
        ProductRow p = lock(con, productId);
        if (qty > 0) {
            applyDelta(con, p, -qty, MovementType.QUARANTINE, lotType, lotId, "LOT", lotId, reason);
            if (p.narcotic()) {
                narcoticEntry(con, p, NarcoticRegisterEntry.TYPE_ADJUSTMENT, 0, qty, batchNumber, null,
                        "Quarantined: " + reason);
            }
        }
        AuditLogger.log("BATCH_QUARANTINE", table, lotId, "Quarantined " + qty + " unit(s). " + reason);
    }

    // ── Physical count (non-lot products only) ───────────────────────────────

    /**
     * Stock-take for products WITHOUT lots. Lot-tracked products must be corrected per lot,
     * otherwise products.stock_qty and the lots would diverge.
     */
    public void adjustToPhysicalCount(Connection con, int productId, int physicalQty, String reason)
            throws SQLException {
        SessionManager.getInstance().requireRole("ADMIN","MAIN_ADMIN","PHARMACIST");
        if(reason==null || reason.isBlank())throw new IllegalArgumentException("An adjustment reason is required.");
        if (physicalQty < 0) throw new IllegalArgumentException("Physical count cannot be negative.");
        ProductRow p = lock(con, productId);
        if (p.tracksExpiry()) {
            throw new IllegalStateException(p.name() + " is tracked by batch/expiry. Count and adjust each lot, "
                    + "or receive/quarantine lots, so lot totals stay equal to stock.");
        }
        int delta = physicalQty - p.stock();
        if (delta == 0) return;
        applyDelta(con, p, delta, MovementType.ADJUSTMENT, "NONE", null, "STOCK_TAKE", null, reason);
        if (p.narcotic()) {
            narcoticEntry(con, p, NarcoticRegisterEntry.TYPE_ADJUSTMENT, Math.max(delta, 0),
                    Math.max(-delta, 0), null, null, reason);
        }
    }

    // ── Reconciliation ───────────────────────────────────────────────────────

    public List<StockCheck> reconcile() throws SQLException {
        List<StockCheck> rows = new ArrayList<>();
        String sql = "SELECT product_id, name, category, tracks_expiry, product_qty, medicine_qty, lot_qty, status "
                + "FROM v_stock_reconciliation ORDER BY (status = 'OK'), name";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new StockCheck(rs.getInt("product_id"), rs.getString("name"), rs.getString("category"),
                        rs.getBoolean("tracks_expiry"), rs.getInt("product_qty"),
                        (Integer) rs.getObject("medicine_qty"), (Integer) rs.getObject("lot_qty"),
                        rs.getString("status")));
            }
        }
        return rows;
    }

    /** Records physical removal without silently returning quarantined units to sellable stock. */
    public void removeStock(Connection con, int productId, String lotType, Integer lotId, int quantity,
                            MovementType type, String reason, String witness) throws SQLException {
        SessionManager.getInstance().requireRole("ADMIN","MAIN_ADMIN","PHARMACIST");
        if(quantity<=0 || reason==null || reason.isBlank() || (type!=MovementType.DISPOSAL && type!=MovementType.SUPPLIER_RETURN))
            throw new IllegalArgumentException("Choose disposal or supplier return, a positive quantity and a reason.");
        ProductRow p=lock(con,productId);
        if(p.narcotic() && (witness==null || witness.isBlank()))throw new IllegalArgumentException("A witness name is required for controlled stock removal.");
        boolean quarantined=false;
        String batch=null;
        if(p.tracksExpiry()) {
            String expected=p.medId()!=null?"MEDICINE_BATCH":"PRODUCT_BATCH";
            if(!expected.equals(lotType) || lotId==null)throw new IllegalArgumentException("Select the product's batch.");
            String table=p.medId()!=null?"batches":"product_batches", key=p.medId()!=null?"med_id":"product_id";
            try(var ps=con.prepareStatement("SELECT qty_remaining,is_quarantined,batch_number FROM " + table + " WHERE batch_id=? AND " + key + "=? FOR UPDATE")) {
                ps.setInt(1,lotId);ps.setInt(2,p.medId()!=null?p.medId():productId);
                try(var rs=ps.executeQuery()) {
                    if(!rs.next() || rs.getInt("qty_remaining")<quantity)throw new SQLException("Insufficient stock in the selected batch.");
                    quarantined=rs.getBoolean("is_quarantined");batch=rs.getString("batch_number");
                }
            }
            try(var ps=con.prepareStatement("UPDATE " + table + " SET qty_remaining=qty_remaining-? WHERE batch_id=?")) {
                ps.setInt(1,quantity);ps.setInt(2,lotId);ps.executeUpdate();
            }
        } else if(lotId!=null || !"NONE".equals(lotType))throw new IllegalArgumentException("This product does not use batches.");
        String note=quantity + " physical unit(s) removed. " + reason + (witness==null || witness.isBlank()?"":" Witness: " + witness);
        if(quarantined)insertMovement(con,productId,type,lotType,lotId,0,p.stock(),"STOCK_REMOVAL",null,note);
        else applyDelta(con,p,-quantity,type,lotType,lotId,"STOCK_REMOVAL",null,note);
        if(p.narcotic()) {
            var user=SessionManager.getInstance().getCurrentUser();
            NarcoticRegisterEntry entry=new NarcoticRegisterEntry();
            entry.setMedId(p.medId());entry.setMedicineName(p.name());entry.setBatchNumber(batch);
            entry.setTransactionType(type==MovementType.DISPOSAL?NarcoticRegisterEntry.TYPE_DESTROYED:NarcoticRegisterEntry.TYPE_RETURNED);
            entry.setQtyOut(quarantined?0:quantity);entry.setWitnessName(witness);entry.setNotes(note);
            entry.setDispensedBy(user.getUserId());entry.setDispensedByName(user.getFullName());narcoticDAO.insert(con,entry);
        }
        AuditLogger.log(type.name(),"stock_movements",productId,note);
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private ProductRow lock(Connection con, int productId) throws SQLException {
        String sql = "SELECT p.product_id, p.name, p.tracks_expiry, p.stock_qty, p.legacy_med_id, "
                + "COALESCE(m.is_narcotic, 0) AS is_narcotic FROM products p "
                + "LEFT JOIN medicines m ON m.med_id = p.legacy_med_id WHERE p.product_id = ? FOR UPDATE";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Unknown product ID " + productId);
                int med = rs.getInt("legacy_med_id");
                boolean noMedicine = rs.wasNull();
                return new ProductRow(rs.getInt("product_id"), rs.getString("name"),
                        rs.getBoolean("tracks_expiry"), rs.getInt("stock_qty"),
                        noMedicine ? null : med, rs.getBoolean("is_narcotic"));
            }
        }
    }

    private void applyDelta(Connection con, ProductRow p, int delta, MovementType type, String lotType,
                            Integer lotId, String refType, Integer refId, String notes) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE products SET stock_qty = stock_qty + ? WHERE product_id = ? AND stock_qty + ? >= 0")) {
            ps.setInt(1, delta);
            ps.setInt(2, p.id());
            ps.setInt(3, delta);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Insufficient stock for " + p.name());
            }
        }
        insertMovement(con, p.id(), type, lotType, lotId, delta, readStock(con, p.id()), refType, refId, notes);
    }

    private int readStock(Connection con, int productId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT stock_qty FROM products WHERE product_id = ?")) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        }
    }

    private void insertMovement(Connection con, int productId, MovementType type, String lotType, Integer lotId,
                                int delta, int balance, String refType, Integer refId, String notes)
            throws SQLException {
        User u = SessionManager.getInstance().getCurrentUser();
        String sql = "INSERT INTO stock_movements (product_id, movement_type, lot_type, lot_id, qty_delta, "
                + "balance_after, ref_type, ref_id, user_id, notes) VALUES (?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setString(2, type.name());
            ps.setString(3, lotType == null ? "NONE" : lotType);
            ps.setObject(4, lotId, Types.INTEGER);
            ps.setInt(5, delta);
            ps.setInt(6, balance);
            ps.setString(7, refType);
            ps.setObject(8, refId, Types.INTEGER);
            ps.setObject(9, u == null ? null : u.getUserId(), Types.INTEGER);
            ps.setString(10, notes == null ? null : notes.length() > 255 ? notes.substring(0, 255) : notes);
            ps.executeUpdate();
        }
    }

    /** Puts quantity back into a lot. Returns true if that lot is quarantined meanwhile. */
    private boolean restoreLot(Connection con, String table, int lotId, int qty) throws SQLException {
        boolean quarantined;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COALESCE(is_quarantined,0) FROM " + table + " WHERE batch_id = ? FOR UPDATE")) {
            ps.setInt(1, lotId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Lot " + lotId + " no longer exists.");
                quarantined = rs.getBoolean(1);
            }
        }
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE " + table + " SET qty_remaining = qty_remaining + ? WHERE batch_id = ?")) {
            ps.setInt(1, qty);
            ps.setInt(2, lotId);
            ps.executeUpdate();
        }
        return quarantined;
    }

    private int insertMedicineBatch(Connection con, int medId, ReceiveRequest r) throws SQLException {
        Batch b = new Batch();
        b.setMedId(medId);
        b.setBatchNumber(r.batchNumber() == null || r.batchNumber().isBlank()
                ? "AUTO-" + System.currentTimeMillis() : r.batchNumber().trim());
        b.setQtyReceived(r.quantity());
        b.setQtyRemaining(r.quantity());
        b.setExpiryDate(r.expiryDate());
        b.setUnitCost(r.unitCost());
        b.setPackCost(r.packCost());
        return batchDAO.insert(con, b);
    }

    /** Keeps the legal narcotic register balance equal to real stock. */
    private void narcoticEntry(Connection con, ProductRow p, String type, int in, int out,
                               String batchNumber, Integer saleId, String notes) throws SQLException {
        User u = SessionManager.getInstance().getCurrentUser();
        if (u == null) throw new SecurityException("A signed-in user is required for narcotic stock changes.");
        NarcoticRegisterEntry e = new NarcoticRegisterEntry();
        e.setMedId(p.medId());
        e.setMedicineName(p.name());
        e.setBatchNumber(batchNumber);
        e.setTransactionType(type);
        e.setQtyIn(in);
        e.setQtyOut(out);
        e.setSaleId(saleId);
        e.setDispensedBy(u.getUserId());
        e.setDispensedByName(u.getFullName());
        e.setNotes(notes);
        narcoticDAO.insert(con, e);
    }
}
