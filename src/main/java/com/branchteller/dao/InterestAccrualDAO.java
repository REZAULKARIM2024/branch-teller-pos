package com.branchteller.dao;

import com.branchteller.model.InterestAccrual;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class InterestAccrualDAO {

    public boolean existsForPeriod(Connection conn, int accountId, String period) throws SQLException {
        String sql = "SELECT 1 FROM interest_accruals WHERE account_id = ? AND period = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            ps.setString(2, period);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void insert(Connection conn, InterestAccrual accrual) throws SQLException {
        String sql = "INSERT INTO interest_accruals (account_id, period, rate_applied, amount, posted_date) " +
                "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accrual.getAccountId());
            ps.setString(2, accrual.getPeriod());
            ps.setBigDecimal(3, accrual.getRateApplied());
            ps.setBigDecimal(4, accrual.getAmount());
            ps.setDate(5, Date.valueOf(accrual.getPostedDate()));
            ps.executeUpdate();
        }
    }

    /** All accruals ever posted for a given account, oldest period first -- used for interest certificates. */
    public List<InterestAccrual> findByAccountId(Connection conn, int accountId) throws SQLException {
        String sql = "SELECT ia.*, a.account_number FROM interest_accruals ia " +
                "JOIN accounts a ON a.account_id = ia.account_id " +
                "WHERE ia.account_id = ? ORDER BY ia.period ASC";
        List<InterestAccrual> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(map(rs));
            }
        }
        return results;
    }

    public List<InterestAccrual> findByPeriod(Connection conn, String period) throws SQLException {
        String sql = "SELECT ia.*, a.account_number FROM interest_accruals ia " +
                "JOIN accounts a ON a.account_id = ia.account_id " +
                "WHERE ia.period = ? ORDER BY a.account_number ASC";
        List<InterestAccrual> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, period);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(map(rs));
            }
        }
        return results;
    }

    private InterestAccrual map(ResultSet rs) throws SQLException {
        InterestAccrual accrual = new InterestAccrual();
        accrual.setId(rs.getInt("accrual_id"));
        accrual.setAccountId(rs.getInt("account_id"));
        accrual.setPeriod(rs.getString("period"));
        accrual.setRateApplied(rs.getBigDecimal("rate_applied"));
        accrual.setAmount(rs.getBigDecimal("amount"));
        Date posted = rs.getDate("posted_date");
        if (posted != null) accrual.setPostedDate(posted.toLocalDate());
        accrual.setAccountNumber(rs.getString("account_number"));
        return accrual;
    }
}
