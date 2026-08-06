package com.branchteller.dao;

import com.branchteller.model.RegulatoryReport;
import com.branchteller.model.ScreeningResult;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ComplianceDAO {

    public List<String[]> sanctionsList(Connection conn) throws SQLException {
        String sql = "SELECT entry_id, full_name, list_type FROM sanctions_list";
        List<String[]> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(new String[]{String.valueOf(rs.getInt(1)), rs.getString(2), rs.getString(3)});
            }
        }
        return results;
    }

    public int insertScreeningResult(Connection conn, ScreeningResult r) throws SQLException {
        String sql = "INSERT INTO screening_results (customer_id, matched_entry_id, match_score, status) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, r.getCustomerId());
            if (r.getMatchedEntryId() != null) ps.setInt(2, r.getMatchedEntryId()); else ps.setNull(2, Types.INTEGER);
            ps.setDouble(3, r.getMatchScore());
            ps.setString(4, r.getStatus());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    public List<ScreeningResult> allScreeningResults(Connection conn) throws SQLException {
        String sql = "SELECT sr.*, c.full_name AS customer_name, s.full_name AS matched_name " +
                "FROM screening_results sr JOIN customers c ON c.customer_id = sr.customer_id " +
                "LEFT JOIN sanctions_list s ON s.entry_id = sr.matched_entry_id " +
                "ORDER BY sr.screened_at DESC LIMIT 300";
        List<ScreeningResult> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ScreeningResult r = new ScreeningResult();
                r.setId(rs.getInt("result_id"));
                r.setCustomerId(rs.getInt("customer_id"));
                r.setCustomerName(rs.getString("customer_name"));
                int matchedId = rs.getInt("matched_entry_id");
                r.setMatchedEntryId(rs.wasNull() ? null : matchedId);
                r.setMatchedName(rs.getString("matched_name"));
                r.setMatchScore(rs.getDouble("match_score"));
                r.setStatus(rs.getString("status"));
                Timestamp screenedAt = rs.getTimestamp("screened_at");
                if (screenedAt != null) r.setScreenedAt(screenedAt.toLocalDateTime());
                results.add(r);
            }
        }
        return results;
    }

    public int insertReport(Connection conn, RegulatoryReport r) throws SQLException {
        String sql = "INSERT INTO regulatory_reports (report_type, reference_no, related_account_id, related_flag_id, filed_by, narrative) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, r.getReportType());
            ps.setString(2, r.getReferenceNo());
            if (r.getRelatedAccountId() != null) ps.setInt(3, r.getRelatedAccountId()); else ps.setNull(3, Types.INTEGER);
            if (r.getRelatedFlagId() != null) ps.setInt(4, r.getRelatedFlagId()); else ps.setNull(4, Types.INTEGER);
            ps.setInt(5, r.getFiledBy());
            ps.setString(6, r.getNarrative());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    public List<RegulatoryReport> allReports(Connection conn) throws SQLException {
        String sql = "SELECT r.*, a.account_number, u.full_name AS filed_by_name FROM regulatory_reports r " +
                "LEFT JOIN accounts a ON a.account_id = r.related_account_id " +
                "JOIN users u ON u.user_id = r.filed_by ORDER BY r.filed_at DESC";
        List<RegulatoryReport> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                RegulatoryReport r = new RegulatoryReport();
                r.setId(rs.getInt("report_id"));
                r.setReportType(rs.getString("report_type"));
                r.setReferenceNo(rs.getString("reference_no"));
                int accId = rs.getInt("related_account_id");
                r.setRelatedAccountId(rs.wasNull() ? null : accId);
                r.setRelatedAccountNumber(rs.getString("account_number"));
                int flagId = rs.getInt("related_flag_id");
                r.setRelatedFlagId(rs.wasNull() ? null : flagId);
                r.setFiledBy(rs.getInt("filed_by"));
                r.setFiledByName(rs.getString("filed_by_name"));
                Timestamp filedAt = rs.getTimestamp("filed_at");
                if (filedAt != null) r.setFiledAt(filedAt.toLocalDateTime());
                r.setNarrative(rs.getString("narrative"));
                results.add(r);
            }
        }
        return results;
    }
}
