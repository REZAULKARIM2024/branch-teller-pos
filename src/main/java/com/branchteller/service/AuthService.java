package com.branchteller.service;

import com.branchteller.config.DBConnection;
import com.branchteller.dao.OtpDAO;
import com.branchteller.dao.UserDAO;
import com.branchteller.model.User;
import com.branchteller.util.PasswordUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Password auth plus a simulated OTP second factor and a simple lockout policy.
 * No real SMS/email gateway exists in this project, so the generated OTP is shown to the
 * user in a dialog (standing in for "delivered to their phone") -- see LoginFrame.
 */
public class AuthService {

    public static final int MAX_FAILED_ATTEMPTS = 5;

    private final UserDAO userDAO = new UserDAO();
    private final OtpDAO otpDAO = new OtpDAO();
    private final AuditService auditService = new AuditService();

    /** Step 1: verify username/password. Does not complete login if the user requires OTP. */
    public Optional<User> verifyPassword(String username, String password) throws SQLException, AccountLockedException {
        Optional<User> maybeUser = userDAO.findByUsername(username);
        if (maybeUser.isEmpty()) return Optional.empty();

        User user = maybeUser.get();
        if (user.getFailedLoginAttempts() >= MAX_FAILED_ATTEMPTS) {
            throw new AccountLockedException("Account locked after " + MAX_FAILED_ATTEMPTS +
                    " failed attempts. Contact an administrator to reset.");
        }

        boolean ok = PasswordUtil.verify(password, user.getSalt(), user.getPasswordHash());
        if (!ok) {
            userDAO.recordFailedLogin(username);
            return Optional.empty();
        }
        userDAO.resetFailedLogins(user.getId());
        return Optional.of(user);
    }

    /** Backward-compatible single-step login (no OTP) -- used only if OTP is disabled for the user. */
    public Optional<User> login(String username, String password) throws SQLException, AccountLockedException {
        return verifyPassword(username, password);
    }

    /** Step 2: generates and stores a 6-digit OTP, valid for 5 minutes. Returns the code so the
     *  caller can display it (simulated delivery -- no real SMS/email gateway is wired up). */
    public String issueOtp(User user) throws SQLException {
        String otp = PasswordUtil.generateOtp();
        try (Connection conn = DBConnection.getConnection()) {
            otpDAO.insert(conn, user.getId(), otp, LocalDateTime.now().plusMinutes(5));
        }
        return otp;
    }

    public boolean verifyOtp(User user, String code) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            boolean ok = otpDAO.verifyAndConsume(conn, user.getId(), code.trim());
            auditService.log(conn, user.getId(), ok ? "OTP_VERIFIED" : "OTP_FAILED", "user", user.getId(), null, null);
            return ok;
        }
    }

    /**
     * QA finding (fixed): the password UPDATE and its audit log entry used to run on two
     * separate connections -- {@code userDAO.changePassword(...)} opened and committed its own
     * connection internally, while the audit log used this method's own separate connection.
     * That meant they could never fail or roll back together: if the audit insert failed for any
     * reason (a transient DB error, a full disk, whatever), the password had *already* been
     * permanently changed with zero record of who changed it or when -- for a security-sensitive
     * action, that's exactly the kind of silent gap this codebase's other services (Compliance,
     * Credit Scoring, Payroll) were already fixed for. Now both writes share one connection and
     * one explicit transaction, matching that same established pattern.
     */
    public void changePassword(User user, String currentPassword, String newPassword)
            throws SQLException, WeakPasswordException, WrongPasswordException {
        if (!PasswordUtil.verify(currentPassword, user.getSalt(), user.getPasswordHash())) {
            throw new WrongPasswordException("Current password is incorrect");
        }
        String issue = PasswordUtil.checkComplexity(newPassword);
        if (issue != null) throw new WeakPasswordException(issue);

        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hash(newPassword, salt);
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                userDAO.changePassword(conn, user.getId(), hash, salt);
                auditService.log(conn, user.getId(), "PASSWORD_CHANGED", "user", user.getId(), null, null);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
        user.setPasswordHash(hash);
        user.setSalt(salt);
    }

    public static class AccountLockedException extends Exception {
        public AccountLockedException(String message) { super(message); }
    }

    public static class WeakPasswordException extends Exception {
        public WeakPasswordException(String message) { super(message); }
    }

    public static class WrongPasswordException extends Exception {
        public WrongPasswordException(String message) { super(message); }
    }
}
