package com.pharmacy.util;

import com.pharmacy.model.User;

import java.time.LocalDateTime;
import java.util.logging.Logger;

/**
 * Enhanced SessionManager.
 *
 * Improvements over v1:
 *   - Inactivity timeout (configurable, default 15 min)
 *   - Activity refresh on every user action
 *   - isSessionExpired() check to enforce auto-logout
 *   - Stores shift session ID for the current cashier
 *   - Thread-safe singleton
 */
public class SessionManager {

    private static final Logger LOG = Logger.getLogger(SessionManager.class.getName());
    private static volatile SessionManager instance;

    // ── Session state ────────────────────────────────────────
    private User    currentUser;
    private Integer currentShiftSessionId;
    private String  sessionToken;          // Future: JWT token reference
    private LocalDateTime sessionStartedAt;
    private int     timeoutMinutes = AppConstants.SESSION_TIMEOUT_MINUTES;

    private SessionManager() {}

    /** Thread-safe double-checked locking singleton */
    public static SessionManager getInstance() {
        if (instance == null) {
            synchronized (SessionManager.class) {
                if (instance == null) {
                    instance = new SessionManager();
                }
            }
        }
        return instance;
    }

    // ── Session Lifecycle ────────────────────────────────────

    public void setCurrentUser(User user) {
        this.currentUser = user;
        this.sessionStartedAt = LocalDateTime.now();
        if (user != null) {
            user.refreshActivity();
            LOG.info("Session started for: " + user.getUsername() + " (" + user.getRole() + ")");
        }
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void clearSession() {
        if (currentUser != null) {
            LOG.info("Session ended for: " + currentUser.getUsername());
        }
        currentUser = null;
        currentShiftSessionId = null;
        sessionToken = null;
        sessionStartedAt = null;
    }

    // ── Activity Tracking ────────────────────────────────────

    /**
     * Must be called on every user interaction to reset the inactivity timer.
     * Call this from every controller action.
     */
    public void refreshActivity() {
        if (currentUser != null) {
            currentUser.refreshActivity();
        }
    }

    /**
     * Returns true if the session has expired due to inactivity.
     * The UI should call this before processing any action and redirect to login if true.
     */
    public boolean isSessionExpired() {
        if (currentUser == null) return true;
        return currentUser.isSessionExpired(timeoutMinutes);
    }

    /**
     * Call this at the start of every controller action.
     * Throws SecurityException if session is expired, refreshes activity otherwise.
     */
    public void requireActiveSession() {
        if (isSessionExpired()) {
            clearSession();
            throw new SecurityException("Your session has expired. Please log in again.");
        }
        refreshActivity();
    }

    // ── Authorization Helpers ────────────────────────────────

    public boolean isLoggedIn() {
        return currentUser != null && !isSessionExpired();
    }

    public boolean isAdmin() {
        return currentUser != null && currentUser.isAdmin();
    }

    public boolean isPharmacist() {
        return currentUser != null && currentUser.isPharmacist();
    }

    public boolean isCashier() {
        return currentUser != null && currentUser.isCashier();
    }

    /** Throws SecurityException if current user doesn't have the required role */
    public void requireRole(String... roles) {
        requireActiveSession();
        if (currentUser == null) throw new SecurityException("Not logged in");
        for (String role : roles) {
            if (role.equals(currentUser.getRole())) return;
        }
        throw new SecurityException("Access denied. Required role(s): "
                + String.join(", ", roles) + ". Your role: " + currentUser.getRole());
    }

    public void requireAdmin() {
        requireActiveSession();
        if (currentUser == null || !currentUser.isAdmin()) {
            throw new SecurityException("Administrator access required");
        }
    }

    public void requirePharmacistOrAdmin() {
        requireActiveSession();
        if (currentUser == null || (!currentUser.isPharmacist() && !currentUser.isAdmin())) {
            throw new SecurityException("Pharmacist or administrator access required");
        }
    }

    public void requireCanDispenseNarcotics() {
        requireActiveSession();
        if (currentUser == null || !currentUser.canDispenseNarcotics()) {
            throw new SecurityException(
                    "Only licensed pharmacists may dispense controlled substances");
        }
    }

    // ── Shift Session ────────────────────────────────────────

    public Integer getCurrentShiftSessionId() { return currentShiftSessionId; }
    public void setCurrentShiftSessionId(Integer id) { currentShiftSessionId = id; }
    public boolean hasOpenShift() { return currentShiftSessionId != null; }

    // ── Getters ──────────────────────────────────────────────

    public String getSessionToken() { return sessionToken; }
    public void setSessionToken(String token) { sessionToken = token; }

    public LocalDateTime getSessionStartedAt() { return sessionStartedAt; }

    public int getTimeoutMinutes() { return timeoutMinutes; }
    public void setTimeoutMinutes(int minutes) { timeoutMinutes = minutes; }
}
