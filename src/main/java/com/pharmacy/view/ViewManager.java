package com.pharmacy.view;

import com.pharmacy.util.SessionManager;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class ViewManager {

    private static final Logger LOG = Logger.getLogger(ViewManager.class.getName());
    private static volatile ViewManager instance;

    private BorderPane contentPane;   // The center panel of MainView's BorderPane
    private Stage      primaryStage;

    // Cache of already-built view nodes (key → JavaFX node supplier)
    private final Map<String, Supplier<javafx.scene.Node>> viewFactories = new HashMap<>();

    private ViewManager() {}

    public static ViewManager getInstance() {
        if (instance == null) {
            synchronized (ViewManager.class) {
                if (instance == null) instance = new ViewManager();
            }
        }
        return instance;
    }

    // ── Init ─────────────────────────────────────────────────

    public void initialize(BorderPane contentPane, Stage stage) {
        this.contentPane  = contentPane;
        this.primaryStage = stage;
        registerViewFactories();
    }

    private void registerViewFactories() {
        // Always rebuild — keeps state fresh
        viewFactories.put("dashboard",     () -> new DashboardView().getView());
        viewFactories.put("pos",           () -> new POSView().getView());
        viewFactories.put("inventory",     () -> new com.pharmacy.view.inventory.InventoryView().getView());viewFactories.put("addstock",      () -> new com.pharmacy.view.inventory.AddStockView().getView());
        viewFactories.put("expiry",        () -> new com.pharmacy.view.inventory.ExpiryAlertView().getView());
        viewFactories.put("reorder",       () -> new com.pharmacy.view.inventory.StockReorderView().getView());
        viewFactories.put("patients",      () -> new com.pharmacy.view.patient.PatientManagementView().getView());
        viewFactories.put("prescriptions", () -> new com.pharmacy.view.prescription.PrescriptionView().getView());
//        viewFactories.put("billing",       () -> new com.pharmacy.view.billing.BillingView().getView());
        viewFactories.put("insurance",     () -> new com.pharmacy.view.billing.InsuranceClaimView().getView());
        viewFactories.put("reports",       () -> new com.pharmacy.view.reports.ReportsDashboardView().getView());
        viewFactories.put("salesreport",   () -> new com.pharmacy.view.reports.SalesReportView().getView());
        viewFactories.put("profit",        () -> new com.pharmacy.view.reports.ProfitMarginView().getView());
        viewFactories.put("expiryreport",  () -> new com.pharmacy.view.reports.ExpiryReportView().getView());
        viewFactories.put("invoicehistory",() -> new com.pharmacy.view.reports.InvoiceHistoryView().getView());
        viewFactories.put("suppliers",     () -> new com.pharmacy.view.supplier.SupplierManagementView().getView());
        viewFactories.put("purchaseorders",() -> new com.pharmacy.view.supplier.PurchaseOrderView().getView());
        viewFactories.put("users",         () -> new com.pharmacy.view.admin.UserManagementView().getView());
        viewFactories.put("auditlog",      () -> new com.pharmacy.view.admin.AuditLogView().getView());
        viewFactories.put("backup",        () -> new com.pharmacy.view.admin.BackupRestoreView().getView());
        viewFactories.put("settings",      () -> new com.pharmacy.view.settings.SettingsView().getView());
    }

    // ── Navigate ─────────────────────────────────────────────

    /**
     * Navigate to a named view. Performs session and permission check first.
     */
    public void navigate(String viewKey) {
        if (contentPane == null) {
            LOG.warning("ViewManager not initialized — call initialize() first");
            return;
        }

        // Session guard
        if (SessionManager.getInstance().isSessionExpired()) {
            SessionManager.getInstance().clearSession();
            new LoginView().show(primaryStage);
            return;
        }
        SessionManager.getInstance().refreshActivity();

        Supplier<javafx.scene.Node> factory = viewFactories.get(viewKey.toLowerCase());
        if (factory == null) {
            LOG.warning("Unknown view key: " + viewKey);
            return;
        }

        try {
            javafx.scene.Node node = factory.get();
            contentPane.setCenter(node);
        } catch (Exception ex) {
            LOG.severe("Failed to load view '" + viewKey + "': " + ex.getMessage());
            showError("Could not load view: " + ex.getMessage());
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    public Stage getPrimaryStage() { return primaryStage; }

    private void showError(String message) {
        if (contentPane != null) {
            javafx.scene.control.Label err = new javafx.scene.control.Label("⚠ " + message);
            err.setStyle("-fx-text-fill: #DC2626; -fx-font-size: 14px; -fx-padding: 40;");
            contentPane.setCenter(err);
        }
    }
}