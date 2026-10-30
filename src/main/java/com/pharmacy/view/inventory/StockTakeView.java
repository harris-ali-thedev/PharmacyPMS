package com.pharmacy.view.inventory;

import com.pharmacy.dao.ProductDAO;
import com.pharmacy.model.Product;
import com.pharmacy.service.StockService;
import com.pharmacy.util.*;
import com.pharmacy.view.AppUIManager;
import com.pharmacy.view.dialogs.MessageDialog;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

/** Physical counts for products without batch tracking; lots remain managed individually. */
public class StockTakeView {
    public Node getView() {
        if (!SessionManager.getInstance().isAdmin() && !SessionManager.getInstance().isPharmacist())
            return new Label("Access denied. Pharmacists and administrators only.");
        ComboBox<Product> product = new ComboBox<>(); product.setMaxWidth(Double.MAX_VALUE);
        TextField count = AppUIManager.styledField("Physical quantity"), reason = AppUIManager.styledField("Reason for adjustment");
        Label status = new Label("Loading products...");
        Button save = AppUIManager.primaryButton("Record Physical Count"); save.setDisable(true);
        UiTasks.run(() -> new ProductDAO().search("").stream().filter(p -> !p.isTracksExpiry()).toList(),
                products -> { product.getItems().setAll(products); save.setDisable(false); status.setText("Select a product without batch tracking."); },
                error -> status.setText("Could not load products: " + error.getMessage()));
        save.setOnAction(e -> {
            try {
                SessionManager.getInstance().requireRole("ADMIN", "MAIN_ADMIN", "PHARMACIST");
                var selected=product.getValue(); if(selected==null)throw new IllegalArgumentException("Select a product.");
                int physical=Integer.parseInt(count.getText().trim());
                if(physical<0 || reason.getText().isBlank())throw new IllegalArgumentException("Enter a non-negative count and an adjustment reason.");
                String note=reason.getText().trim();
                if(!AlertUtil.showConfirm("Stock Take", "Set " + selected.getName() + " stock to " + physical + " units?"))return;
                save.setDisable(true);
                UiTasks.run(() -> {
                    try(var con=DatabaseConnection.getInstance().getConnection()) {
                        con.setAutoCommit(false);
                        try { new StockService().adjustToPhysicalCount(con,selected.getProductId(),physical,note); con.commit(); }
                        catch(Exception ex) { con.rollback(); throw ex; }
                    }
                    return physical;
                }, saved -> { save.setDisable(false); selected.setStockQty(saved); status.setText("Physical count recorded: " + saved); },
                        error -> { save.setDisable(false); MessageDialog.showError("Stock take failed",error.getMessage()); });
            } catch(Exception ex) { MessageDialog.showWarning("Invalid count",ex.getMessage()); }
        });
        VBox page=new VBox(16,AppUIManager.pageTitle("Stock Take"),new Label("Batch-tracked stock must be adjusted per lot through receiving and quarantine."),product,count,reason,save,status);
        page.setPadding(new Insets(24)); return page;
    }
}
