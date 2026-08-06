package com.branchteller.dao;

import com.branchteller.model.Loan;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LoanDAO {

    public int insert(Connection conn, Loan loan) throws SQLException {
        String sql = "INSERT INTO loans (customer_id, account_id, loan_type, principal, interest_rate, tenure_months, status, applied_date) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'APPLIED', ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, loan.getCustomerId());
            ps.setInt(2, loan.getAccountId());
            ps.setString(3, loan.getLoanType());
            ps.setBigDecimal(4, loan.getPrincipal());
            ps.setBigDecimal(5, loan.getInterestRate());
            ps.setInt(6, loan.getTenureMonths());
            ps.setDate(7, Date.valueOf(loan.getAppliedDate()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    public Optional<Loan> findById(Connection conn, int loanId) throws SQLException {
        String sql = "SELECT l.*, c.full_name AS customer_name, a.account_number FROM loans l " +
                "JOIN customers c ON c.customer_id = l.customer_id " +
                "JOIN accounts a ON a.account_id = l.account_id " +
                "WHERE l.loan_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, loanId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    public List<Loan> findByStatus(Connection conn, String status) throws SQLException {
        String sql = "SELECT l.*, c.full_name AS customer_name, a.account_number FROM loans l " +
                "JOIN customers c ON c.customer_id = l.customer_id " +
                "JOIN accounts a ON a.account_id = l.account_id " +
                "WHERE l.status = ? ORDER BY l.applied_date ASC";
        List<Loan> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(map(rs));
            }
        }
        return results;
    }

    public List<Loan> findAll(Connection conn) throws SQLException {
        String sql = "SELECT l.*, c.full_name AS customer_name, a.account_number FROM loans l " +
                "JOIN customers c ON c.customer_id = l.customer_id " +
                "JOIN accounts a ON a.account_id = l.account_id " +
                "ORDER BY l.applied_date DESC";
        List<Loan> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) results.add(map(rs));
        }
        return results;
    }

    public void updateStatus(Connection conn, int loanId, String status, Integer approvedBy) throws SQLException {
        String sql = "UPDATE loans SET status = ?, approved_by = COALESCE(?, approved_by) WHERE loan_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            if (approvedBy != null) ps.setInt(2, approvedBy);
            else ps.setNull(2, Types.INTEGER);
            ps.setInt(3, loanId);
            ps.executeUpdate();
        }
    }

    public void markDisbursed(Connection conn, int loanId, java.time.LocalDate disbursedDate) throws SQLException {
        String sql = "UPDATE loans SET status = 'DISBURSED', disbursed_date = ? WHERE loan_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(disbursedDate));
            ps.setInt(2, loanId);
            ps.executeUpdate();
        }
    }

    private Loan map(ResultSet rs) throws SQLException {
        Loan loan = new Loan();
        loan.setId(rs.getInt("loan_id"));
        loan.setCustomerId(rs.getInt("customer_id"));
        loan.setAccountId(rs.getInt("account_id"));
        loan.setLoanType(rs.getString("loan_type"));
        loan.setPrincipal(rs.getBigDecimal("principal"));
        loan.setInterestRate(rs.getBigDecimal("interest_rate"));
        loan.setTenureMonths(rs.getInt("tenure_months"));
        loan.setStatus(rs.getString("status"));
        Date applied = rs.getDate("applied_date");
        if (applied != null) loan.setAppliedDate(applied.toLocalDate());
        int approvedBy = rs.getInt("approved_by");
        loan.setApprovedBy(rs.wasNull() ? null : approvedBy);
        Date disbursed = rs.getDate("disbursed_date");
        if (disbursed != null) loan.setDisbursedDate(disbursed.toLocalDate());
        loan.setCustomerName(rs.getString("customer_name"));
        loan.setAccountNumber(rs.getString("account_number"));
        return loan;
    }
}
