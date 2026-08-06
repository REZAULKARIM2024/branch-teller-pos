package com.branchteller.dao;

import com.branchteller.model.Account;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * All methods accept a Connection so BankingService can compose them inside a single
 * JDBC transaction (deposit/withdraw/transfer must be atomic).
 */
public class AccountDAO {

    public Optional<Account> findByAccountNumber(Connection conn, String accountNumber) throws SQLException {
        String sql = "SELECT a.*, c.full_name AS customer_name FROM accounts a " +
                "JOIN customers c ON c.customer_id = a.customer_id WHERE a.account_number = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    /** Locks the row for update — must be called inside an open transaction. */
    public Optional<Account> findByIdForUpdate(Connection conn, int accountId) throws SQLException {
        String sql = "SELECT a.*, c.full_name AS customer_name FROM accounts a " +
                "JOIN customers c ON c.customer_id = a.customer_id WHERE a.account_id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    /** All ACTIVE accounts of a given type (e.g. "SAVINGS") — used by the interest accrual job. */
    public List<Account> findActiveByType(Connection conn, String accountType) throws SQLException {
        String sql = "SELECT a.*, c.full_name AS customer_name FROM accounts a " +
                "JOIN customers c ON c.customer_id = a.customer_id " +
                "WHERE a.status = 'ACTIVE' AND a.account_type = ?";
        List<Account> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(map(rs));
            }
        }
        return results;
    }

    public void updateBalance(Connection conn, int accountId, java.math.BigDecimal newBalance) throws SQLException {
        String sql = "UPDATE accounts SET balance = ? WHERE account_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, newBalance);
            ps.setInt(2, accountId);
            ps.executeUpdate();
        }
    }

    public int create(Connection conn, Account a) throws SQLException {
        String sql = "INSERT INTO accounts (account_number, customer_id, branch_id, account_type, balance, interest_rate, opened_date) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, a.getAccountNumber());
            ps.setInt(2, a.getCustomerId());
            ps.setInt(3, a.getBranchId());
            ps.setString(4, a.getAccountType());
            ps.setBigDecimal(5, a.getBalance());
            ps.setBigDecimal(6, a.getInterestRate());
            ps.setDate(7, Date.valueOf(a.getOpenedDate()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    private Account map(ResultSet rs) throws SQLException {
        Account a = new Account();
        a.setId(rs.getInt("account_id"));
        a.setAccountNumber(rs.getString("account_number"));
        a.setCustomerId(rs.getInt("customer_id"));
        a.setBranchId(rs.getInt("branch_id"));
        a.setAccountType(rs.getString("account_type"));
        a.setBalance(rs.getBigDecimal("balance"));
        a.setInterestRate(rs.getBigDecimal("interest_rate"));
        a.setStatus(rs.getString("status"));
        Date opened = rs.getDate("opened_date");
        if (opened != null) a.setOpenedDate(opened.toLocalDate());
        a.setCustomerName(rs.getString("customer_name"));
        return a;
    }
}
