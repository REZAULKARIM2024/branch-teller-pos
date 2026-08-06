package com.branchteller.dao;

import com.branchteller.model.GlAccount;
import com.branchteller.model.GlEntryLine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for GlDAO against a real (if in-memory) relational database, instead
 * of a live MySQL server. H2 stands in for MySQL here -- every GlDAO method takes a plain
 * java.sql.Connection, so the same SQL runs unmodified against H2 in-memory. This is the
 * cheapest way to prove the double-entry bookkeeping actually balances without needing
 * MySQL running in CI.
 */
class GlDaoIntegrationTest {

    private final GlDAO glDAO = new GlDAO();
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        Class.forName("org.h2.Driver");
        // A fresh, uniquely-named in-memory DB per test run so tests don't leak state into each other.
        conn = DriverManager.getConnection("jdbc:h2:mem:gltest_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
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
                    "('1000','Cash and Cash Equivalents','ASSET','DEBIT'), " +
                    "('1100','Customer Deposits Control','LIABILITY','CREDIT')");
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        conn.close();
    }

    @Test
    void postingBothLegsOfADeposit_keepsTheLedgerBalanced() throws Exception {
        // Mirrors what GlService.post(conn, "1000", "1100", amount, txnId, desc) does under the
        // hood: one debit leg, one credit leg, same amount, same description.
        glDAO.postEntry(conn, "1000", 42, new BigDecimal("500.00"), BigDecimal.ZERO, "Deposit to account #7");
        glDAO.postEntry(conn, "1100", 42, BigDecimal.ZERO, new BigDecimal("500.00"), "Deposit to account #7");

        List<GlAccount> trialBalance = glDAO.trialBalance(conn);
        assertEquals(2, trialBalance.size());

        BigDecimal totalDebits = trialBalance.stream().map(GlAccount::getDebitTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredits = trialBalance.stream().map(GlAccount::getCreditTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, totalDebits.compareTo(totalCredits), "Trial balance must always balance: debits == credits");
        assertEquals(0, totalDebits.compareTo(new BigDecimal("500.00")));

        GlAccount cash = trialBalance.stream().filter(a -> "1000".equals(a.getCode())).findFirst().orElseThrow();
        assertEquals(0, cash.getNetBalance().compareTo(new BigDecimal("500.00")));

        GlAccount deposits = trialBalance.stream().filter(a -> "1100".equals(a.getCode())).findFirst().orElseThrow();
        assertEquals(0, deposits.getNetBalance().compareTo(new BigDecimal("500.00")));
    }

    @Test
    void unbalancedPosting_isDetectableViaTrialBalance() throws Exception {
        // If a bug ever posted only one leg (e.g. a missing glService.post() call), the trial
        // balance would visibly fail to balance -- this test documents that as the safety net.
        glDAO.postEntry(conn, "1000", null, new BigDecimal("200.00"), BigDecimal.ZERO, "Broken single-leg post");

        List<GlAccount> trialBalance = glDAO.trialBalance(conn);
        BigDecimal totalDebits = trialBalance.stream().map(GlAccount::getDebitTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredits = trialBalance.stream().map(GlAccount::getCreditTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertTrue(totalDebits.compareTo(totalCredits) != 0, "A single-leg post should show up as out of balance");
    }

    @Test
    void ledgerForAccount_returnsLegsInChronologicalOrder() throws Exception {
        glDAO.postEntry(conn, "1000", 1, new BigDecimal("100.00"), BigDecimal.ZERO, "First deposit");
        glDAO.postEntry(conn, "1100", 1, BigDecimal.ZERO, new BigDecimal("100.00"), "First deposit");
        glDAO.postEntry(conn, "1100", 2, new BigDecimal("30.00"), BigDecimal.ZERO, "Partial withdrawal");
        glDAO.postEntry(conn, "1000", 2, BigDecimal.ZERO, new BigDecimal("30.00"), "Partial withdrawal");

        List<GlEntryLine> cashLegs = glDAO.ledgerForAccount(conn, "1000", null, null);
        assertEquals(2, cashLegs.size());
        assertEquals("First deposit", cashLegs.get(0).getDescription());
        assertEquals(0, cashLegs.get(0).getDebit().compareTo(new BigDecimal("100.00")));
        assertEquals("Partial withdrawal", cashLegs.get(1).getDescription());
        assertEquals(0, cashLegs.get(1).getCredit().compareTo(new BigDecimal("30.00")));
    }

    @Test
    void trialBalance_withDateFilter_excludesEntriesOutsideRange() throws Exception {
        glDAO.postEntry(conn, "1000", 1, new BigDecimal("75.00"), BigDecimal.ZERO, "In-range deposit");
        glDAO.postEntry(conn, "1100", 1, BigDecimal.ZERO, new BigDecimal("75.00"), "In-range deposit");

        LocalDate farFuture = LocalDate.now().plusYears(10);
        List<GlAccount> future = glDAO.trialBalance(conn, farFuture, farFuture.plusDays(1));
        BigDecimal futureDebits = future.stream().map(GlAccount::getDebitTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, futureDebits.compareTo(BigDecimal.ZERO), "Entries posted today shouldn't appear in a future-only window");
    }
}
