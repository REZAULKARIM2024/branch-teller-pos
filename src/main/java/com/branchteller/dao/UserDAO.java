package com.branchteller.dao;

import com.branchteller.config.DBConnection;
import com.branchteller.model.User;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserDAO {

    public Optional<User> findByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ? AND active = TRUE";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        }
        return Optional.empty();
    }

    /** QA finding (fixed): ComplaintService.assign() used to hand assigned_to straight to the
     *  UPDATE statement with no check that the user actually exists -- an unknown/typo'd staff
     *  ID only failed with a raw FK-violation SQLException instead of a clear message. Takes the
     *  caller's connection (unlike the methods above, which each open their own) so it can be
     *  called from inside another service's existing transaction. */
    public Optional<User> findById(Connection conn, int userId) throws SQLException {
        String sql = "SELECT * FROM users WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    public List<User> findAll() throws SQLException {
        String sql = "SELECT * FROM users WHERE active = TRUE ORDER BY full_name";
        List<User> results = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) results.add(map(rs));
        }
        return results;
    }

    public void recordFailedLogin(String username) throws SQLException {
        String sql = "UPDATE users SET failed_login_attempts = failed_login_attempts + 1 WHERE username = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.executeUpdate();
        }
    }

    public void resetFailedLogins(int userId) throws SQLException {
        String sql = "UPDATE users SET failed_login_attempts = 0 WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }

    /** Uses the caller's existing connection/transaction -- see AuthService#changePassword's
     *  javadoc for why this matters (it used to open its own separate connection here, so the
     *  password update and its audit log entry could never commit or roll back together). */
    public void changePassword(Connection conn, int userId, String newHash, String newSalt) throws SQLException {
        String sql = "UPDATE users SET password_hash = ?, salt = ?, password_last_changed = NOW() WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newHash);
            ps.setString(2, newSalt);
            ps.setInt(3, userId);
            ps.executeUpdate();
        }
    }

    private User map(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getInt("user_id"));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setSalt(rs.getString("salt"));
        u.setFullName(rs.getString("full_name"));
        u.setRole(rs.getString("role"));
        u.setBranchId(rs.getInt("branch_id"));
        u.setActive(rs.getBoolean("active"));
        try {
            BigDecimal limit = rs.getBigDecimal("approval_limit");
            if (limit != null) u.setApprovalLimit(limit);
            u.setOtpRequired(rs.getBoolean("otp_required"));
            u.setFailedLoginAttempts(rs.getInt("failed_login_attempts"));
        } catch (SQLException ignore) {
            // columns not present yet (pre-Phase-18 schema) -- keep model defaults
        }
        return u;
    }
}
