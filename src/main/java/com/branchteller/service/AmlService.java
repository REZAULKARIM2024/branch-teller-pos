package com.branchteller.service;

import com.branchteller.config.DBConnection;
import com.branchteller.dao.AmlDAO;
import com.branchteller.model.SuspiciousActivityFlag;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Threshold-based suspicious-activity flagging. Any single cash transaction at or above
 * REPORTING_THRESHOLD gets flagged for manual review -- a simplified stand-in for real
 * AML/CTR (Currency Transaction Report) logic. Flags are reviewed manually via AmlPanel.
 */
public class AmlService {

    public static final BigDecimal REPORTING_THRESHOLD = BigDecimal.valueOf(10000);

    private final AmlDAO amlDAO = new AmlDAO();

    /** Call from inside an existing transaction so the flag commits atomically with the txn it describes. */
    public void checkAndFlag(Connection conn, int accountId, Integer txnId, BigDecimal amount, String txnType) throws SQLException {
        if (amount.compareTo(REPORTING_THRESHOLD) < 0) return;

        SuspiciousActivityFlag flag = new SuspiciousActivityFlag();
        flag.setAccountId(accountId);
        flag.setTxnId(txnId);
        flag.setReason(txnType + " of $" + amount + " meets/exceeds the $" + REPORTING_THRESHOLD + " reporting threshold");
        flag.setAmount(amount);
        amlDAO.insert(conn, flag);
    }

    public List<SuspiciousActivityFlag> unreviewed() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return amlDAO.findUnreviewed(conn);
        }
    }

    public List<SuspiciousActivityFlag> all(int limit) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return amlDAO.findAll(conn, limit);
        }
    }

    public void markReviewed(int flagId, int reviewerId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            amlDAO.markReviewed(conn, flagId, reviewerId);
        }
    }
}
