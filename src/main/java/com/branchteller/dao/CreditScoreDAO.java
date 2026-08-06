package com.branchteller.dao;

import com.branchteller.model.CreditScoreHistory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CreditScoreDAO {

    public BigDecimal totalBalance(Connection conn, int customerId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(balance),0) FROM accounts WHERE customer_id = ? AND status = 'ACTIVE'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
            }
        }
    }

    /** Oldest account opened_date for the customer, or null if they have no accounts. */
    public LocalDate earliestAccountDate(Connection conn, int customerId) throws SQLException {
        String sql = "SELECT MIN(opened_date) FROM accounts WHERE customer_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Date d = rs.getDate(1);
                    return d == null ? null : d.toLocalDate();
                }
            }
        }
        return null;
    }

    /** On-time repayment ratio in [0,1] across all loans for this customer; 1.0 (neutral) if no repayments yet. */
    public double loanOnTimeRatio(Connection conn, int customerId) throws SQLException {
        String sql = "SELECT COUNT(*) AS total, " +
                "SUM(CASE WHEN r.status = 'PAID' AND r.paid_date <= r.due_date THEN 1 ELSE 0 END) AS on_time " +
                "FROM loan_repayments r JOIN loans l ON l.loan_id = r.loan_id " +
                "WHERE l.customer_id = ? AND r.status IN ('PAID','OVERDUE')";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int total = rs.getInt("total");
                    int onTime = rs.getInt("on_time");
                    if (total == 0) return 1.0;
                    return (double) onTime / total;
                }
            }
        }
        return 1.0;
    }

    public int amlFlagCount(Connection conn, int customerId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM suspicious_activity_flags f " +
                "JOIN accounts a ON a.account_id = f.account_id WHERE a.customer_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void insertHistory(Connection conn, int customerId, int score, String rating) throws SQLException {
        String sql = "INSERT INTO credit_score_history (customer_id, score, rating) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.setInt(2, score);
            ps.setString(3, rating);
            ps.executeUpdate();
        }
    }

    public List<CreditScoreHistory> findByCustomer(Connection conn, int customerId) throws SQLException {
        String sql = "SELECT h.*, c.full_name AS customer_name FROM credit_score_history h " +
                "JOIN customers c ON c.customer_id = h.customer_id WHERE h.customer_id = ? ORDER BY h.computed_at DESC";
        List<CreditScoreHistory> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(map(rs));
            }
        }
        return results;
    }

    public List<CreditScoreHistory> recentAll(Connection conn, int limit) throws SQLException {
        String sql = "SELECT h.*, c.full_name AS customer_name FROM credit_score_history h " +
                "JOIN customers c ON c.customer_id = h.customer_id ORDER BY h.computed_at DESC LIMIT ?";
        List<CreditScoreHistory> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(map(rs));
            }
        }
        return results;
    }

    private CreditScoreHistory map(ResultSet rs) throws SQLException {
        CreditScoreHistory h = new CreditScoreHistory();
        h.setId(rs.getInt("history_id"));
        h.setCustomerId(rs.getInt("customer_id"));
        h.setCustomerName(rs.getString("customer_name"));
        h.setScore(rs.getInt("score"));
        h.setRating(rs.getString("rating"));
        Timestamp computedAt = rs.getTimestamp("computed_at");
        if (computedAt != null) h.setComputedAt(computedAt.toLocalDateTime());
        return h;
    }
}
