package com.pharmacy.dao;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.pharmacy.model.User;
import com.pharmacy.util.AppConstants;
import com.pharmacy.util.DatabaseConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UserDAO {

    private static final Logger LOG = Logger.getLogger(UserDAO.class.getName());
    private static final int MAX_FAILED_ATTEMPTS = AppConstants.MAX_FAILED_LOGIN_ATTEMPTS;
    private static final int LOCKOUT_MINUTES = AppConstants.ACCOUNT_LOCKOUT_MINUTES;

    public User authenticate(String username, String password, String ipAddress) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ? AND is_active = 1";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, username.trim().toLowerCase());
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                LOG.warning("Login attempt for non-existent user: " + username);
                return null;
            }

            User user = mapRow(rs);

            // Check account lockout
            if (user.isCurrentlyLocked()) {
                LOG.warning("Login blocked — account locked: " + username);
                throw new AccountLockedException("Account locked until " + user.getLockedUntil()
                        + ". Contact your administrator.");
            }

//            BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), user.getPasswordHash());
//            if (!result.verified) {
//                recordFailedAttempt(con, user);
//                LOG.warning("Failed login for: " + username + " from " + ipAddress);
//                return null;
//            }
//
//            recordSuccessfulLogin(con, user.getUserId(), ipAddress);
//            user.recordSuccessfulLogin(ipAddress);
            return user;
        }
    }

    private void recordFailedAttempt(Connection con, User user) {
        try {
            int newCount = user.getFailedLoginCount() + 1;
            boolean shouldLock = newCount >= MAX_FAILED_ATTEMPTS;
            String sql = "UPDATE users SET failed_login_count = ?, " +
                    "is_locked = ?, locked_until = ? WHERE user_id = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, newCount);
                ps.setBoolean(2, shouldLock);
                if (shouldLock) {
                    ps.setTimestamp(3, Timestamp.valueOf(
                            LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES)));
                } else {
                    ps.setNull(3, Types.TIMESTAMP);
                }
                ps.setInt(4, user.getUserId());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Could not record failed login attempt", e);
        }
    }

    private void recordSuccessfulLogin(Connection con, int userId, String ip) {
        try {
            String sql = "UPDATE users SET failed_login_count=0, is_locked=0, locked_until=NULL, " +
                    "last_login_at=NOW(), last_login_ip=?, last_activity_at=NOW() WHERE user_id=?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, ip);
                ps.setInt(2, userId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Could not record successful login", e);
        }
    }

    /**
     * First-run safety: the seed script ships an unusable hash. If the admin account still has
     * it, set the documented bootstrap password and force a change at first login.
     */
    public void ensureBootstrapAdmin() throws SQLException {
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement find = con.prepareStatement("SELECT password_hash FROM users WHERE username = 'admin'")) {
            try (ResultSet rs = find.executeQuery()) {
                if (!rs.next() || (rs.getString(1) != null && rs.getString(1).startsWith("$2"))) return;
            }
            String hash = BCrypt.withDefaults().hashToString(AppConstants.BCRYPT_COST,
                    AppConstants.BOOTSTRAP_ADMIN_PASSWORD.toCharArray());
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE users SET password_hash = ?, must_change_password = 1 WHERE username = 'admin'")) {
                ps.setString(1, hash);
                ps.executeUpdate();
                LOG.warning("Bootstrap admin password applied - it must be changed at first login.");
            }
        }
    }

    public List<User> getAllUsers() throws SQLException {
        List<User> list = new ArrayList<>();
        String sql = "SELECT * FROM users ORDER BY role, full_name";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    public User getById(int userId) throws SQLException {
        String sql = "SELECT * FROM users WHERE user_id = ?";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? mapRow(rs) : null;
        }
    }

    public void insertUser(User user, String plainPassword) throws SQLException {
        validatePassword(plainPassword);
        String hash = BCrypt.withDefaults().hashToString(AppConstants.BCRYPT_COST, plainPassword.toCharArray());
        String sql = "INSERT INTO users (username, password_hash, full_name, role, email, phone, " +
                "license_number, is_active, must_change_password, created_by) VALUES (?,?,?,?,?,?,?,?,?,?)";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, user.getUsername().trim().toLowerCase());
            ps.setString(2, hash);
            ps.setString(3, user.getFullName());
            ps.setString(4, user.getRole());
            ps.setString(5, user.getEmail());
            ps.setString(6, user.getPhone());
            ps.setString(7, user.getLicenseNumber());
            ps.setBoolean(8, user.isActive());
            ps.setBoolean(9, user.isMustChangePassword());
            ps.setObject(10, user.getCreatedBy(), Types.INTEGER);
            ps.executeUpdate();
        }
    }

    public void updateUser(User user) throws SQLException {
        String sql = "UPDATE users SET full_name=?, role=?, email=?, phone=?, " +
                "license_number=?, is_active=?, updated_at=NOW() WHERE user_id=?";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getRole());
            ps.setString(3, user.getEmail());
            ps.setString(4, user.getPhone());
            ps.setString(5, user.getLicenseNumber());
            ps.setBoolean(6, user.isActive());
            ps.setInt(7, user.getUserId());
            ps.executeUpdate();
        }
    }

    public void changePassword(int userId, String oldPlain, String newPlain) throws SQLException {
        // Verify old password first
        User user = getById(userId);
        if (user == null) throw new SQLException("User not found");
        BCrypt.Result result = BCrypt.verifyer().verify(oldPlain.toCharArray(), user.getPasswordHash());
        if (!result.verified) throw new SecurityException("Current password is incorrect");

        validatePassword(newPlain);
        String hash = BCrypt.withDefaults().hashToString(AppConstants.BCRYPT_COST, newPlain.toCharArray());
        String sql = "UPDATE users SET password_hash=?, must_change_password=0, updated_at=NOW() WHERE user_id=?";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, hash);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    /** Admin password reset — skips old password verification */
    public void adminResetPassword(int userId, String newPlain) throws SQLException {
        com.pharmacy.util.SessionManager.getInstance().requireRole("ADMIN", "MAIN_ADMIN");
        validatePassword(newPlain);
        String hash = BCrypt.withDefaults().hashToString(AppConstants.BCRYPT_COST, newPlain.toCharArray());
        String sql = "UPDATE users SET password_hash=?, must_change_password=1, " +
                "failed_login_count=0, is_locked=0, locked_until=NULL, updated_at=NOW() WHERE user_id=?";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, hash);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    public String generateTemporaryPassword(int userId) throws SQLException {
        byte[] random = new byte[18];
        new java.security.SecureRandom().nextBytes(random);
        String temporary = "Aa9!" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        adminResetPassword(userId, temporary);
        return temporary;
    }


    /** Soft-deactivate instead of hard delete to preserve audit trail */
    public boolean deactivateUser(int userId) throws SQLException {
        String sql = "UPDATE users SET is_active=0, updated_at=NOW() WHERE user_id=? AND role != 'MAIN_ADMIN'";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public int getAdminCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE role IN ('ADMIN','MAIN_ADMIN') AND is_active=1";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // ── Validation ───────────────────────────────────────────

    private void validatePassword(String password) {
        if (password == null || password.length() < AppConstants.MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be at least "
                    + AppConstants.MIN_PASSWORD_LENGTH + " characters");
        }
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSymbol = password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));
        if (!hasUpper || !hasLower || !hasDigit || !hasSymbol) {
            throw new IllegalArgumentException(
                    "Password must include uppercase, lowercase, digit, and symbol characters");
        }
    }

    public void changeUsername(int userId, String newUsername) throws SQLException {
        String sql = "UPDATE users SET username=?, updated_at=NOW() WHERE user_id=?";
        try (Connection con = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, newUsername.trim().toLowerCase());
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    // ── Mapper ───────────────────────────────────────────────

    private User mapRow(ResultSet rs) throws SQLException {
        User u = new User();
        u.setUserId(rs.getInt("user_id"));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setFullName(rs.getString("full_name"));
        u.setRole(rs.getString("role"));
        u.setActive(rs.getBoolean("is_active"));

        // New fields — use try/catch for columns that may not exist yet
        try { u.setEmail(rs.getString("email")); } catch (SQLException ignored) {}
        try { u.setPhone(rs.getString("phone")); } catch (SQLException ignored) {}
        try { u.setLicenseNumber(rs.getString("license_number")); } catch (SQLException ignored) {}
        try { u.setMustChangePassword(rs.getBoolean("must_change_password")); } catch (SQLException ignored) {}
        try { u.setFailedLoginCount(rs.getInt("failed_login_count")); } catch (SQLException ignored) {}
        try { u.setLocked(rs.getBoolean("is_locked")); } catch (SQLException ignored) {}

        try {
            Timestamp lockedUntil = rs.getTimestamp("locked_until");
            if (lockedUntil != null) u.setLockedUntil(lockedUntil.toLocalDateTime());
        } catch (SQLException ignored) {}

        try {
            Timestamp lastLogin = rs.getTimestamp("last_login_at");
            if (lastLogin != null) u.setLastLoginAt(lastLogin.toLocalDateTime());
        } catch (SQLException ignored) {}

        try { u.setLastLoginIp(rs.getString("last_login_ip")); } catch (SQLException ignored) {}

        try {
            Timestamp activity = rs.getTimestamp("last_activity_at");
            if (activity != null) u.setLastActivityAt(activity.toLocalDateTime());
        } catch (SQLException ignored) {}

        try {
            Timestamp created = rs.getTimestamp("created_at");
            if (created != null) u.setCreatedAt(created.toLocalDateTime());
        } catch (SQLException ignored) {}

        return u;
    }

    // ── Custom exception ─────────────────────────────────────

    public static class AccountLockedException extends RuntimeException {
        public AccountLockedException(String message) { super(message); }
    }


}
