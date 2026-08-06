package com.branchteller.dao;

import com.branchteller.model.GlAccount;
import com.branchteller.model.GlEntryLine;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GlDAO {

    public Optional<Integer> findAccountIdByCode(Connection conn, String code) throws SQLException {
        String sql = "SELECT gl_account_id FROM gl_accounts WHERE code = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(rs.getInt(1));
            }
        }
        return Optional.empty();
    }

    /** Posts one leg of a double-entry journal line. Call twice (debit + credit) per business event. */
    public void postEntry(Connection conn, String glCode, Integer txnId, BigDecimal debit, BigDecimal credit, String description) throws SQLException {
        int glAccountId = findAccountIdByCode(conn, glCode)
                .orElseThrow(() -> new SQLException("Unknown GL account code: " + glCode));
        String sql = "INSERT INTO gl_entries (gl_account_id, txn_id, debit, credit, description) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glAccountId);
            if (txnId != null) ps.setInt(2, txnId); else ps.setNull(2, Types.INTEGER);
            ps.setBigDecimal(3, debit);
            ps.setBigDecimal(4, credit);
            ps.setString(5, description);
            ps.executeUpdate();
        }
    }

    public List<GlAccount> trialBalance(Connection conn) throws SQLException {
        return trialBalance(conn, null, null);
    }

    /** Same as trialBalance(conn) but optionally restricted to entries posted within [from, to). Used
     *  by the Income Statement to total INCOME/EXPENSE activity for a specific period. */
    public List<GlAccount> trialBalance(Connection conn, LocalDate from, LocalDate to) throws SQLException {
        // Date filters go in the JOIN's ON clause (not WHERE) so accounts with no matching
        // entries in the period still appear with zero totals rather than being dropped.
        StringBuilder sql = new StringBuilder(
                "SELECT a.gl_account_id, a.code, a.name, a.account_class, a.normal_balance, " +
                "COALESCE(SUM(e.debit),0) AS debit_total, COALESCE(SUM(e.credit),0) AS credit_total " +
                "FROM gl_accounts a LEFT JOIN gl_entries e ON e.gl_account_id = a.gl_account_id");
        if (from != null) sql.append(" AND e.created_at >= ?");
        if (to != null) sql.append(" AND e.created_at < ?");
        sql.append(" GROUP BY a.gl_account_id, a.code, a.name, a.account_class, a.normal_balance ORDER BY a.code");

        List<GlAccount> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (from != null) ps.setTimestamp(idx++, Timestamp.valueOf(from.atStartOfDay()));
            if (to != null) ps.setTimestamp(idx++, Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    GlAccount a = new GlAccount();
                    a.setId(rs.getInt("gl_account_id"));
                    a.setCode(rs.getString("code"));
                    a.setName(rs.getString("name"));
                    a.setAccountClass(rs.getString("account_class"));
                    a.setNormalBalance(rs.getString("normal_balance"));
                    a.setDebitTotal(rs.getBigDecimal("debit_total"));
                    a.setCreditTotal(rs.getBigDecimal("credit_total"));
                    results.add(a);
                }
            }
        }
        return results;
    }

    /** General Journal: every posted leg across every account, in chronological posting order.
     *  `from`/`to` are inclusive/exclusive date bounds; either or both may be null for "no bound". */
    public List<GlEntryLine> journal(Connection conn, LocalDate from, LocalDate to) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT e.entry_id, e.created_at, a.code, a.name, e.debit, e.credit, e.description, e.txn_id " +
                "FROM gl_entries e JOIN gl_accounts a ON a.gl_account_id = e.gl_account_id WHERE 1=1");
        if (from != null) sql.append(" AND e.created_at >= ?");
        if (to != null) sql.append(" AND e.created_at < ?");
        sql.append(" ORDER BY e.created_at, e.entry_id");

        List<GlEntryLine> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (from != null) ps.setTimestamp(idx++, Timestamp.valueOf(from.atStartOfDay()));
            if (to != null) ps.setTimestamp(idx++, Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapEntryLine(rs));
            }
        }
        return results;
    }

    /** General Ledger: every posted leg for a single GL account, in chronological order, so the
     *  caller can compute a running balance (T-account view). */
    public List<GlEntryLine> ledgerForAccount(Connection conn, String glCode, LocalDate from, LocalDate to) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT e.entry_id, e.created_at, a.code, a.name, e.debit, e.credit, e.description, e.txn_id " +
                "FROM gl_entries e JOIN gl_accounts a ON a.gl_account_id = e.gl_account_id WHERE a.code = ?");
        if (from != null) sql.append(" AND e.created_at >= ?");
        if (to != null) sql.append(" AND e.created_at < ?");
        sql.append(" ORDER BY e.created_at, e.entry_id");

        List<GlEntryLine> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            ps.setString(idx++, glCode);
            if (from != null) ps.setTimestamp(idx++, Timestamp.valueOf(from.atStartOfDay()));
            if (to != null) ps.setTimestamp(idx++, Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapEntryLine(rs));
            }
        }
        return results;
    }

    /** Every posting that touches the given cash-like account (e.g. "1000"), paired with its "contra"
     *  account -- the other leg of the same journal entry, matched by identical description + timestamp
     *  (GlService.post() always writes both legs together with the same description and created_at).
     *  Used to build the Statement of Cash Flows: each cash movement is classified Operating/Investing/
     *  Financing based on the contra account's class. If a match can't be found (shouldn't normally
     *  happen), contraCode/contraName/contraClass come back null and the caller treats it as Operating. */
    public List<GlEntryLine> cashTouchingEntries(Connection conn, String cashCode, LocalDate from, LocalDate to) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT e.entry_id, e.created_at, e.debit, e.credit, e.description, e.txn_id, " +
                "(SELECT a2.code FROM gl_entries e2 JOIN gl_accounts a2 ON a2.gl_account_id = e2.gl_account_id " +
                "   WHERE e2.description = e.description AND e2.created_at = e.created_at " +
                "   AND e2.entry_id <> e.entry_id AND e2.gl_account_id <> e.gl_account_id " +
                "   ORDER BY e2.entry_id LIMIT 1) AS contra_code, " +
                "(SELECT a2.name FROM gl_entries e2 JOIN gl_accounts a2 ON a2.gl_account_id = e2.gl_account_id " +
                "   WHERE e2.description = e.description AND e2.created_at = e.created_at " +
                "   AND e2.entry_id <> e.entry_id AND e2.gl_account_id <> e.gl_account_id " +
                "   ORDER BY e2.entry_id LIMIT 1) AS contra_name, " +
                "(SELECT a2.account_class FROM gl_entries e2 JOIN gl_accounts a2 ON a2.gl_account_id = e2.gl_account_id " +
                "   WHERE e2.description = e.description AND e2.created_at = e.created_at " +
                "   AND e2.entry_id <> e.entry_id AND e2.gl_account_id <> e.gl_account_id " +
                "   ORDER BY e2.entry_id LIMIT 1) AS contra_class " +
                "FROM gl_entries e JOIN gl_accounts cash ON cash.gl_account_id = e.gl_account_id AND cash.code = ? " +
                "WHERE 1=1");
        if (from != null) sql.append(" AND e.created_at >= ?");
        if (to != null) sql.append(" AND e.created_at < ?");
        sql.append(" ORDER BY e.created_at, e.entry_id");

        List<GlEntryLine> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            ps.setString(idx++, cashCode);
            if (from != null) ps.setTimestamp(idx++, Timestamp.valueOf(from.atStartOfDay()));
            if (to != null) ps.setTimestamp(idx++, Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    GlEntryLine line = new GlEntryLine();
                    line.setEntryId(rs.getInt("entry_id"));
                    Timestamp ts = rs.getTimestamp("created_at");
                    if (ts != null) line.setPostedAt(ts.toLocalDateTime());
                    line.setDebit(rs.getBigDecimal("debit"));
                    line.setCredit(rs.getBigDecimal("credit"));
                    line.setDescription(rs.getString("description"));
                    int txnId = rs.getInt("txn_id");
                    line.setTxnId(rs.wasNull() ? null : txnId);
                    line.setContraCode(rs.getString("contra_code"));
                    line.setContraName(rs.getString("contra_name"));
                    line.setContraClass(rs.getString("contra_class"));
                    results.add(line);
                }
            }
        }
        return results;
    }

    public List<GlAccount> listAccounts(Connection conn) throws SQLException {
        String sql = "SELECT gl_account_id, code, name, account_class, normal_balance FROM gl_accounts ORDER BY code";
        List<GlAccount> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                GlAccount a = new GlAccount();
                a.setId(rs.getInt("gl_account_id"));
                a.setCode(rs.getString("code"));
                a.setName(rs.getString("name"));
                a.setAccountClass(rs.getString("account_class"));
                a.setNormalBalance(rs.getString("normal_balance"));
                results.add(a);
            }
        }
        return results;
    }

    private GlEntryLine mapEntryLine(ResultSet rs) throws SQLException {
        GlEntryLine line = new GlEntryLine();
        line.setEntryId(rs.getInt("entry_id"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) line.setPostedAt(ts.toLocalDateTime());
        line.setCode(rs.getString("code"));
        line.setAccountName(rs.getString("name"));
        line.setDebit(rs.getBigDecimal("debit"));
        line.setCredit(rs.getBigDecimal("credit"));
        line.setDescription(rs.getString("description"));
        int txnId = rs.getInt("txn_id");
        line.setTxnId(rs.wasNull() ? null : txnId);
        return line;
    }
}
