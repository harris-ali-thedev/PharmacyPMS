package com.pharmacy.controller;

import com.pharmacy.dao.UserDAO;
import com.pharmacy.model.User;
import com.pharmacy.util.AuditLogger;
import com.pharmacy.util.SessionManager;
import com.pharmacy.view.MainView;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.util.Optional;

/**
 * Enhanced Login Controller.
 *
 * Fixes vs v1:
 *   - BCrypt verification is now active (was bypassed in v1 UserDAO)
 *   - Account lockout messaging shown to user
 *   - Failed login audit trail
 *   - Must-change-password redirect after login
 *   - IP address captured for audit
 *   - Session initialized properly after login
 */
public class LoginController {

    private final Stage         stage;
    private final TextField     usernameField;
    private final PasswordField passwordField;
    private final Label         errorLabel;
    private final UserDAO       userDAO = new UserDAO();

    public LoginController(Stage stage, TextField usernameField,
                           PasswordField passwordField, Label errorLabel) {
        this.stage = stage;
        this.usernameField = usernameField;
        this.passwordField = passwordField;
        this.errorLabel = errorLabel;
    }

    public void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Username and password are required.");
            return;
        }

        // Get caller IP — localhost for desktop app
        String ipAddress = "127.0.0.1";

        try {
            User user = userDAO.authenticate(username, password, ipAddress);

            if (user == null) {
                showError("Invalid username or password.");
                AuditLogger.logLogin(0, username, ipAddress, false);
                // Clear password field on failure
                passwordField.clear();
                return;
            }

            // Successful authentication
            SessionManager.getInstance().setCurrentUser(user);
            AuditLogger.logLogin(user.getUserId(), username, ipAddress, true);

            if (user.isMustChangePassword() && !promptPasswordChange(user.getUserId(), password)) {
                SessionManager.getInstance().clearSession();
                showError("Password change is required before continuing.");
                return;
            }

            MainView mainView = new MainView();
            mainView.show(stage);

        } catch (UserDAO.AccountLockedException e) {
            showError(e.getMessage());
            AuditLogger.logLogin(0, username, ipAddress, false);

        } catch (Exception e) {
            showError("Authentication error: " + e.getMessage());
        }
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
    }

    private boolean promptPasswordChange(int userId, String currentPassword) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Change Password");
        dialog.setHeaderText("You must set a new password before continuing.");

        ButtonType updateButton = new ButtonType("Update Password", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(updateButton, ButtonType.CANCEL);

        PasswordField newPassword = new PasswordField();
        newPassword.setPromptText("New password");
        PasswordField confirmPassword = new PasswordField();
        confirmPassword.setPromptText("Confirm new password");
        Label validation = new Label();
        validation.setStyle("-fx-text-fill: #B91C1C;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        grid.add(new Label("New password"), 0, 0);
        grid.add(newPassword, 1, 0);
        grid.add(new Label("Confirm password"), 0, 1);
        grid.add(confirmPassword, 1, 1);
        grid.add(validation, 1, 2);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button != updateButton) {
                return null;
            }
            String first = newPassword.getText();
            String second = confirmPassword.getText();
            if (!first.equals(second)) {
                validation.setText("Passwords do not match.");
                return "";
            }
            return first;
        });

        while (true) {
            Optional<String> result = dialog.showAndWait();
            if (result.isEmpty() || result.get() == null) {
                return false;
            }
            String newPlain = result.get();
            if (newPlain.isEmpty()) {
                continue;
            }
            try {
                userDAO.changePassword(userId, currentPassword, newPlain);
                return true;
            } catch (Exception e) {
                validation.setText(e.getMessage());
            }
        }
    }
}
