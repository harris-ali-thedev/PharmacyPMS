package com.pharmacy;

import com.pharmacy.util.DatabaseConnection;
import com.pharmacy.view.LoginView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            // Verify database connection on startup
            DatabaseConnection.getInstance().getConnection();
        } catch (Exception e) {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle("Database Error");
            alert.setHeaderText("Cannot connect to the database.");
            alert.setContentText("Please check db.properties and ensure MySQL is running.\n\n" + e.getMessage());
            alert.showAndWait();
            
            Platform.exit();
            return;
        }

        LoginView loginView = new LoginView();
        loginView.show(primaryStage);
    }
    @Override
    public void stop() {
        DatabaseConnection.getInstance().closeConnection();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
