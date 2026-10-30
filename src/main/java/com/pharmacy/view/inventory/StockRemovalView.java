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
import java.util.ArrayList;
import java.util.List;

public class StockRemovalView {
    private record Lot(int id, String type, String description) { @Override public String toString(){return description;} }
    public Node getView() {
        if(!SessionManager.getInstance().isAdmin() && !SessionManager.getInstance().isPharmacist())return new Label("Access denied.");
        ComboBox<Product> products=new ComboBox<>();products.setMaxWidth(Double.MAX_VALUE);
        ComboBox<Lot> lots=new ComboBox<>();lots.setMaxWidth(Double.MAX_VALUE);
        ComboBox<StockService.MovementType> type=new ComboBox<>();type.getItems().setAll(StockService.MovementType.SUPPLIER_RETURN,StockService.MovementType.DISPOSAL);type.setValue(StockService.MovementType.SUPPLIER_RETURN);
        TextField qty=AppUIManager.styledField("Quantity to remove"),reason=AppUIManager.styledField("Reason / supplier return reference"),witness=AppUIManager.styledField("Witness name (required for controlled medicines)");
        Button save=AppUIManager.primaryButton("Record Stock Removal");save.setDisable(true);
        Label status=new Label("Loading products...");
        Runnable loadLots=() -> {
            Product product=products.getValue();lots.getItems().clear();save.setDisable(true);if(product==null)return;
            if(!product.isTracksExpiry()){save.setDisable(false);return;}
            UiTasks.run(() -> {
                List<Lot> result=new ArrayList<>();boolean medicine=product.isMedicine();
                String table=medicine?"batches":"product_batches",key=medicine?"med_id":"product_id";
                try(var con=DatabaseConnection.getInstance().getConnection();var ps=con.prepareStatement("SELECT batch_id,batch_number,expiry_date,qty_remaining,is_quarantined FROM " + table + " WHERE " + key + "=? AND qty_remaining>0 ORDER BY expiry_date")) {
                    ps.setInt(1,medicine?product.getMedicineId():product.getProductId());
                    try(var rs=ps.executeQuery()) {while(rs.next())result.add(new Lot(rs.getInt("batch_id"),medicine?"MEDICINE_BATCH":"PRODUCT_BATCH",
                            rs.getString("batch_number") + " | expiry " + rs.getDate("expiry_date") + " | qty " + rs.getInt("qty_remaining") + (rs.getBoolean("is_quarantined")?" | quarantined":"")));}
                }
                return result;
            }, loaded -> {if(products.getValue()==product){lots.getItems().setAll(loaded);save.setDisable(false);}},error -> status.setText(error.getMessage()));
        };
        products.setOnAction(e -> loadLots.run());
        UiTasks.run(() -> new ProductDAO().search(""),loaded -> {products.getItems().setAll(loaded);status.setText("Select a product, batch where applicable, quantity and reason.");},error -> status.setText(error.getMessage()));
        save.setOnAction(e -> {
            try {
                var product=products.getValue();var lot=lots.getValue();var movement=type.getValue();
                int quantity=Integer.parseInt(qty.getText().trim());String note=reason.getText().trim(), observer=witness.getText().trim();
                if(product==null || quantity<=0 || note.isBlank() || (product.isTracksExpiry() && lot==null))throw new IllegalArgumentException("Select a product and batch, and enter a positive quantity and reason.");
                if(!AlertUtil.showConfirm("Stock Removal","Remove " + quantity + " unit(s) of " + product.getName() + " as " + movement + "?"))return;
                save.setDisable(true);
                UiTasks.run(() -> {
                    try(var con=DatabaseConnection.getInstance().getConnection()) {
                        con.setAutoCommit(false);
                        try {new StockService().removeStock(con,product.getProductId(),lot==null?"NONE":lot.type(),lot==null?null:lot.id(),quantity,movement,note,observer);con.commit();}
                        catch(Exception ex){con.rollback();throw ex;}
                    }
                    return true;
                },saved -> {status.setText("Stock removal recorded.");qty.clear();loadLots.run();},error -> {save.setDisable(false);MessageDialog.showError("Removal failed",error.getMessage());});
            }catch(Exception ex){MessageDialog.showWarning("Invalid removal",ex.getMessage());}
        });
        VBox page=new VBox(16,AppUIManager.pageTitle("Supplier Returns & Disposal"),products,lots,type,qty,reason,witness,save,status);
        page.setPadding(new Insets(24));return page;
    }
}
