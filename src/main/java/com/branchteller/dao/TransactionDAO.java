package com.branchteller.dao;

import com.branchteller.model.Transaction;

import java.sql.*;

public class TransactionDAO {

    public int insert(Connection conn, Transaction t) throws SQLException {
        String sql = "INSERT INTO transactions " +
                "(account_id, txn_type, amount, balance_after, teller_id, related_txn_id, channel, note) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, t.getAccountId());
            ps.setString(2, t.getTxnType());
            ps.setBigDecimal(3, t.getAmount());
            ps.setBigDecimal(4, t.getBalanceAfter());
            ps.setInt(5, t.getTellerId());
            if (t.getRelatedTxnId() != null) ps.setInt(6, t.getRelatedTxnId());
            else ps.setNull(6, Types.INTEGER);
            ps.setString(7, t.getChannel());
            ps.setString(8, t.getNote());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    public java.util.List<Transaction> findByAccountId(Connection conn, int accountId, int limit) throws SQLException {
        String sql = "SELECT * FROM transactions WHERE account_id = ? ORDER BY created_at DESC LIMIT ?";
        java.util.List<Transaction> results = new java.util.ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(map(rs));
            }
        }
        return results;
    }

    /** Statement generation: all transactions for an account within [from, to], oldest first. */
    public java.util.List<Transaction> findByAccountIdAndDateRange(
            Connection conn, int accountId, java.time.LocalDate from, java.time.LocalDate to) throws SQLException {
        String sql = "SELECT * FROM transactions WHERE account_id = ? " +
                "AND created_at >= ? AND created_at < ? ORDER BY created_at ASC";
        java.util.List<Transaction> results = new java.util.ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            ps.setTimestamp(2, Timestamp.valueOf(from.atStartOfDay()));
            ps.setTimestamp(3, Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(map(rs));
            }
        }
        return results;
    }

    private Transaction map(ResultSet rs) throws SQLException {
        Transaction t = new Transaction();
        t.setId(rs.getInt("txn_id"));
        t.setAccountId(rs.getInt("account_id"));
        t.setTxnType(rs.getString("txn_type"));
        t.setAmount(rs.getBigDecimal("amount"));
        t.setBalanceAfter(rs.getBigDecimal("balance_after"));
        t.setTellerId(rs.getInt("teller_id"));
        int related = rs.getInt("related_txn_id");
        t.setRelatedTxnId(rs.wasNull() ? null : related);
        t.setChannel(rs.getString("channel"));
        t.setNote(rs.getString("note"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) t.setCreatedAt(ts.toLocalDateTime());
        return t;
    }
}
