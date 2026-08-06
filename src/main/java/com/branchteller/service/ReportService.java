package com.branchteller.service;

import com.branchteller.config.DBConnection;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Simplified regulatory/branch reporting -- a daily summary of transaction volume by
 * type plus any AML flags raised that day, exported as CSV. A real regulatory filing
 * (e.g. a CTR) has a fixed government form format; this is a stand-in daily branch
 * report, not a compliance-grade export.
 */
public class ReportService {

    public static class DailySummaryLine {
        public final String txnType;
        public final int count;
        public final BigDecimal totalAmount;

        DailySummaryLine(String txnType, int count, BigDecimal totalAmount) {
            this.txnType = txnType;
            this.count = count;
            this.totalAmount = totalAmount;
        }
    }

    public List<DailySummaryLine> dailySummary(LocalDate date) throws SQLException {
        String sql = "SELECT txn_type, COUNT(*) AS cnt, SUM(amount) AS total FROM transactions " +
                "WHERE created_at >= ? AND created_at < ? GROUP BY txn_type ORDER BY txn_type";
        List<DailySummaryLine> results = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(date.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(date.plusDays(1).atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new DailySummaryLine(
                            rs.getString("txn_type"), rs.getInt("cnt"),
                            rs.getBigDecimal("total") == null ? BigDecimal.ZERO : rs.getBigDecimal("total")));
                }
            }
        }
        return results;
    }

    public int flagCountForDate(LocalDate date) throws SQLException {
        String sql = "SELECT COUNT(*) FROM suspicious_activity_flags WHERE flagged_at >= ? AND flagged_at < ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(date.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(date.plusDays(1).atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Writes a CSV daily report to the given path and returns it. */
    public String exportDailyReportCsv(LocalDate date, String filePath) throws SQLException, IOException {
        List<DailySummaryLine> summary = dailySummary(date);
        int flagCount = flagCountForDate(date);

        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write("NY Financial Bank - Daily Branch Report\n");
            writer.write("Date," + date + "\n\n");
            writer.write("Transaction Type,Count,Total Amount\n");
            BigDecimal grandTotal = BigDecimal.ZERO;
            int grandCount = 0;
            for (DailySummaryLine line : summary) {
                writer.write(String.format("%s,%d,%s%n", line.txnType, line.count, line.totalAmount));
                grandTotal = grandTotal.add(line.totalAmount);
                grandCount += line.count;
            }
            writer.write(String.format("TOTAL,%d,%s%n", grandCount, grandTotal));
            writer.write("\nAML flags raised," + flagCount + "\n");
        }
        return filePath;
    }
}
