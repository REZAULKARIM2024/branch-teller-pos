package com.branchteller.dao;

import com.branchteller.model.AccountHold;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class HoldDAO {

    public int insert(Connection conn, AccountHold h) throws SQLException {
        String sql = "INSERT INTO account_holds (account_id, amount, reason, placed_by) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, h.getAccountId());
            ps.setBigDecimal(2, h.getAmount());
            ps.setString(3, h.getReason());
            ps.setInt(4, h.getPlacedBy());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    public void release(Connection conn, int holdId, int releasedBy) throws SQLException {
        String sql = "UPDATE account_holds SET status = 'RELEASED', released_at = NOW(), released_by = ? WHERE hold_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, releasedBy);
            ps.setInt(2, holdId);
            ps.executeUpdate();
        }
    }

    public BigDecimal activeHoldsTotal(Connection conn, int accountId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount),0) FROM account_holds WHERE account_id = ? AND status = 'ACTIVE'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBigDecimal(1);
            }
        }
        return BigDecimal.ZERO;
    }

    public List<AccountHold> findActive(Connection conn) throws SQLException {
        String sql = "SELECT h.*, a.account_number FROM account_holds h " +
                "JOIN accounts a ON a.account_id = h.account_id WHERE h.status = 'ACTIVE' ORDER BY h.placed_at DESC";
        return query(conn, sql, ps -> {});
    }

    public List<AccountHold> findByAccount(Connection conn, int accountId) throws SQLException {
        String sql = "SELECT h.*, a.account_number FROM account_holds h " +
                "JOIN accounts a ON a.account_id = h.account_id WHERE h.account_id = ? ORDER BY h.placed_at DESC";
        return query(conn, sql, ps -> ps.setInt(1, accountId));
    }

    @FunctionalInterface
    private interface Binder { void bind(PreparedStatement ps) throws SQLException; }

    private List<AccountHold> query(Connection conn, String sql, Binder binder) throws SQLException {
        List<AccountHold> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(map(rs));
            }
        }
        return results;
    }

    private AccountHold map(ResultSet rs) throws SQLException {
        AccountHold h = new AccountHold();
        h.setId(rs.getInt("hold_id"));
        h.setAccountId(rs.getInt("account_id"));
        h.setAccountNumber(rs.getString("account_number"));
        h.setAmount(rs.getBigDecimal("amount"));
        h.setReason(rs.getString("reason"));
        h.setPlacedBy(rs.getInt("placed_by"));
        h.setStatus(rs.getString("status"));
        Timestamp placedAt = rs.getTimestamp("placed_at");
        if (placedAt != null) h.setPlacedAt(placedAt.toLocalDateTime());
        Timestamp releasedAt = rs.getTimestamp("released_at");
        if (releasedAt != null) h.setReleasedAt(releasedAt.toLocalDateTime());
        int releasedBy = rs.getInt("released_by");
        h.setReleasedBy(rs.wasNull() ? null : releasedBy);
        return h;
    }
}
