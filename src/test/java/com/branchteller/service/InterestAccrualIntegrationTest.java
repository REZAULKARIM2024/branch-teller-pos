package com.branchteller.service;

import com.branchteller.dao.InterestAccrualDAO;
import com.branchteller.model.InterestAccrual;
import com.branchteller.support.TestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;

import static com.branchteller.config.DBConnection.getConnection;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration-level validation of InterestService.runMonthlyAccrual against a shared H2
 * database (see support/TestDatabase). InterestServiceTest already pins down the pure
 * calculateMonthlyInterest() math in isolation; EndToEndFlowTest has one happy-path
 * idempotency check. This class exists to close the gaps a senior QA pass over the feature
 * turns up -- the parts of the accrual job that only show up once a real database, the
 * general ledger, and multiple accounts are involved:
 *
 * <ul>
 *   <li>the GL posting (debit Interest Expense / credit Customer Deposits Control) is never
 *       asserted on anywhere else in the suite -- for a banking system that is the single
 *       most important side effect to get right, since a silent GL bug means the books
 *       don't balance while the customer-facing balance still looks correct;</li>
 *   <li>the zero-interest path (0% rate, or a $0.00 balance) is documented in the code as
 *       "still record the accrual so the period is marked done, but touch nothing else" --
 *       untested;</li>
 *   <li>the eligibility filter (ACTIVE + SAVINGS only) was never actually exercised against
 *       a non-SAVINGS or a non-ACTIVE account;</li>
 *   <li>the AccrualResult contract (skipped=true/false, amount) returned to callers was
 *       never asserted on -- only the resulting balance was checked;</li>
 *   <li>isolation between accounts in the same run (one already-accrued account must not
 *       block/skip a fresh one) was untested; and</li>
 *   <li>the two read-side methods, history() and the DAO's findByAccountId(), had zero
 *       coverage.</li>
 * </ul>
 */
class InterestAccrualIntegrationTest {

    private final InterestService interestService = new InterestService();
    private final InterestAccrualDAO accrualDAO = new InterestAccrualDAO();

    @BeforeAll
    static void setUpSchema() throws Exception {
        TestDatabase.ensureSchema();
    }

    private static String freshPeriod() throws Exception {
        // Each test gets its own "YYYY-MM" so runs from different tests never collide on the
        // (account_id, period) uniqueness constraint or on each other's history()/findByPeriod results.
        long seq = TestDatabase.nextSeq();
        int month = (int) (1 + (seq % 12));
        return String.format("20%02d-%02d", 50 + (seq % 49), month);
    }

    // ---------- General ledger correctness ----------

    @Test
    void accrual_postsBalancedGlEntries_debitInterestExpense_creditCustomerDeposits() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("10000.00")); // 6.00% rate
        String period = freshPeriod();

        interestService.runMonthlyAccrual(period, fx.tellerId);

        Integer txnId = TestDatabase.transactionIdFor(fx.accountId, "Interest accrual " + period);
        assertNotNull(txnId, "Expected a DEPOSIT transaction row for the accrual");

        // $10,000 at 6.00% / 12 = $50.00, same formula InterestServiceTest pins down separately.
        BigDecimal expected = new BigDecimal("50.00");

        assertEquals(0, expected.compareTo(TestDatabase.glDebitForTxnAndCode(txnId, "5000")),
                "Interest Expense (5000) should be debited for the accrued amount");
        assertEquals(0, expected.compareTo(TestDatabase.glCreditForTxnAndCode(txnId, "1100")),
                "Customer Deposits Control (1100) should be credited for the same amount");

        // And the two legs must actually balance -- no debit without a matching credit.
        assertEquals(2, TestDatabase.glEntryCountForTxn(txnId));
    }

    @Test
    void accrual_createsATransactionRecord_withCorrectAmountAndBalanceAfter() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("2000.00")); // 6.00% rate
        String period = freshPeriod();

        interestService.runMonthlyAccrual(period, fx.tellerId);

        // 2000 * 6.00 / 1200 = 10.00
        BigDecimal expectedInterest = new BigDecimal("10.00");
        BigDecimal expectedBalance = new BigDecimal("2010.00");

        assertEquals(0, expectedBalance.compareTo(TestDatabase.balanceOf(fx.accountId)));

        Integer txnId = TestDatabase.transactionIdFor(fx.accountId, "Interest accrual " + period);
        assertNotNull(txnId);
        // The transaction's own balance_after must agree with the account's actual balance --
        // proves the two were written from the same computed value, not independently derived.
        try (Connection conn = getConnection();
             var ps = conn.prepareStatement("SELECT amount, balance_after, txn_type FROM transactions WHERE txn_id = ?")) {
            ps.setInt(1, txnId);
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0, expectedInterest.compareTo(rs.getBigDecimal("amount")));
                assertEquals(0, expectedBalance.compareTo(rs.getBigDecimal("balance_after")));
                assertEquals("DEPOSIT", rs.getString("txn_type"));
            }
        }
    }

    @Test
    void accrual_writesAnAuditLogEntry_forInterestAccrued() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("5000.00"));
        String period = freshPeriod();

        interestService.runMonthlyAccrual(period, fx.tellerId);

        try (Connection conn = getConnection();
             var ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM audit_trail WHERE entity_type = 'account' AND entity_id = ? AND action = 'INTEREST_ACCRUED'")) {
            ps.setInt(1, fx.accountId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                assertEquals(1, rs.getInt(1), "Expected exactly one INTEREST_ACCRUED audit row for this account/run");
            }
        }
    }

    // ---------- Zero-interest edge cases: accrual row recorded, everything else untouched ----------

    @Test
    void zeroInterestRate_recordsAZeroAccrual_butNeverTouchesBalanceTransactionsOrGl() throws Exception {
        int branchId = TestDatabase.insertBranch("Branch " + TestDatabase.nextSeq());
        int tellerId = TestDatabase.insertUser("teller", "TELLER");
        int customerId = TestDatabase.insertCustomer("VERIFIED");
        int accountId = TestDatabase.insertAccount(customerId, branchId, "SAVINGS",
                new BigDecimal("10000.00"), BigDecimal.ZERO); // 0% rate
        String period = freshPeriod();

        List<InterestService.AccrualResult> results = interestService.runMonthlyAccrual(period, tellerId);

        String accountNumber = TestDatabase.accountNumberFor(accountId);
        InterestService.AccrualResult result = results.stream()
                .filter(r -> r.accountNumber.equals(accountNumber))
                .findFirst().orElseThrow();
        assertFalse(result.skipped, "A first-time run for this period is a real (zero-amount) accrual, not a skip");
        assertEquals(0, BigDecimal.ZERO.compareTo(result.amount));

        // Balance must be untouched.
        assertEquals(0, new BigDecimal("10000.00").compareTo(TestDatabase.balanceOf(accountId)));
        // No DEPOSIT transaction should have been written for a $0.00 "interest" credit.
        assertNull(TestDatabase.transactionIdFor(accountId, "Interest accrual " + period));

        // But the period IS marked done, so a re-run skips it (the whole point of recording
        // a zero-amount row -- see the idempotency tests below).
        List<InterestAccrual> history = interestService.history(period);
        assertTrue(history.stream().anyMatch(a -> a.getAccountId() == accountId && a.getAmount().signum() == 0));
    }

    @Test
    void zeroBalanceAccount_producesNoInterest_evenWithANonZeroRate() throws Exception {
        int branchId = TestDatabase.insertBranch("Branch " + TestDatabase.nextSeq());
        int tellerId = TestDatabase.insertUser("teller", "TELLER");
        int customerId = TestDatabase.insertCustomer("VERIFIED");
        int accountId = TestDatabase.insertAccount(customerId, branchId, "SAVINGS",
                BigDecimal.ZERO, new BigDecimal("6.00")); // real rate, but nothing to earn interest on
        String period = freshPeriod();

        interestService.runMonthlyAccrual(period, tellerId);

        assertEquals(0, BigDecimal.ZERO.compareTo(TestDatabase.balanceOf(accountId)));
        assertNull(TestDatabase.transactionIdFor(accountId, "Interest accrual " + period));
    }

    // ---------- Eligibility filter: ACTIVE SAVINGS accounts only ----------

    @Test
    void nonSavingsAccountType_isExcludedFromTheMonthlySweep() throws Exception {
        int branchId = TestDatabase.insertBranch("Branch " + TestDatabase.nextSeq());
        int tellerId = TestDatabase.insertUser("teller", "TELLER");
        int customerId = TestDatabase.insertCustomer("VERIFIED");
        int checkingAccountId = TestDatabase.insertAccount(customerId, branchId, "CHECKING",
                new BigDecimal("50000.00"), new BigDecimal("6.00"));
        String period = freshPeriod();

        List<InterestService.AccrualResult> results = interestService.runMonthlyAccrual(period, tellerId);

        String checkingAccountNumber = TestDatabase.accountNumberFor(checkingAccountId);
        assertTrue(results.stream().noneMatch(r -> r.accountNumber.equals(checkingAccountNumber)),
                "A CHECKING account must never appear in a SAVINGS-only interest run");
        assertEquals(0, new BigDecimal("50000.00").compareTo(TestDatabase.balanceOf(checkingAccountId)));
    }

    @Test
    void closedSavingsAccount_isExcludedFromTheMonthlySweep() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("10000.00"));
        TestDatabase.setAccountStatus(fx.accountId, "CLOSED");
        String period = freshPeriod();

        List<InterestService.AccrualResult> results = interestService.runMonthlyAccrual(period, fx.tellerId);

        String accountNumber = TestDatabase.accountNumberFor(fx.accountId);
        assertTrue(results.stream().noneMatch(r -> r.accountNumber.equals(accountNumber)),
                "A CLOSED account must be excluded even though it is still a SAVINGS account");
        assertEquals(0, new BigDecimal("10000.00").compareTo(TestDatabase.balanceOf(fx.accountId)));
    }

    // ---------- AccrualResult contract + multi-account isolation ----------

    @Test
    void secondRunForTheSamePeriod_reportsSkippedTrue_andZeroAmount() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("10000.00"));
        String period = freshPeriod();

        interestService.runMonthlyAccrual(period, fx.tellerId); // first run: real accrual
        List<InterestService.AccrualResult> secondRun = interestService.runMonthlyAccrual(period, fx.tellerId);

        String accountNumber = TestDatabase.accountNumberFor(fx.accountId);
        InterestService.AccrualResult result = secondRun.stream()
                .filter(r -> r.accountNumber.equals(accountNumber)).findFirst().orElseThrow();

        assertTrue(result.skipped, "Re-running an already-accrued period must be reported as skipped");
        assertEquals(0, BigDecimal.ZERO.compareTo(result.amount),
                "A skipped result must not also claim a non-zero interest amount");
    }

    @Test
    void oneAlreadyAccruedAccount_doesNotBlockAFreshAccountInTheSameRun() throws Exception {
        TestDatabase.Fixture accountA = TestDatabase.standardFixture(new BigDecimal("10000.00"));
        String period = freshPeriod();

        // Accrue period P for account A only (account B doesn't exist yet).
        interestService.runMonthlyAccrual(period, accountA.tellerId);

        // Now seed a second SAVINGS account and re-run the same period.
        int branchId = TestDatabase.insertBranch("Branch " + TestDatabase.nextSeq());
        int customerId = TestDatabase.insertCustomer("VERIFIED");
        int accountB = TestDatabase.insertAccount(customerId, branchId, "SAVINGS",
                new BigDecimal("4000.00"), new BigDecimal("6.00"));

        List<InterestService.AccrualResult> results = interestService.runMonthlyAccrual(period, accountA.tellerId);

        String numberA = TestDatabase.accountNumberFor(accountA.accountId);
        String numberB = TestDatabase.accountNumberFor(accountB);

        InterestService.AccrualResult resultA = results.stream().filter(r -> r.accountNumber.equals(numberA)).findFirst().orElseThrow();
        InterestService.AccrualResult resultB = results.stream().filter(r -> r.accountNumber.equals(numberB)).findFirst().orElseThrow();

        assertTrue(resultA.skipped, "Account A was already accrued for this period and must be skipped");
        assertFalse(resultB.skipped, "Account B is new to this period and must accrue for real");
        // 4000 * 6.00 / 1200 = 20.00
        assertEquals(0, new BigDecimal("20.00").compareTo(resultB.amount));
        assertEquals(0, new BigDecimal("4020.00").compareTo(TestDatabase.balanceOf(accountB)));
    }

    // ---------- Read-side reporting: history() and the DAO's per-account certificate query ----------

    @Test
    void history_returnsEveryAccrualForThatPeriod_orderedByAccountNumber() throws Exception {
        TestDatabase.Fixture fx1 = TestDatabase.standardFixture(new BigDecimal("1000.00"));
        int branchId = TestDatabase.insertBranch("Branch " + TestDatabase.nextSeq());
        int customerId = TestDatabase.insertCustomer("VERIFIED");
        int account2 = TestDatabase.insertAccount(customerId, branchId, "SAVINGS",
                new BigDecimal("2000.00"), new BigDecimal("6.00"));
        String period = freshPeriod();

        interestService.runMonthlyAccrual(period, fx1.tellerId);

        List<InterestAccrual> history = interestService.history(period);
        assertTrue(history.stream().anyMatch(a -> a.getAccountId() == fx1.accountId));
        assertTrue(history.stream().anyMatch(a -> a.getAccountId() == account2));

        // findByPeriod orders by account_number ASC -- confirm the returned list is actually sorted.
        List<String> accountNumbersInOrder = history.stream().map(InterestAccrual::getAccountNumber).toList();
        List<String> sorted = new java.util.ArrayList<>(accountNumbersInOrder);
        sorted.sort(String::compareTo);
        assertEquals(sorted, accountNumbersInOrder, "history() should be ordered by account number");
    }

    @Test
    void rateAppliedOnTheAccrualRecord_matchesTheAccountsRateAtTheTimeOfAccrual() throws Exception {
        int branchId = TestDatabase.insertBranch("Branch " + TestDatabase.nextSeq());
        int tellerId = TestDatabase.insertUser("teller", "TELLER");
        int customerId = TestDatabase.insertCustomer("VERIFIED");
        int accountId = TestDatabase.insertAccount(customerId, branchId, "SAVINGS",
                new BigDecimal("1000.00"), new BigDecimal("4.75"));
        String period = freshPeriod();

        interestService.runMonthlyAccrual(period, tellerId);

        InterestAccrual recorded = interestService.history(period).stream()
                .filter(a -> a.getAccountId() == accountId).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("4.75").compareTo(recorded.getRateApplied()));
    }

    @Test
    void findByAccountId_returnsAllPeriodsForOneAccount_oldestFirst() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("1000.00"));
        String periodA = "2091-01";
        String periodB = "2091-02";

        interestService.runMonthlyAccrual(periodA, fx.tellerId);
        interestService.runMonthlyAccrual(periodB, fx.tellerId);

        try (Connection conn = getConnection()) {
            List<InterestAccrual> certificate = accrualDAO.findByAccountId(conn, fx.accountId);
            List<String> periods = certificate.stream().map(InterestAccrual::getPeriod)
                    .filter(p -> p.equals(periodA) || p.equals(periodB)).toList();
            assertEquals(List.of(periodA, periodB), periods, "Expected both periods, oldest first");
        }
    }
}
