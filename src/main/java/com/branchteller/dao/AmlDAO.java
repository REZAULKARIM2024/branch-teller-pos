package com.branchteller.dao;

import com.branchteller.model.SuspiciousActivityFlag;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AmlDAO {

    public void insert(Connection conn, SuspiciousActivityFlag flag) throws SQLException {
        String sql = "INSERT INTO suspicious_activity_flags (account_id, txn_id, reason, amount) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, flag.getAccountId());
            if (flag.getTxnId() != null) ps.setInt(2, flag.getTxnId());
            else ps.setNull(2, Types.INTEGER);
            ps.setString(3, flag.getReason());
            ps.setBigDecimal(4, flag.getAmount());
            ps.executeUpdate();
        }
    }

    public List<SuspiciousActivityFlag> findUnreviewed(Connection conn) throws SQLException {
        String sql = "SELECT f.*, a.account_number FROM suspicious_activity_flags f " +
                "JOIN accounts a ON a.account_id = f.account_id " +
                "WHERE f.reviewed = FALSE ORDER BY f.flagged_at DESC";
        return query(conn, sql, ps -> {});
    }

    public List<SuspiciousActivityFlag> findAll(Connection conn, int limit) throws SQLException {
        String sql = "SELECT f.*, a.account_number FROM suspicious_activity_flags f " +
                "JOIN accounts a ON a.account_id = f.account_id " +
                "ORDER BY f.flagged_at DESC LIMIT ?";
        return query(conn, sql, ps -> ps.setInt(1, limit));
    }

    public void markReviewed(Connection conn, int flagId, int reviewerId) throws SQLException {
        String sql = "UPDATE suspicious_activity_flags SET reviewed = TRUE, reviewed_by = ?, review_date = NOW() WHERE flag_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, reviewerId);
            ps.setInt(2, flagId);
            ps.executeUpdate();
        }
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private List<SuspiciousActivityFlag> query(Connection conn, String sql, Binder binder) throws SQLException {
        List<SuspiciousActivityFlag> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(map(rs));
            }
        }
        return results;
    }

    private SuspiciousActivityFlag map(ResultSet rs) throws SQLException {
        SuspiciousActivityFlag f = new SuspiciousActivityFlag();
        f.setId(rs.getInt("flag_id"));
        f.setAccountId(rs.getInt("account_id"));
        int txnId = rs.getInt("txn_id");
        f.setTxnId(rs.wasNull() ? null : txnId);
        f.setReason(rs.getString("reason"));
        f.setAmount(rs.getBigDecimal("amount"));
        Timestamp flaggedAt = rs.getTimestamp("flagged_at");
        if (flaggedAt != null) f.setFlaggedAt(flaggedAt.toLocalDateTime());
        f.setReviewed(rs.getBoolean("reviewed"));
        int reviewedBy = rs.getInt("reviewed_by");
        f.setReviewedBy(rs.wasNull() ? null : reviewedBy);
        Timestamp reviewDate = rs.getTimestamp("review_date");
        if (reviewDate != null) f.setReviewDate(reviewDate.toLocalDateTime());
        f.setAccountNumber(rs.getString("account_number"));
        return f;
    }
}
