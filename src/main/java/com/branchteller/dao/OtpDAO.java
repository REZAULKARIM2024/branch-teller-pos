package com.branchteller.dao;

import java.sql.*;
import java.time.LocalDateTime;

public class OtpDAO {

    public void insert(Connection conn, int userId, String otpCode, LocalDateTime expiresAt) throws SQLException {
        String sql = "INSERT INTO login_otps (user_id, otp_code, expires_at) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, otpCode);
            ps.setTimestamp(3, Timestamp.valueOf(expiresAt));
            ps.executeUpdate();
        }
    }

    /** Returns true and consumes the OTP if it matches an unused, unexpired code for this user. */
    public boolean verifyAndConsume(Connection conn, int userId, String otpCode) throws SQLException {
        String sql = "SELECT otp_id FROM login_otps WHERE user_id = ? AND otp_code = ? AND used = FALSE AND expires_at >= NOW() " +
                "ORDER BY otp_id DESC LIMIT 1";
        int otpId;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, otpCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                otpId = rs.getInt(1);
            }
        }
        try (PreparedStatement ps = conn.prepareStatement("UPDATE login_otps SET used = TRUE WHERE otp_id = ?")) {
            ps.setInt(1, otpId);
            ps.executeUpdate();
        }
        return true;
    }
}
