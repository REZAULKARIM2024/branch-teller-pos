package com.branchteller.dao;

import com.branchteller.model.BillPayment;
import com.branchteller.model.Biller;
import com.branchteller.model.ExternalTransfer;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PaymentsDAO {

    public int insertExternalTransfer(Connection conn, ExternalTransfer t) throws SQLException {
        String sql = "INSERT INTO external_transfers (account_id, transfer_type, beneficiary_name, beneficiary_bank, " +
                "beneficiary_account, routing_swift, amount, status, reference_no, initiated_by, completed_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, t.getAccountId());
            ps.setString(2, t.getTransferType());
            ps.setString(3, t.getBeneficiaryName());
            ps.setString(4, t.getBeneficiaryBank());
            ps.setString(5, t.getBeneficiaryAccount());
            ps.setString(6, t.getRoutingSwift());
            ps.setBigDecimal(7, t.getAmount());
            ps.setString(8, t.getStatus());
            ps.setString(9, t.getReferenceNo());
            ps.setInt(10, t.getInitiatedBy());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    public List<ExternalTransfer> findExternalTransfers(Connection conn, int limit) throws SQLException {
        String sql = "SELECT t.*, a.account_number FROM external_transfers t " +
                "JOIN accounts a ON a.account_id = t.account_id ORDER BY t.created_at DESC LIMIT ?";
        List<ExternalTransfer> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ExternalTransfer t = new ExternalTransfer();
                    t.setId(rs.getInt("ext_transfer_id"));
                    t.setAccountId(rs.getInt("account_id"));
                    t.setAccountNumber(rs.getString("account_number"));
                    t.setTransferType(rs.getString("transfer_type"));
                    t.setBeneficiaryName(rs.getString("beneficiary_name"));
                    t.setBeneficiaryBank(rs.getString("beneficiary_bank"));
                    t.setBeneficiaryAccount(rs.getString("beneficiary_account"));
                    t.setRoutingSwift(rs.getString("routing_swift"));
                    t.setAmount(rs.getBigDecimal("amount"));
                    t.setStatus(rs.getString("status"));
                    t.setReferenceNo(rs.getString("reference_no"));
                    t.setInitiatedBy(rs.getInt("initiated_by"));
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    if (createdAt != null) t.setCreatedAt(createdAt.toLocalDateTime());
                    results.add(t);
                }
            }
        }
        return results;
    }

    public List<Biller> findBillers(Connection conn) throws SQLException {
        String sql = "SELECT biller_id, name, category FROM billers ORDER BY category, name";
        List<Biller> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Biller b = new Biller();
                b.setId(rs.getInt(1));
                b.setName(rs.getString(2));
                b.setCategory(rs.getString(3));
                results.add(b);
            }
        }
        return results;
    }

    public int insertBillPayment(Connection conn, BillPayment p) throws SQLException {
        String sql = "INSERT INTO bill_payments (account_id, biller_id, reference_no, amount, status, paid_by) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, p.getAccountId());
            ps.setInt(2, p.getBillerId());
            ps.setString(3, p.getReferenceNo());
            ps.setBigDecimal(4, p.getAmount());
            ps.setString(5, p.getStatus());
            ps.setInt(6, p.getPaidBy());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    public List<BillPayment> findBillPayments(Connection conn, int limit) throws SQLException {
        String sql = "SELECT p.*, a.account_number, b.name AS biller_name FROM bill_payments p " +
                "JOIN accounts a ON a.account_id = p.account_id JOIN billers b ON b.biller_id = p.biller_id " +
                "ORDER BY p.created_at DESC LIMIT ?";
        List<BillPayment> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BillPayment p = new BillPayment();
                    p.setId(rs.getInt("payment_id"));
                    p.setAccountId(rs.getInt("account_id"));
                    p.setAccountNumber(rs.getString("account_number"));
                    p.setBillerId(rs.getInt("biller_id"));
                    p.setBillerName(rs.getString("biller_name"));
                    p.setReferenceNo(rs.getString("reference_no"));
                    p.setAmount(rs.getBigDecimal("amount"));
                    p.setStatus(rs.getString("status"));
                    p.setPaidBy(rs.getInt("paid_by"));
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    if (createdAt != null) p.setCreatedAt(createdAt.toLocalDateTime());
                    results.add(p);
                }
            }
        }
        return results;
    }
}
