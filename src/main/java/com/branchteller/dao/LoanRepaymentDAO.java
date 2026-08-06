package com.branchteller.dao;

import com.branchteller.model.LoanRepayment;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LoanRepaymentDAO {

    public void insert(Connection conn, LoanRepayment r) throws SQLException {
        String sql = "INSERT INTO loan_repayments (loan_id, installment_no, due_date, amount_due, amount_paid, status) " +
                "VALUES (?, ?, ?, ?, 0.00, 'PENDING')";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, r.getLoanId());
            ps.setInt(2, r.getInstallmentNo());
            ps.setDate(3, Date.valueOf(r.getDueDate()));
            ps.setBigDecimal(4, r.getAmountDue());
            ps.executeUpdate();
        }
    }

    public List<LoanRepayment> findByLoanId(Connection conn, int loanId) throws SQLException {
        String sql = "SELECT * FROM loan_repayments WHERE loan_id = ? ORDER BY installment_no ASC";
        List<LoanRepayment> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, loanId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(map(rs));
            }
        }
        return results;
    }

    public java.util.Optional<LoanRepayment> findNextPending(Connection conn, int loanId) throws SQLException {
        String sql = "SELECT * FROM loan_repayments WHERE loan_id = ? AND status != 'PAID' " +
                "ORDER BY installment_no ASC LIMIT 1 FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, loanId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return java.util.Optional.of(map(rs));
            }
        }
        return java.util.Optional.empty();
    }

    public void recordPayment(Connection conn, int repaymentId, java.math.BigDecimal amountPaid, java.time.LocalDate paidDate) throws SQLException {
        String sql = "UPDATE loan_repayments SET amount_paid = amount_paid + ?, status = 'PAID', paid_date = ? WHERE repayment_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, amountPaid);
            ps.setDate(2, Date.valueOf(paidDate));
            ps.setInt(3, repaymentId);
            ps.executeUpdate();
        }
    }

    private LoanRepayment map(ResultSet rs) throws SQLException {
        LoanRepayment r = new LoanRepayment();
        r.setId(rs.getInt("repayment_id"));
        r.setLoanId(rs.getInt("loan_id"));
        r.setInstallmentNo(rs.getInt("installment_no"));
        Date due = rs.getDate("due_date");
        if (due != null) r.setDueDate(due.toLocalDate());
        r.setAmountDue(rs.getBigDecimal("amount_due"));
        r.setAmountPaid(rs.getBigDecimal("amount_paid"));
        r.setStatus(rs.getString("status"));
        Date paid = rs.getDate("paid_date");
        if (paid != null) r.setPaidDate(paid.toLocalDate());
        return r;
    }
}
