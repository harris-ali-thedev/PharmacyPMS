package com.pharmacy;

import com.pharmacy.dao.UserDAO;
import com.pharmacy.scheduler.PharmacyScheduler;
import com.pharmacy.util.DatabaseConnection;
import com.pharmacy.view.LoginView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.sql.Connection;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        try (Connection ignored = DatabaseConnection.getInstance().getConnection()) {
            new UserDAO().ensureBootstrapAdmin();
            try { new com.pharmacy.service.SettingsService().load(); }
            catch (java.sql.SQLException ex) {
                java.util.logging.Logger.getLogger(Main.class.getName()).warning("Settings could not be loaded; defaults apply. Apply migration 004. " + ex.getMessage());
            }
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Database Error");
            alert.setHeaderText("Cannot connect to the database.");
            alert.setContentText("Please check db.properties and ensure MySQL is running.\n\n" + e.getMessage());
            alert.showAndWait();
            Platform.exit();
            return;
        }

        PharmacyScheduler.getInstance().start();
        new LoginView().show(primaryStage);
    }

    @Override
    public void stop() {
        PharmacyScheduler.getInstance().stop();
        DatabaseConnection.getInstance().close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
