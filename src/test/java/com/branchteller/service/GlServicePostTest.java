package com.branchteller.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GlService.post -- the single entry point every money-movement service
 * uses to write a balanced double-entry pair. Complements the existing GlDaoIntegrationTest
 * (which exercises GlDAO's own SQL directly) by testing the service-level guard clauses.
 */
class GlServicePostTest {

    private final GlService glService = new GlService();
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        Class.forName("org.h2.Driver");
        conn = DriverManager.getConnection("jdbc:h2:mem:glposttest_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        try (Statement st = conn.createStatement()) {
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
            st.execute("INSERT INTO gl_accounts (code, name, account_class, normal_balance) VALUES " +
                    "('1000','Cash','ASSET','DEBIT'), ('1100','Customer Deposits Control','LIABILITY','CREDIT')");
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        conn.close();
    }

    private int entryCount() throws Exception {
        try (Statement st = conn.createStatement();
             var rs = st.executeQuery("SELECT COUNT(*) FROM gl_entries")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void post_zeroAmount_isANoOp_andNeverTouchesTheConnection() {
        // amount.signum() == 0 short-circuits before any SQL -- proven by passing null.
        assertDoesNotThrow(() -> glService.post(null, "1000", "1100", BigDecimal.ZERO, 1, "no-op"));
    }

    @Test
    void post_nullAmount_isANoOp_andNeverTouchesTheConnection() {
        assertDoesNotThrow(() -> glService.post(null, "1000", "1100", null, 1, "no-op"));
    }

    @Test
    void post_validCodes_writesExactlyTwoBalancedLegs() throws Exception {
        glService.post(conn, "1000", "1100", new BigDecimal("250.00"), 99, "Test post");
        assertEquals(2, entryCount());
    }

    @Test
    void post_unknownDebitCode_throwsSqlException() {
        assertThrows(SQLException.class,
                () -> glService.post(conn, "9999", "1100", new BigDecimal("10.00"), 1, "bad code"));
    }

    @Test
    void post_unknownCreditCode_throwsSqlException() {
        assertThrows(SQLException.class,
                () -> glService.post(conn, "1000", "9999", new BigDecimal("10.00"), 1, "bad code"));
    }

    @Test
    void post_negativeAmount_stillPostsBothLegs_signHandledByCaller() throws Exception {
        // GlService.post() itself has no sign guard beyond zero/null -- documents that
        // callers (BankingService etc.) are responsible for only ever passing positive
        // amounts; a negative amount here still writes two rows rather than silently dropping.
        glService.post(conn, "1000", "1100", new BigDecimal("-5.00"), 1, "unexpected negative");
        assertEquals(2, entryCount());
    }
}
