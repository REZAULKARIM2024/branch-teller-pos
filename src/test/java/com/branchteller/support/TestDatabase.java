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
                    "('1200','Loans Receivable','ASSET','DEBIT'), " +
                    "('3000','Owners Equity / Capital','EQUITY','CREDIT'), " +
                    "('4000','Interest Income','INCOME','CREDIT'), " +
                    "('5000','Interest Expense','EXPENSE','DEBIT'), " +
                    "('5100','Salaries Expense','EXPENSE','DEBIT'), " +
                    "('9001','Test-Only Ledger Regression Account','ASSET','DEBIT')");

            st.execute("CREATE TABLE employees (" +
                    "employee_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "full_name VARCHAR(100) NOT NULL, " +
                    "position VARCHAR(50) NOT NULL, " +
                    "hourly_rate DECIMAL(8,2) NOT NULL, " +
                    "hire_date DATE NOT NULL, " +
                    "active BOOLEAN NOT NULL DEFAULT TRUE, " +
                    "user_id INT NULL)");

            st.execute("CREATE TABLE time_clock (" +
                    "clock_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "employee_id INT NOT NULL, " +
                    "clock_in TIMESTAMP NOT NULL, " +
                    "clock_out TIMESTAMP NULL)");

            st.execute("CREATE TABLE sanctions_list (" +
                    "entry_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "full_name VARCHAR(100) NOT NULL, " +
                    "list_type VARCHAR(20) NOT NULL, " +
                    "note VARCHAR(255))");

            st.execute("CREATE TABLE screening_results (" +
                    "result_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "customer_id INT NOT NULL, " +
                    "matched_entry_id INT NULL, " +
                    "match_score DECIMAL(5,2) NOT NULL, " +
                    "status VARCHAR(20) NOT NULL DEFAULT 'CLEAR', " +
                    "screened_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "reviewed_by INT NULL)");

            st.execute("CREATE TABLE regulatory_reports (" +
                    "report_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "report_type VARCHAR(10) NOT NULL, " +
                    "reference_no VARCHAR(30) UNIQUE NOT NULL, " +
                    "related_account_id INT NULL, " +
                    "related_flag_id INT NULL, " +
                    "filed_by INT NOT NULL, " +
                    "filed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "narrative VARCHAR(2000))");

            // Same fictional sample entries seeded in production's database/schema.sql, so
            // ComplianceService tests exercise the real word-overlap matching logic against
            // the same data shape (two OFAC-only names, one PEP name where "PEP" is itself
            // part of the seeded full_name -- see ComplianceIntegrationTest for why that matters).
            st.execute("INSERT INTO sanctions_list (full_name, list_type, note) VALUES " +
                    "('Viktor Bout', 'OFAC', 'Sample OFAC SDN-style test entry (fictional use)'), " +
                    "('Ali Khamenei', 'OFAC', 'Sample OFAC SDN-style test entry (fictional use)'), " +
                    "('Nicolas Maduro', 'OFAC', 'Sample OFAC SDN-style test entry (fictional use)'), " +
                    "('John Doe PEP', 'PEP', 'Sample politically-exposed-person test entry'), " +
                    "('Jane Smith PEP', 'PEP', 'Sample politically-exposed-person test entry')");

            st.execute("CREATE TABLE loans (" +
                    "loan_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "customer_id INT NOT NULL, " +
                    "account_id INT NOT NULL, " +
                    "loan_type VARCHAR(50) NOT NULL, " +
                    "principal DECIMAL(15,2) NOT NULL, " +
                    "interest_rate DECIMAL(5,2) NOT NULL, " +
                    "tenure_months INT NOT NULL, " +
                    "status VARCHAR(20) NOT NULL DEFAULT 'APPLIED', " +
                    "applied_date DATE NOT NULL, " +
                    "approved_by INT NULL, " +
                    "disbursed_date DATE NULL)");

            st.execute("CREATE TABLE loan_repayments (" +
                    "repayment_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "loan_id INT NOT NULL, " +
                    "installment_no INT NOT NULL, " +
                    "due_date DATE NOT NULL, " +
                    "amount_due DECIMAL(15,2) NOT NULL, " +
                    "amount_paid DECIMAL(15,2) NOT NULL DEFAULT 0.00, " +
                    "status VARCHAR(20) NOT NULL DEFAULT 'PENDING', " +
                    "paid_date DATE NULL)");

            st.execute("CREATE TABLE credit_score_history (" +
                    "history_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "customer_id INT NOT NULL, " +
                    "score INT NOT NULL, " +
                    "rating VARCHAR(20) NOT NULL, " +
                    "computed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            st.execute("CREATE TABLE payroll_runs (" +
                    "run_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "employee_id INT NOT NULL, " +
                    "period_start DATE NOT NULL, " +
                    "period_end DATE NOT NULL, " +
                    "hours_worked DECIMAL(6,2) NOT NULL, " +
                    "gross_pay DECIMAL(10,2) NOT NULL, " +
                    "tax_withheld DECIMAL(10,2) NOT NULL, " +
                    "net_pay DECIMAL(10,2) NOT NULL, " +
                    "run_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
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

    /** Like {@link #insertCustomer}, but with an exact, caller-chosen full_name instead of the
     *  auto-generated "Test Customer N" pattern -- needed for ComplianceService sanctions-screening
     *  tests, which must control the customer's name precisely to get a deterministic match score
     *  against the shared, seeded sanctions_list. Phone/email are still auto-generated and unique. */
    public static int insertCustomerNamed(String fullName, String kycStatus) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO customers (full_name, phone, email, address, kyc_status) VALUES (?, ?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            long seq = nextSeq();
            ps.setString(1, fullName);
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

    /** Backdates an account's opened_date -- insertAccount() always stamps "today", so
     *  CreditScoreService tests that need to control relationship tenure (months since the
     *  earliest account was opened) must move the date back explicitly after inserting. */
    public static void setAccountOpenedDate(int accountId, LocalDate openedDate) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE accounts SET opened_date = ? WHERE account_id = ?")) {
            ps.setDate(1, java.sql.Date.valueOf(openedDate));
            ps.setInt(2, accountId);
            ps.executeUpdate();
        }
    }

    /** Inserts a loan in DISBURSED status for a customer/account -- bypasses LoanService (which
     *  is exercised directly elsewhere), needed only as a parent row for loan_repayments so
     *  CreditScoreService's on-time-ratio calculation has something to query against. */
    public static int insertLoan(int customerId, int accountId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO loans (customer_id, account_id, loan_type, principal, interest_rate, tenure_months, status, applied_date) " +
                             "VALUES (?, ?, 'PERSONAL', 5000.00, 8.00, 12, 'DISBURSED', ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, customerId);
            ps.setInt(2, accountId);
            ps.setDate(3, java.sql.Date.valueOf(LocalDate.now().minusYears(1)));
            ps.executeUpdate();
            return generatedId(ps);
        }
    }

    /** Inserts a loan_repayments row with an explicit status/due/paid date -- lets
     *  CreditScoreIntegrationTest construct exact, known on-time-repayment ratios. Pass
     *  paidDate == null for a repayment that hasn't been paid (e.g. status "OVERDUE" or "PENDING"). */
    public static void insertRepayment(int loanId, int installmentNo, LocalDate dueDate, String status, LocalDate paidDate) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO loan_repayments (loan_id, installment_no, due_date, amount_due, amount_paid, status, paid_date) " +
                             "VALUES (?, ?, ?, 500.00, ?, ?, ?)")) {
            ps.setInt(1, loanId);
            ps.setInt(2, installmentNo);
            ps.setDate(3, java.sql.Date.valueOf(dueDate));
            ps.setBigDecimal(4, paidDate == null ? BigDecimal.ZERO : new BigDecimal("500.00"));
            ps.setString(5, status);
            if (paidDate == null) ps.setNull(6, java.sql.Types.DATE); else ps.setDate(6, java.sql.Date.valueOf(paidDate));
            ps.executeUpdate();
        }
    }

    /** The customers.credit_score column value -- lets tests confirm computeScore() actually
     *  persisted the score onto the customer record, not just into credit_score_history. */
    public static Integer creditScoreOf(int customerId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT credit_score FROM customers WHERE customer_id = ?")) {
            ps.setInt(1, customerId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                int v = rs.getInt(1);
                return rs.wasNull() ? null : v;
            }
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

    /** Count of suspicious_activity_flags rows for a given account -- lets AML tests use a
     *  fresh, test-owned account id to check "no flag was created" without interference from
     *  flags other tests create in this same shared database. */
    public static int flagCountForAccount(int accountId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM suspicious_activity_flags WHERE account_id = ?")) {
            ps.setInt(1, accountId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** A date guaranteed (for all practical purposes) not to collide with any other test's
     *  date in this run -- lets ReportService tests own a whole calendar day exclusively,
     *  since the shared H2 database otherwise accumulates every other test's transactions
     *  under today's real wall-clock date. */
    public static LocalDate uniqueHistoricalDate() {
        return LocalDate.of(2000, 1, 1).plusDays(nextSeq() % 3650);
    }

    /** Inserts a transaction row with an explicit created_at, bypassing BankingService/NOW()
     *  entirely -- needed to test ReportService's day-boundary grouping precisely. */
    public static void insertTransactionAt(int accountId, String txnType, BigDecimal amount,
                                            BigDecimal balanceAfter, int tellerId, java.time.LocalDateTime createdAt) throws SQLException {
        String sql = "INSERT INTO transactions (account_id, txn_type, amount, balance_after, teller_id, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            ps.setString(2, txnType);
            ps.setBigDecimal(3, amount);
            ps.setBigDecimal(4, balanceAfter);
            ps.setInt(5, tellerId);
            ps.setTimestamp(6, java.sql.Timestamp.valueOf(createdAt));
            ps.executeUpdate();
        }
    }

    /** Inserts a suspicious_activity_flags row with an explicit flagged_at, bypassing
     *  AmlService.checkAndFlag()'s NOW()-based timestamp -- needed to test
     *  ReportService.flagCountForDate()'s day-boundary grouping precisely. */
    public static void insertFlagAt(int accountId, BigDecimal amount, java.time.LocalDateTime flaggedAt) throws SQLException {
        String sql = "INSERT INTO suspicious_activity_flags (account_id, reason, amount, flagged_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            ps.setString(2, "Test flag for report boundary check");
            ps.setBigDecimal(3, amount);
            ps.setTimestamp(4, java.sql.Timestamp.valueOf(flaggedAt));
            ps.executeUpdate();
        }
    }

    /** Inserts one gl_entries leg with an explicit created_at, bypassing GlService.post()'s
     *  NOW()-based timestamp -- needed to test GlService.journal()/ledger()'s day-boundary
     *  filtering and running-balance carry-forward precisely, the same way insertTransactionAt/
     *  insertFlagAt let ReportServiceIntegrationTest own an exclusive historical date. */
    public static void insertGlEntryAt(String glCode, BigDecimal debit, BigDecimal credit, String description,
                                        java.time.LocalDateTime postedAt) throws SQLException {
        String sql = "INSERT INTO gl_entries (gl_account_id, debit, credit, description, created_at) " +
                "SELECT gl_account_id, ?, ?, ?, ? FROM gl_accounts WHERE code = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, debit);
            ps.setBigDecimal(2, credit);
            ps.setString(3, description);
            ps.setTimestamp(4, java.sql.Timestamp.valueOf(postedAt));
            ps.setString(5, glCode);
            ps.executeUpdate();
        }
    }

    /** Inserts an active employee at the given hourly rate, bypassing PayrollService.hire()
     *  (which is exercised directly elsewhere) -- used by payroll tests that need an employee
     *  already in place before calling runPayroll(). */
    public static int insertEmployee(BigDecimal hourlyRate) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO employees (full_name, position, hourly_rate, hire_date, active) VALUES (?, 'Test Position', ?, ?, TRUE)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, "Test Employee " + nextSeq());
            ps.setBigDecimal(2, hourlyRate);
            ps.setDate(3, java.sql.Date.valueOf(LocalDate.now().minusYears(1)));
            ps.executeUpdate();
            return generatedId(ps);
        }
    }

    /** Inserts a completed (clocked-out) time_clock row with explicit in/out timestamps,
     *  bypassing PayrollService.clockIn()/clockOut()'s NOW()-based timestamps -- needed so
     *  payroll tests can seed an exact, known number of worked hours. */
    public static void insertCompletedPunch(int employeeId, java.time.LocalDateTime clockIn, java.time.LocalDateTime clockOut) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO time_clock (employee_id, clock_in, clock_out) VALUES (?, ?, ?)")) {
            ps.setInt(1, employeeId);
            ps.setTimestamp(2, java.sql.Timestamp.valueOf(clockIn));
            ps.setTimestamp(3, java.sql.Timestamp.valueOf(clockOut));
            ps.executeUpdate();
        }
    }

    /** Count of payroll_runs rows for a given employee -- lets a test prove a failed
     *  runPayroll() call left no orphaned row behind. */
    public static int payrollRunCountForEmployee(int employeeId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM payroll_runs WHERE employee_id = ?")) {
            ps.setInt(1, employeeId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Temporarily removes a GL account so a test can force GlService.post() to fail with
     *  "Unknown GL account code" -- used to prove a caller correctly rolls back everything else
     *  it wrote when the GL post fails partway through. MUST be paired with
     *  {@link #restoreGlAccount} in a finally block: this mutates the one shared, whole-JVM H2
     *  database every other test class's fixtures also depend on, and Surefire runs JUnit tests
     *  sequentially in a single fork (no parallel execution configured in pom.xml), so as long as
     *  the code is restored before this test method returns, no other test can ever observe it
     *  missing. */
    public static void temporarilyRemoveGlAccount(String code) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM gl_accounts WHERE code = ?")) {
            ps.setString(1, code);
            ps.executeUpdate();
        }
    }

    /** Restores a GL account previously removed by {@link #temporarilyRemoveGlAccount}. */
    public static void restoreGlAccount(String code, String name, String accountClass, String normalBalance) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO gl_accounts (code, name, account_class, normal_balance) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, code);
            ps.setString(2, name);
            ps.setString(3, accountClass);
            ps.setString(4, normalBalance);
            ps.executeUpdate();
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
