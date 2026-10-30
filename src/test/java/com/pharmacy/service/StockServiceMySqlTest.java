package com.pharmacy.service;

import com.pharmacy.dao.SaleDAO;
import com.pharmacy.model.Sale;
import com.pharmacy.model.SaleItem;
import com.pharmacy.model.User;
import com.pharmacy.util.AuditLogger;
import com.pharmacy.util.SessionManager;
import com.pharmacy.util.DatabaseConnection;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.MockedStatic;
import java.sql.*;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.*;

/** Opt-in only: run against a migrated MySQL database whose name ends in _test. */
@EnabledIfEnvironmentVariable(named="PHARMACY_TEST_DB_URL", matches="jdbc:mysql:.*")
class StockServiceMySqlTest {
    private Connection con;
    private MockedStatic<AuditLogger> audit;
    private MockedStatic<DatabaseConnection> database;
    private final StockService stock = new StockService();
    private int productId, userId;

    @BeforeEach void begin() throws Exception {
        String url = System.getenv("PHARMACY_TEST_DB_URL");
        String name = java.net.URI.create(url.substring(5)).getPath();
        if (name == null || !name.endsWith("_test")) throw new IllegalArgumentException("Test database name must end in _test");
        audit = mockStatic(AuditLogger.class); // Prevent audit writes through the application pool.
        con = DriverManager.getConnection(url, System.getenv("PHARMACY_TEST_DB_USER"), System.getenv("PHARMACY_TEST_DB_PASSWORD"));
        con.setAutoCommit(false);
        // Route DAO-owned connections to this transaction; DAO commits remain rollbackable fixtures.
        Connection shared=(Connection) java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
            if (method.getName().equals("close") || method.getName().equals("commit")) return null;
            try { return method.invoke(con,args); }
            catch (java.lang.reflect.InvocationTargetException ex) { throw ex.getCause(); }
        });
        DatabaseConnection manager=mock(DatabaseConnection.class);when(manager.getConnection()).thenReturn(shared);
        database=mockStatic(DatabaseConnection.class);database.when(DatabaseConnection::getInstance).thenReturn(manager);
        try (Statement statement = con.createStatement(); ResultSet rs = statement.executeQuery("SELECT user_id FROM users WHERE role IN ('ADMIN','MAIN_ADMIN') LIMIT 1")) {
            assertTrue(rs.next(), "Seed an administrator in the test database"); userId = rs.getInt(1);
        }
        User user = new User(); user.setUserId(userId); user.setRole("ADMIN");
        SessionManager.getInstance().setCurrentUser(user);
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO products(category_id,name,unit_price,tracks_expiry) SELECT category_id,'Regression test product',10,1 FROM product_categories WHERE code='GENERAL'", Statement.RETURN_GENERATED_KEYS)) {
            assertEquals(1, ps.executeUpdate());
            try (ResultSet rs = ps.getGeneratedKeys()) { assertTrue(rs.next()); productId = rs.getInt(1); }
        }
    }
    @AfterEach void rollback() throws Exception {
        try { if (con != null) { con.rollback(); con.close(); } }
        finally { SessionManager.getInstance().clearSession(); if (database != null) database.close(); if (audit != null) audit.close(); }
    }
    private int receive(int quantity) throws Exception {
        return stock.receive(con, new StockService.ReceiveRequest(productId, quantity, 5, 5,
                "TEST", LocalDate.now().plusMonths(6), "TEST", null, "Regression"));
    }
    private int quantity(String sql, int id) throws Exception {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1,id); try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); return rs.getInt(1); }
        }
    }
    private int onHand() throws Exception { return quantity("SELECT stock_qty FROM products WHERE product_id=?", productId); }
    private int sell(int quantity) throws Exception {
        Sale sale = new Sale(); sale.setUserId(userId); sale.setDiscountType("NONE"); sale.setPaymentMethod("CASH");
        sale.setSubtotal(quantity * 10); sale.setGrandTotal(quantity * 10); sale.setAmountPaid(quantity * 10);
        SaleItem item = new SaleItem(); item.setProductId(productId); item.setQuantity(quantity); item.setUnitPrice(10);
        sale.setItems(List.of(item));
        return new SaleDAO().saveSale(con, sale);
    }
    @Test void receiveKeepsLotLedgerAndProductInSync() throws Exception {
        int lot = receive(10);
        assertEquals(10, onHand());
        assertEquals(10, quantity("SELECT qty_remaining FROM product_batches WHERE batch_id=?", lot));
        assertEquals(10, quantity("SELECT SUM(qty_delta) FROM stock_movements WHERE product_id=?", productId));
    }
    @Test void saleAndVoidRestoreOriginalLotAndRejectRepeatedVoid() throws Exception {
        int lot = receive(10); int sale = sell(3);
        assertEquals(7,onHand());
        stock.voidSale(con,sale,"Regression void");
        assertEquals(10,onHand());
        assertEquals(10,quantity("SELECT qty_remaining FROM product_batches WHERE batch_id=?",lot));
        assertThrows(SQLException.class, () -> stock.voidSale(con,sale,"Duplicate"));
        assertEquals(10,onHand());
    }
    @Test void quarantineIsIdempotentAndVoidsCannotMakeQuarantinedStockSellable() throws Exception {
        int lot = receive(10); int sale = sell(3);
        stock.quarantineLot(con,"PRODUCT_BATCH",lot,"Test quarantine",userId);
        stock.quarantineLot(con,"PRODUCT_BATCH",lot,"Repeated",userId);
        assertEquals(0,onHand());
        stock.voidSale(con,sale,"Return into quarantine");
        assertEquals(0,onHand());
        assertEquals(10,quantity("SELECT qty_remaining FROM product_batches WHERE batch_id=?",lot));
    }
    @Test void failedSaleRollsBackWithoutChangingStock() throws Exception {
        receive(2);
        Savepoint point = con.setSavepoint();
        assertThrows(SQLException.class, () -> sell(3));
        con.rollback(point);
        assertEquals(2,onHand());
        assertEquals(0,quantity("SELECT COUNT(*) FROM sale_items WHERE product_id=?",productId));
    }
    @Test void disposalAndSupplierReturnKeepPhysicalAndSellableStockSeparate() throws Exception {
        int lot=receive(10);
        stock.removeStock(con,productId,"PRODUCT_BATCH",lot,2,StockService.MovementType.SUPPLIER_RETURN,"Supplier credit reference","Witness");
        assertEquals(8,onHand());
        stock.quarantineLot(con,"PRODUCT_BATCH",lot,"Quarantine",userId);
        stock.removeStock(con,productId,"PRODUCT_BATCH",lot,3,StockService.MovementType.DISPOSAL,"Destroyed","Witness");
        assertEquals(0,onHand());
        assertEquals(5,quantity("SELECT qty_remaining FROM product_batches WHERE batch_id=?",lot));
    }
    @Test void prescriptionLinesPartialFillsAndVoidAreTransactional() throws Exception {
        int medId, medicineProduct;
        try(var statement=con.createStatement();var rs=statement.executeQuery("SELECT p.product_id,m.med_id FROM products p JOIN medicines m ON m.med_id=p.legacy_med_id WHERE m.is_narcotic=0 LIMIT 1")) {
            assertTrue(rs.next());medicineProduct=rs.getInt(1);medId=rs.getInt(2);
        }
        stock.receive(con,new StockService.ReceiveRequest(medicineProduct,10,5,5,"RX-TEST",LocalDate.now().plusMonths(6),"TEST",null,"Prescription test"));
        var rx=new com.pharmacy.model.Prescription();rx.setPatientId(1);rx.setPatientName("Regression patient");rx.setDoctorName("Regression doctor");rx.setIssuedDate(LocalDate.now());
        var prescribed=new com.pharmacy.model.Prescription.PrescriptionItem();prescribed.setMedId(medId);prescribed.setMedicineName("Regression medicine");
        prescribed.setQtyPrescribed(10);prescribed.setDosage("1 tablet");prescribed.setFrequency("Daily");prescribed.setDuration("10 days");prescribed.setInstructions("After food");rx.setItems(List.of(prescribed));
        int rxId=new PrescriptionService().createPrescription(rx);
        var dao=new com.pharmacy.dao.PrescriptionDAO();var loaded=dao.getById(rxId);
        assertEquals(1,loaded.getItems().size());assertEquals("After food",loaded.getItems().get(0).getInstructions());
        int first=dispense(rxId,medId,medicineProduct,6);
        assertEquals("PARTIAL",dao.getById(rxId).getStatus());assertEquals(0,dao.getById(rxId).getRefillsUsed());
        int second=dispense(rxId,medId,medicineProduct,4);
        assertEquals("DISPENSED",dao.getById(rxId).getStatus());assertEquals(1,dao.getById(rxId).getRefillsUsed());
        stock.voidSale(con,second,"Undo last partial fill");
        assertEquals(6,dao.getById(rxId).getItems().get(0).getQtyDispensed());assertEquals("PARTIAL",dao.getById(rxId).getStatus());
        assertEquals(0,dao.getById(rxId).getRefillsUsed());
        stock.voidSale(con,first,"Undo first partial fill");
        assertEquals(0,dao.getById(rxId).getItems().get(0).getQtyDispensed());assertEquals("PENDING",dao.getById(rxId).getStatus());
    }
    private int dispense(int rxId,int medId,int medicineProduct,int count) throws Exception {
        Sale sale=new Sale();sale.setUserId(userId);sale.setPrescriptionId(rxId);sale.setDiscountType("NONE");sale.setPaymentMethod("CASH");sale.setSubtotal(count*10);sale.setGrandTotal(count*10);sale.setAmountPaid(count*10);
        SaleItem item=new SaleItem();item.setMedId(medId);item.setProductId(medicineProduct);item.setQuantity(count);item.setUnitPrice(10);sale.setItems(List.of(item));
        int id=new SaleDAO().saveSale(con,sale);sale.setSaleId(id);
        new PrescriptionService().recordDispensing(con,sale,new com.pharmacy.dao.PrescriptionDAO().getById(rxId),List.of(item));
        return id;
    }
    @Test void billingSettingsPersistAndUpdateSessionTimeout() throws Exception {
        var service=new SettingsService();
        service.save(new SettingsService.Settings("Regression Pharmacy",SettingsService.TaxMode.TAX_EXCLUSIVE,30));
        var loaded=service.load();
        assertEquals("Regression Pharmacy",loaded.storeName());
        assertEquals(SettingsService.TaxMode.TAX_EXCLUSIVE,loaded.taxMode());
        assertEquals(30,loaded.timeoutMinutes());
    }
    @Test void claimsUseStoredInvoiceTotalAndCancelOnVoid() throws Exception {
        receive(10);int sale=sell(3);
        var claim=new InsuranceClaimService().record(sale,"TEST-POLICY","Test Insurer",new java.math.BigDecimal("80"));
        assertEquals(new java.math.BigDecimal("30.00"),claim.invoiceTotal());
        assertEquals(new java.math.BigDecimal("24.00"),claim.covered());
        assertEquals(new java.math.BigDecimal("6.00"),claim.copay());
        stock.voidSale(con,sale,"Cancel claim and sale");
        try(var ps=con.prepareStatement("SELECT status FROM insurance_claims WHERE claim_id=?")) {
            ps.setInt(1,claim.id());try(var rs=ps.executeQuery()){assertTrue(rs.next());assertEquals("CANCELLED",rs.getString(1));}
        }
    }
    @Test void voidReversesRecordedLoyaltyWithoutDeletingHistory() throws Exception {
        receive(10);int sale=sell(1);
        try(var statement=con.createStatement()) {
            statement.executeUpdate("UPDATE patients SET loyalty_points=110 WHERE patient_id=1");
            statement.executeUpdate("INSERT INTO loyalty_transactions(patient_id,sale_id,points_earned,balance_after,notes) VALUES(1," + sale + ",10,110,'Test earning')");
        }
        stock.voidSale(con,sale,"Undo loyalty earning");
        assertEquals(100,quantity("SELECT loyalty_points FROM patients WHERE patient_id=?",1));
        assertEquals(2,quantity("SELECT COUNT(*) FROM loyalty_transactions WHERE sale_id=?",sale));
        assertEquals(0,quantity("SELECT SUM(points_earned-points_redeemed) FROM loyalty_transactions WHERE sale_id=?",sale));
    }
}
