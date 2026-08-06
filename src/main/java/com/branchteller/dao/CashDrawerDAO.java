package com.branchteller.dao;

import com.branchteller.model.CashDrawerLog;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CashDrawerDAO {

    public int insert(Connection conn, CashDrawerLog log) throws SQLException {
        String sql = "INSERT INTO cash_drawer_logs (teller_id, branch_id, action, amount, note) " +
                "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, log.getTellerId());
            ps.setInt(2, log.getBranchId());
            ps.setString(3, log.getAction());
            ps.setBigDecimal(4, log.getAmount());
            ps.setString(5, log.getNote());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    public List<CashDrawerLog> findRecentByTeller(Connection conn, int tellerId, int limit) throws SQLException {
        String sql = "SELECT * FROM cash_drawer_logs WHERE teller_id = ? ORDER BY created_at DESC LIMIT ?";
        List<CashDrawerLog> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tellerId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(map(rs));
            }
        }
        return results;
    }

    private CashDrawerLog map(ResultSet rs) throws SQLException {
        CashDrawerLog log = new CashDrawerLog();
        log.setId(rs.getInt("log_id"));
        log.setTellerId(rs.getInt("teller_id"));
        log.setBranchId(rs.getInt("branch_id"));
        log.setAction(rs.getString("action"));
        log.setAmount(rs.getBigDecimal("amount"));
        log.setNote(rs.getString("note"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) log.setCreatedAt(ts.toLocalDateTime());
        return log;
    }
}
