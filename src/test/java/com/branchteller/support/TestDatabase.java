package com.branchteller.support;

import com.branchteller.config.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Creates the H2 in-memory schema that stands in for MySQL during service-layer
 * integration/E2E tests. Surefire (see pom.xml) points DBConnection's DB_URL at a
 * named, DB_CLOSE_DELAY=-1 H2 instance for the entire test JVM, so every service
 * that opens its own connection via DBConnection.getConnection() -- BankingService,
 * CustomerService, ApprovalService, InterestService, HoldService, AmlService's read
 * methods -- talks to the schema created here, with zero changes to production code.
 *
 * The schema is intentionally hand-written (not database/schema.sql replayed
 * verbatim) because that file uses MySQL-only syntax (ENGINE=, inline
 * "UNIQUE KEY name (...)", ENUM columns) -- same approach the pre-existing
 * GlDaoIntegrationTest already used for gl_accounts/gl_entries.
 *
 * Every test class that touches the database calls {@link #ensureSchema()} once
 * (idempotent) and then uses the fixture helpers below, which all generate
 * globally-unique natural keys (account numbers, phone numbers, usernames) so
 * tests from different classes sharing this one process-wide database never
 * collide on a UNIQUE constraint.
 */
public final class TestDatabase {

    private static final AtomicBoolean SCHEMA_READY = new AtomicBoolean(false);
    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 1_000_000_000L);

    private TestDatabase() {}

    public static long nextSeq() {
        return SEQ.incrementAndGet();
    }

    public static synchronized void ensureSchema() throws SQLException {
        if (!SCHEMA_READY.compareAndSet(false, true)) return;

        try (Connection conn = DBConnection.getConnection(); Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE branches (" +
                    "branch_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "name VARCHAR(100) NOT NULL, " +
                    "address VARCHAR(255), " +
                    "routing_code VARCHAR(20) UNIQUE)");

            st.execute("CREATE TABLE users (" +
                    "user_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(50) UNIQUE NOT NULL, " +
                    "password_hash VARCHAR(255) NOT NULL, " +
                    "salt VARCHAR(64) NOT NULL, " +
                    "full_name VARCHAR(100) NOT NULL, " +
                    "role VARCHAR(20) NOT NULL, " +
                    "branch_id INT, " +
                    "active BOOLEAN DEFAULT TRUE, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            st.execute("CREATE TABLE customers (" +
                    "customer_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "full_name VARCHAR(100) NOT NULL, " +
                    "phone VARCHAR(20) UNIQUE NOT NULL, " +
                    "email VARCHAR(100), " +
                    "address VARCHAR(255), " +
                    "kyc_status VARCHAR(20) DEFAULT 'PENDING', " +
                    "credit_score INT, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            st.execute("CREATE TABLE accounts (" +
                    "account_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "account_number VARCHAR(30) UNIQUE NOT NULL, " +
                    "customer_id INT NOT NULL, " +
                    "branch_id INT NOT NULL, " +
                    "account_type VARCHAR(20) NOT NULL DEFAULT 'SAVINGS', " +
                    "balance DECIMAL(15,2) NOT NULL DEFAULT 0.00, " +
                    "interest_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00, " +
                    "status VARCHAR(20) DEFAULT 'ACTIVE', " +
                    "opened_date DATE NOT NULL)");

            st.execute("CREATE TABLE transactions (" +
                    "txn_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "account_id INT NOT NULL, " +
                    "txn_type VARCHAR(20) NOT NULL, " +
                    "amount DECIMAL(15,2) NOT NULL, " +
                    "balance_after DECIMAL(15,2) NOT NULL, " +
                    "teller_id INT NOT NULL, " +
                    "related_txn_id INT NULL, " +
                    "channel VARCHAR(20) DEFAULT 'COUNTER', " +
                    "note VARCHAR(255), " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            st.execute("CREATE TABLE audit_trail (" +
                    "audit_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "actor_id INT, " +
                    "action VARCHAR(50) NOT NULL, " +
                    "entity_type VARCHAR(50) NOT NULL, " +
                    "entity_id INT, " +
                    "before_value VARCHAR(255), " +
                    "after_value VARCHAR(255), " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            st.execute("CREATE TABLE suspicious_activity_flags (" +
                    "flag_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "account_id INT NOT NULL, " +
                    "txn_id INT NULL, " +
                    "reason VARCHAR(255) NOT NULL, " +
                    "amount DECIMAL(15,2) NOT NULL, " +
                    "flagged_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "reviewed BOOLEAN NOT NULL DEFAULT FALSE, " +
                    "reviewed_by INT NULL, " +
                    "review_date TIMESTAMP NULL)");

            st.execute("CREATE TABLE account_holds (" +
                    "hold_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "account_id INT NOT NULL, " +
                    "amount DECIMAL(15,2) NOT NULL, " +
                    "reason VARCHAR(255) NOT NULL, " +
                    "placed_by INT NOT NULL, " +
                    "status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', " +
                    "placed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "released_at TIMESTAMP NULL, " +
                    "released_by INT NULL)");

            st.execute("CREATE TABLE pending_approvals (" +
                    "approval_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "request_type VARCHAR(50) NOT NULL, " +
                    "account_id INT NULL, " +
                    "to_account_id INT NULL, " +
                    "amount DECIMAL(15,2) NULL, " +
                    "requested_by INT NOT NULL, " +
                    "status VARCHAR(20) NOT NULL DEFAULT 'PENDING', " +
                    "approved_by INT NULL, " +
                    "request_note VARCHAR(255), " +
                    "decision_note VARCHAR(255), " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "decided_at TIMESTAMP NULL)");

            st.execute("CREATE TABLE interest_accruals (" +
                    "accrual_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "account_id INT NOT NULL, " +
                    "period VARCHAR(7) NOT NULL, " +
                    "rate_applied DECIMAL(5,2) NOT NULL, " +
                    "amount DECIMAL(15,2) NOT NULL, " +
                    "posted_date DATE NOT NULL, " +
                    "CONSTRAINT uniq_account_period UNIQUE (account_id, period))");

            st.execute("CREATE TABLE gl_accounts (" +
                    "gl_account_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "code VARCHAR(20) UNIQUE NOT NULL, " +
                    "name VARCHAR(100) NOT NULL, " +
                    "account_class VARCHAR(20) NOT NULL, " +
                    "normal_balance VARCHAR(10) NOT NULL)");

            st.execute("CREATE TABLE gl_entries (" +
                    "entry_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "gl_account_id INT NOT NULL, " +
                    "txn_id INT, " +
                    "debit DECIMAL(15,2) NOT NULL DEFAULT 0.00, " +
                    "credit DECIMAL(15,2) NOT NULL DEFAULT 0.00, " +
                    "description VARCHAR(255), " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            // Same chart-of-accounts codes BankingService/InterestService post to in production.
            st.execute("INSERT INTO gl_accounts (code, name, account_class, normal_balance) VALUES " +
                    "('1000','Cash and Cash Equivalents','ASSET','DEBIT'), " +
                    "('1100','Customer Deposits Control','LIABILITY','CREDIT'), " +
                    "('5000','Interest Expense','EXPENSE','DEBIT')");
        }
    }

    // ---------- Fixture helpers -- each opens its own connection, mirroring how the
    // real DAOs/services are used, and returns the generated primary key. ----------

    public static int insertBranch(String name) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO branches (name, routing_code) VALUES (?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, "RT" + nextSeq());
            ps.executeUpdate();
            return generatedId(ps);
        }
    }

    public static int insertUser(String usernamePrefix, String role) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO users (username, password_hash, salt, full_name, role, active) " +
                             "VALUES (?, 'x', 'x', ?, ?, TRUE)",
                     Statement.RETURN_GENERATED_KEYS)) {
            String username = usernamePrefix + nextSeq();
            ps.setString(1, username);
            ps.setString(2, "Test " + role);
            ps.setString(3, role);
            ps.executeUpdate();
            return generatedId(ps);
        }
    }

    public static int insertCustomer(String kycStatus) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO customers (full_name, phone, email, address, kyc_status) VALUES (?, ?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            long seq = nextSeq();
            ps.setString(1, "Test Customer " + seq);
            ps.setString(2, "555-" + seq);
            ps.setString(3, "customer" + seq + "@example.test");
            ps.setString(4, "1 Test St");
            ps.setString(5, kycStatus);
            ps.executeUpdate();
            return generatedId(ps);
        }
    }

    /** Convenience: a VERIFIED customer with an ACTIVE savings account seeded at the given balance. */
    public static int insertAccount(int customerId, int branchId, String accountType, BigDecimal balance) throws SQLException {
        return insertAccount(customerId, branchId, accountType, balance, BigDecimal.ZERO);
    }

    public static int insertAccount(int customerId, int branchId, String accountType, BigDecimal balance, BigDecimal interestRate)
            throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO accounts (account_number, customer_id, branch_id, account_type, balance, interest_rate, opened_date) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, "TST-" + nextSeq());
            ps.setInt(2, customerId);
            ps.setInt(3, branchId);
            ps.setString(4, accountType);
            ps.setBigDecimal(5, balance);
            ps.setBigDecimal(6, interestRate);
            ps.setDate(7, java.sql.Date.valueOf(LocalDate.now()));
            ps.executeUpdate();
            return generatedId(ps);
        }
    }

    public static String accountNumberFor(int accountId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT account_number FROM accounts WHERE account_id = ?")) {
            ps.setInt(1, accountId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    public static BigDecimal balanceOf(int accountId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT balance FROM accounts WHERE account_id = ?")) {
            ps.setInt(1, accountId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    /** Directly flips an account's status (e.g. to "CLOSED"/"INACTIVE") -- used to test that
     *  jobs like the interest accrual sweep correctly exclude non-ACTIVE accounts. */
    public static void setAccountStatus(int accountId, String status) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE accounts SET status = ? WHERE account_id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, accountId);
            ps.executeUpdate();
        }
    }

    /** The most recently-inserted transaction id for an account matching an exact note --
     *  lets tests find "their" transaction (e.g. an interest-accrual deposit) without the
     *  service under test having to hand back an id it doesn't otherwise expose. */
    public static Integer transactionIdFor(int accountId, String note) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT txn_id FROM transactions WHERE account_id = ? AND note = ? ORDER BY txn_id DESC LIMIT 1")) {
            ps.setInt(1, accountId);
            ps.setString(2, note);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    /** The debit leg (if any) that a given transaction posted to a given GL account code. */
    public static BigDecimal glDebitForTxnAndCode(int txnId, String glCode) throws SQLException {
        String sql = "SELECT ge.debit FROM gl_entries ge JOIN gl_accounts ga ON ga.gl_account_id = ge.gl_account_id " +
                "WHERE ge.txn_id = ? AND ga.code = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, txnId);
            ps.setString(2, glCode);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : null;
            }
        }
    }

    /** The credit leg (if any) that a given transaction posted to a given GL account code. */
    public static BigDecimal glCreditForTxnAndCode(int txnId, String glCode) throws SQLException {
        String sql = "SELECT ge.credit FROM gl_entries ge JOIN gl_accounts ga ON ga.gl_account_id = ge.gl_account_id " +
                "WHERE ge.txn_id = ? AND ga.code = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, txnId);
            ps.setString(2, glCode);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : null;
            }
        }
    }

    /** Count of gl_entries rows posted for a given transaction -- used to prove that when
     *  interest is zero, no GL legs are written at all (not even zero-amount ones). */
    public static int glEntryCountForTxn(int txnId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM gl_entries WHERE txn_id = ?")) {
            ps.setInt(1, txnId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** The current kyc_status column value for a customer. */
    public static String kycStatusOf(int customerId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT kyc_status FROM customers WHERE customer_id = ?")) {
            ps.setInt(1, customerId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    /** The full_name for a customer -- used to confirm a registration's data actually persisted. */
    public static String fullNameOf(int customerId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT full_name FROM customers WHERE customer_id = ?")) {
            ps.setInt(1, customerId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    /** The before_value of the most recent audit_trail row for the given entity/action -- lets
     *  tests confirm the audit trail recorded the actual prior state, not a hardcoded guess. */
    public static String auditBeforeValue(String entityType, int entityId, String action) throws SQLException {
        String sql = "SELECT before_value FROM audit_trail WHERE entity_type = ? AND entity_id = ? AND action = ? " +
                "ORDER BY audit_id DESC LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entityType);
            ps.setInt(2, entityId);
            ps.setString(3, action);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** The after_value of the most recent audit_trail row for the given entity/action. */
    public static String auditAfterValue(String entityType, int entityId, String action) throws SQLException {
        String sql = "SELECT after_value FROM audit_trail WHERE entity_type = ? AND entity_id = ? AND action = ? " +
                "ORDER BY audit_id DESC LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entityType);
            ps.setInt(2, entityId);
            ps.setString(3, action);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** Count of audit_trail rows for a given entity/action -- used to prove a step wrote
     *  exactly the audit rows it should (not zero, not duplicated). */
    public static int auditCountFor(String entityType, int entityId, String action) throws SQLException {
        String sql = "SELECT COUNT(*) FROM audit_trail WHERE entity_type = ? AND entity_id = ? AND action = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entityType);
            ps.setInt(2, entityId);
            ps.setString(3, action);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** A ready-to-use test fixture: one branch, one teller user, one VERIFIED customer,
     *  one ACTIVE savings account with the given opening balance. */
    public static Fixture standardFixture(BigDecimal openingBalance) throws SQLException {
        int branchId = insertBranch("Test Branch");
        int tellerId = insertUser("teller", "TELLER");
        int customerId = insertCustomer("VERIFIED");
        int accountId = insertAccount(customerId, branchId, "SAVINGS", openingBalance, new BigDecimal("6.00"));
        return new Fixture(branchId, tellerId, customerId, accountId);
    }

    public static final class Fixture {
        public final int branchId;
        public final int tellerId;
        public final int customerId;
        public final int accountId;

        Fixture(int branchId, int tellerId, int customerId, int accountId) {
            this.branchId = branchId;
            this.tellerId = tellerId;
            this.customerId = customerId;
            this.accountId = accountId;
        }
    }

    private static int generatedId(PreparedStatement ps) throws SQLException {
        try (var keys = ps.getGeneratedKeys()) {
            if (keys.next()) return keys.getInt(1);
        }
        return -1;
    }
}
