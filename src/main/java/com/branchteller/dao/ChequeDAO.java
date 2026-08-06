package com.branchteller.dao;

import com.branchteller.model.Cheque;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ChequeDAO {

    public int insert(Connection conn, Cheque c) throws SQLException {
        String sql = "INSERT INTO cheques (account_id, cheque_no, amount, status, teller_id, deposit_date, note) " +
                "VALUES (?, ?, ?, 'PENDING', ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, c.getAccountId());
            ps.setString(2, c.getChequeNo());
            ps.setBigDecimal(3, c.getAmount());
            ps.setInt(4, c.getTellerId());
            ps.setDate(5, Date.valueOf(c.getDepositDate()));
            ps.setString(6, c.getNote());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    public void updateStatus(Connection conn, int chequeId, String status, java.time.LocalDate clearDate) throws SQLException {
        String sql = "UPDATE cheques SET status = ?, clear_date = ? WHERE cheque_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            if (clearDate != null) ps.setDate(2, Date.valueOf(clearDate));
            else ps.setNull(2, Types.DATE);
            ps.setInt(3, chequeId);
            ps.executeUpdate();
        }
    }

    public List<Cheque> findPending(Connection conn) throws SQLException {
        String sql = "SELECT ch.*, a.account_number FROM cheques ch " +
                "JOIN accounts a ON a.account_id = ch.account_id " +
                "WHERE ch.status = 'PENDING' ORDER BY ch.deposit_date ASC";
        List<Cheque> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) results.add(map(rs));
        }
        return results;
    }

    public java.util.Optional<Cheque> findById(Connection conn, int chequeId) throws SQLException {
        String sql = "SELECT ch.*, a.account_number FROM cheques ch " +
                "JOIN accounts a ON a.account_id = ch.account_id WHERE ch.cheque_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, chequeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return java.util.Optional.of(map(rs));
            }
        }
        return java.util.Optional.empty();
    }

    private Cheque map(ResultSet rs) throws SQLException {
        Cheque c = new Cheque();
        c.setId(rs.getInt("cheque_id"));
        c.setAccountId(rs.getInt("account_id"));
        c.setChequeNo(rs.getString("cheque_no"));
        c.setAmount(rs.getBigDecimal("amount"));
        c.setStatus(rs.getString("status"));
        c.setTellerId(rs.getInt("teller_id"));
        Date deposit = rs.getDate("deposit_date");
        if (deposit != null) c.setDepositDate(deposit.toLocalDate());
        Date clear = rs.getDate("clear_date");
        if (clear != null) c.setClearDate(clear.toLocalDate());
        c.setNote(rs.getString("note"));
        c.setAccountNumber(rs.getString("account_number"));
        return c;
    }
}
