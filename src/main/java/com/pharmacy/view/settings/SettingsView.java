package com.pharmacy.view.settings;

import com.pharmacy.dao.UserDAO;
import com.pharmacy.service.SettingsService;
import com.pharmacy.util.SessionManager;
import com.pharmacy.util.UiTasks;
import com.pharmacy.view.AppUIManager;
import com.pharmacy.view.dialogs.MessageDialog;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

public class SettingsView {
    public Node getView() {
        VBox page=new VBox(16,AppUIManager.pageTitle("Settings")); page.setPadding(new Insets(24));
        if(SessionManager.getInstance().isAdmin()) {
            TextField name=AppUIManager.styledField("Store name"), timeout=AppUIManager.styledField("Session timeout in minutes");
            ComboBox<SettingsService.TaxMode> tax=new ComboBox<>(); tax.getItems().setAll(SettingsService.TaxMode.values());
            Button save=AppUIManager.primaryButton("Save Billing Settings"); save.setDisable(true);
            Label status=new Label("Loading saved settings...");
            UiTasks.run(() -> new SettingsService().load(), settings -> {
                name.setText(settings.storeName()); timeout.setText(Integer.toString(settings.timeoutMinutes()));
                tax.setValue(settings.taxMode()); save.setDisable(false); status.setText("Other workstations load changes when they restart.");
            }, error -> status.setText("Settings unavailable. Apply migration 004. " + error.getMessage()));
            save.setOnAction(e -> {
                try {
                    var settings=new SettingsService.Settings(name.getText().trim(),tax.getValue(),Integer.parseInt(timeout.getText().trim()));
                    save.setDisable(true);
                    UiTasks.run(() -> {new SettingsService().save(settings);return settings;}, saved -> {
                        save.setDisable(false); status.setText("Settings saved.");
                    }, error -> {save.setDisable(false);MessageDialog.showError("Save failed",error.getMessage());});
                } catch(Exception ex) {MessageDialog.showWarning("Invalid settings","Enter a valid timeout in minutes.");}
            });
            page.getChildren().addAll(new Label("Store name"),name,new Label("Tax mode"),tax,new Label("Session timeout (1–240 minutes)"),timeout,save,status,new Separator());
        }
        PasswordField old=new PasswordField(), password=new PasswordField(), confirm=new PasswordField();
        old.setPromptText("Current password");password.setPromptText("New password");confirm.setPromptText("Confirm new password");
        Button change=AppUIManager.primaryButton("Change My Password");
        change.setOnAction(e -> {
            try {
                SessionManager.getInstance().requireActiveSession();
                if(!password.getText().equals(confirm.getText()))throw new IllegalArgumentException("Passwords must match.");
                int id=SessionManager.getInstance().getCurrentUser().getUserId();
                String previous=old.getText(), next=password.getText();
                change.setDisable(true);
                UiTasks.run(() -> {new UserDAO().changePassword(id,previous,next);return true;}, saved -> {
                    old.clear();password.clear();confirm.clear();change.setDisable(false);MessageDialog.showSuccess("Password changed","Your password has been updated.");
                },error -> {change.setDisable(false);MessageDialog.showError("Password change failed",error.getMessage());});
            } catch(Exception ex) {MessageDialog.showWarning("Password change",ex.getMessage());}
        });
        page.getChildren().addAll(AppUIManager.sectionTitle("My Password"),old,password,confirm,change);
        ScrollPane scroll=new ScrollPane(page);scroll.setFitToWidth(true);return scroll;
    }
}
