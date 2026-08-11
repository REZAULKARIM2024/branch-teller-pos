package com.branchteller.dao;

import com.branchteller.model.Card;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CardDAO {

    public int insert(Connection conn, Card c) throws SQLException {
        String sql = "INSERT INTO cards (account_id, card_number, card_type, cardholder_name, expiry_date, credit_limit, daily_limit, issued_date) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, c.getAccountId());
            ps.setString(2, c.getCardNumber());
            ps.setString(3, c.getCardType());
            ps.setString(4, c.getCardholderName());
            ps.setDate(5, Date.valueOf(c.getExpiryDate()));
            if (c.getCreditLimit() != null) ps.setBigDecimal(6, c.getCreditLimit()); else ps.setNull(6, Types.DECIMAL);
            ps.setBigDecimal(7, c.getDailyLimit());
            ps.setDate(8, Date.valueOf(c.getIssuedDate()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    public void updateStatus(Connection conn, int cardId, String status) throws SQLException {
        String sql = "UPDATE cards SET status = ? WHERE card_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, cardId);
            ps.executeUpdate();
        }
    }

    public void updateLimits(Connection conn, int cardId, BigDecimal dailyLimit, BigDecimal creditLimit) throws SQLException {
        String sql = "UPDATE cards SET daily_limit = ?, credit_limit = ? WHERE card_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, dailyLimit);
            if (creditLimit != null) ps.setBigDecimal(2, creditLimit); else ps.setNull(2, Types.DECIMAL);
            ps.setInt(3, cardId);
            ps.executeUpdate();
        }
    }

    public java.util.Optional<Card> findById(Connection conn, int cardId) throws SQLException {
        String sql = "SELECT c.*, a.account_number FROM cards c JOIN accounts a ON a.account_id = c.account_id WHERE c.card_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cardId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return java.util.Optional.of(map(rs));
            }
        }
        return java.util.Optional.empty();
    }

    public List<Card> findAll(Connection conn) throws SQLException {
        String sql = "SELECT c.*, a.account_number FROM cards c JOIN accounts a ON a.account_id = c.account_id ORDER BY c.card_id DESC";
        List<Card> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) results.add(map(rs));
        }
        return results;
    }

    public List<Card> findByAccount(Connection conn, int accountId) throws SQLException {
        String sql = "SELECT c.*, a.account_number FROM cards c JOIN accounts a ON a.account_id = c.account_id WHERE c.account_id = ?";
        List<Card> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(map(rs));
            }
        }
        return results;
    }

    private Card map(ResultSet rs) throws SQLException {
        Card c = new Card();
        c.setId(rs.getInt("card_id"));
        c.setAccountId(rs.getInt("account_id"));
        c.setAccountNumber(rs.getString("account_number"));
        c.setCardNumber(rs.getString("card_number"));
        c.setCardType(rs.getString("card_type"));
        c.setCardholderName(rs.getString("cardholder_name"));
        Date expiry = rs.getDate("expiry_date");
        if (expiry != null) c.setExpiryDate(expiry.toLocalDate());
        c.setCreditLimit(rs.getBigDecimal("credit_limit"));
        c.setDailyLimit(rs.getBigDecimal("daily_limit"));
        c.setStatus(rs.getString("status"));
        Date issued = rs.getDate("issued_date");
        if (issued != null) c.setIssuedDate(issued.toLocalDate());
        return c;
    }
}
