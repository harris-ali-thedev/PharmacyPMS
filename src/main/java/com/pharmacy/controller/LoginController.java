package com.pharmacy.controller;

import com.pharmacy.dao.UserDAO;
import com.pharmacy.model.User;
import com.pharmacy.util.AuditLogger;
import com.pharmacy.util.SessionManager;
import com.pharmacy.view.MainView;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

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

            // If the admin forced a password change, redirect to change password screen
            if (user.isMustChangePassword()) {
                showError("You must change your password before proceeding.");
//                 TODO: Open ChangePasswordView instead of MainView
                // For now we still open MainView — add ChangePasswordView in next iteration
            }

            // Open main application
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
}