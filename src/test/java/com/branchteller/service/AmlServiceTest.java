package com.branchteller.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Threshold-boundary tests for AmlService.checkAndFlag, the transaction-monitoring
 * trigger every deposit/withdraw/transfer runs through. Uses its own small, self-contained
 * H2 connection (same pattern as GlDaoIntegrationTest) since checkAndFlag() takes a
 * Connection parameter directly rather than opening one via DBConnection -- no need for
 * the shared test database here.
 */
class AmlServiceTest {

    private final AmlService amlService = new AmlService();
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        Class.forName("org.h2.Driver");
        conn = DriverManager.getConnection("jdbc:h2:mem:amltest_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        try (Statement st = conn.createStatement()) {
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
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        conn.close();
    }

    private int flagCount() throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM suspicious_activity_flags")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "1.00, false",
            "500.00, false",
            "9999.99, false",
            "10000.00, true",   // exactly the threshold -- inclusive
            "10000.01, true",
            "25000.00, true"
    })
    void checkAndFlag_boundaryAmounts_matchExpectedFlagOutcome(String amount, boolean shouldFlag) throws Exception {
        amlService.checkAndFlag(conn, 1, 100, new BigDecimal(amount), "DEPOSIT");
        assertEquals(shouldFlag ? 1 : 0, flagCount());
    }

    @Test
    void checkAndFlag_belowThreshold_neverTouchesTheDatabase() throws Exception {
        // The early-return branch runs before any SQL -- passing a null Connection here
        // proves it truly never dereferences conn for a sub-threshold amount.
        assertDoesNotThrow(() -> amlService.checkAndFlag(null, 1, 100, new BigDecimal("500.00"), "DEPOSIT"));
    }

    @Test
    void checkAndFlag_flagReason_mentionsTxnTypeAndAmount() throws Exception {
        amlService.checkAndFlag(conn, 5, 200, new BigDecimal("15000.00"), "WITHDRAW");
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT reason FROM suspicious_activity_flags")) {
            assertTrue(rs.next());
            String reason = rs.getString(1);
            assertTrue(reason.contains("WITHDRAW"));
            assertTrue(reason.contains("15000"));
        }
    }

    @Test
    void checkAndFlag_multipleQualifyingTransactions_eachCreatesItsOwnFlag() throws Exception {
        amlService.checkAndFlag(conn, 1, 10, new BigDecimal("12000.00"), "DEPOSIT");
        amlService.checkAndFlag(conn, 1, 11, new BigDecimal("13000.00"), "TRANSFER_OUT");
        assertEquals(2, flagCount());
    }

    @Test
    void checkAndFlag_recordsTheGivenAccountAndAmount() throws Exception {
        amlService.checkAndFlag(conn, 77, 300, new BigDecimal("10500.00"), "WITHDRAW");
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT account_id, amount, reviewed FROM suspicious_activity_flags")) {
            assertTrue(rs.next());
            assertEquals(77, rs.getInt("account_id"));
            assertEquals(0, rs.getBigDecimal("amount").compareTo(new BigDecimal("10500.00")));
            assertFalse(rs.getBoolean("reviewed"), "A freshly created flag should start unreviewed");
        }
    }
}
