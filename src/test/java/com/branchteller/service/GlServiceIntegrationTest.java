package com.branchteller.service;

import com.branchteller.model.GlAccount;
import com.branchteller.model.GlEntryLine;
import com.branchteller.support.TestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Senior-QA-style integration coverage for the General Ledger feature (GlService's read-side
 * views: trialBalance/listAccounts/journal/ledger), which had ZERO existing coverage at the
 * service layer before this class -- the pre-existing GlDaoIntegrationTest/GlServicePostTest
 * only exercised GlDAO directly against a private, throwaway H2 schema, and GlService.post()
 * in isolation. Nothing previously proved that GlService's three reporting views (the ones the
 * GL tab's Trial Balance/Journal/Ledger sub-tabs actually call) work correctly against the real,
 * shared schema.
 *
 * <p>This review found one real defect, now fixed in {@link GlService#ledger}: filtering the
 * Ledger view by a {@code from} date reset the running balance (and the "Ending balance" label)
 * to zero instead of carrying forward the account's real prior balance -- so a manager narrowing
 * the date range would see a completely wrong "Ending balance" that was actually just that
 * window's net change. {@link #ledger_withFromDateFilter_seedsRunningBalanceFromPriorActivity_regressionTest()}
 * guards against this regressing.
 *
 * <p>Since gl_accounts/gl_entries are shared, whole-JVM tables touched by every other financial
 * feature's tests, absolute-count assertions on "1000"/"1100" are avoided in favor of either
 * (a) before/after delta assertions around a controlled posting, or (b) exclusively-owned
 * historical dates via {@link TestDatabase#uniqueHistoricalDate()} + {@link TestDatabase#insertGlEntryAt}
 * -- the same isolation techniques {@code ReportServiceIntegrationTest} established.
 */
class GlServiceIntegrationTest {

    private final GlService glService = new GlService();
    private final BankingService bankingService = new BankingService();

    @BeforeAll
    static void setUpSchema() throws Exception {
        TestDatabase.ensureSchema();
    }

    private static Optional<GlAccount> accountByCode(List<GlAccount> accounts, String code) {
        return accounts.stream().filter(a -> code.equals(a.getCode())).findFirst();
    }

    // ------------------------------------------------------------------
    // listAccounts / trialBalance
    // ------------------------------------------------------------------

    @Test
    void listAccounts_includesTheKnownChartOfAccountsCodes() throws Exception {
        List<GlAccount> accounts = glService.listAccounts();
        assertTrue(accountByCode(accounts, "1000").isPresent(), "Cash account (1000) must exist");
        assertTrue(accountByCode(accounts, "1100").isPresent(), "Customer Deposits Control (1100) must exist");
        assertEquals("ASSET", accountByCode(accounts, "1000").orElseThrow().getAccountClass());
        assertEquals("DEBIT", accountByCode(accounts, "1000").orElseThrow().getNormalBalance());
        assertEquals("LIABILITY", accountByCode(accounts, "1100").orElseThrow().getAccountClass());
        assertEquals("CREDIT", accountByCode(accounts, "1100").orElseThrow().getNormalBalance());
    }

    @Test
    void trialBalance_alwaysBalances_globalDoubleEntryInvariant() throws Exception {
        // The single most important property of the whole General Ledger feature: total debits
        // must equal total credits across EVERY account, at all times -- not just right after our
        // own postings. Since every other feature's tests (deposits, withdrawals, loans, payroll,
        // interest, payments...) share this same database and post through GlService.post() inside
        // this same JVM, this test's real value is asserting the invariant holds GLOBALLY, proving
        // no feature anywhere in the suite has ever posted an unbalanced (single-leg) entry.
        List<GlAccount> trialBalance = glService.trialBalance();
        BigDecimal totalDebits = trialBalance.stream().map(GlAccount::getDebitTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredits = trialBalance.stream().map(GlAccount::getCreditTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, totalDebits.compareTo(totalCredits),
                "Trial balance must balance globally: total debits (" + totalDebits + ") must equal total credits (" + totalCredits + ")");
    }

    @Test
    void trialBalance_reflectsARealDepositPostedThroughBankingService() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(BigDecimal.ZERO);

        GlAccount cashBefore = accountByCode(glService.trialBalance(), "1000").orElseThrow();
        BigDecimal cashDebitBefore = cashBefore.getDebitTotal();

        bankingService.deposit(fx.accountId, new BigDecimal("777.00"), fx.tellerId, "GL wiring check");

        GlAccount cashAfter = accountByCode(glService.trialBalance(), "1000").orElseThrow();
        assertEquals(0, new BigDecimal("777.00").compareTo(cashAfter.getDebitTotal().subtract(cashDebitBefore)),
                "Cash account's debit total should have increased by exactly the deposit amount");
    }

    // ------------------------------------------------------------------
    // journal
    // ------------------------------------------------------------------

    @Test
    void journal_excludesEntriesOutsideTheDateBoundary() throws Exception {
        LocalDate day = TestDatabase.uniqueHistoricalDate();

        // Each entry below is posted as a BALANCED debit/credit pair (1000/1100), same as every
        // real GlService.post() call -- keeping the global trial-balance invariant intact instead
        // of leaving orphaned single-leg postings behind for the rest of this JVM's test run.
        TestDatabase.insertGlEntryAt("1000", new BigDecimal("11.00"), BigDecimal.ZERO, "GL journal boundary test", day.atStartOfDay());
        TestDatabase.insertGlEntryAt("1100", BigDecimal.ZERO, new BigDecimal("11.00"), "GL journal boundary test", day.atStartOfDay()); // midnight -- start of day, must be included
        TestDatabase.insertGlEntryAt("1000", new BigDecimal("22.00"), BigDecimal.ZERO, "GL journal boundary test", day.atTime(23, 59, 59));
        TestDatabase.insertGlEntryAt("1100", BigDecimal.ZERO, new BigDecimal("22.00"), "GL journal boundary test", day.atTime(23, 59, 59)); // last second of day, must be included
        TestDatabase.insertGlEntryAt("1000", new BigDecimal("999.00"), BigDecimal.ZERO, "GL journal boundary test", day.plusDays(1).atStartOfDay());
        TestDatabase.insertGlEntryAt("1100", BigDecimal.ZERO, new BigDecimal("999.00"), "GL journal boundary test", day.plusDays(1).atStartOfDay()); // next day, must be excluded
        TestDatabase.insertGlEntryAt("1000", new BigDecimal("999.00"), BigDecimal.ZERO, "GL journal boundary test", day.minusDays(1).atTime(23, 59, 59));
        TestDatabase.insertGlEntryAt("1100", BigDecimal.ZERO, new BigDecimal("999.00"), "GL journal boundary test", day.minusDays(1).atTime(23, 59, 59)); // previous day, must be excluded

        List<GlEntryLine> lines = glService.journal(day, day);
        assertEquals(4, lines.size(), "Only the two same-day balanced pairs (4 legs) should appear in the journal for this exclusively-owned day");
        assertTrue(lines.stream().allMatch(l -> "GL journal boundary test".equals(l.getDescription())));
    }

    @Test
    void journal_returnsLegsInChronologicalPostingOrder() throws Exception {
        LocalDate day = TestDatabase.uniqueHistoricalDate();
        TestDatabase.insertGlEntryAt("1000", new BigDecimal("5.00"), BigDecimal.ZERO, "Chrono A", day.atTime(9, 0));
        TestDatabase.insertGlEntryAt("1100", BigDecimal.ZERO, new BigDecimal("5.00"), "Chrono A", day.atTime(9, 0));
        TestDatabase.insertGlEntryAt("1000", new BigDecimal("6.00"), BigDecimal.ZERO, "Chrono B", day.atTime(10, 0));
        TestDatabase.insertGlEntryAt("1100", BigDecimal.ZERO, new BigDecimal("6.00"), "Chrono B", day.atTime(10, 0));

        List<GlEntryLine> lines = glService.journal(day, day);
        assertEquals(4, lines.size());
        assertEquals("Chrono A", lines.get(0).getDescription(), "Earlier-posted leg must come first");
        assertEquals("Chrono A", lines.get(1).getDescription());
        assertEquals("Chrono B", lines.get(2).getDescription());
        assertEquals("Chrono B", lines.get(3).getDescription());
    }

    // ------------------------------------------------------------------
    // ledger
    // ------------------------------------------------------------------

    @Test
    void ledger_unknownGlCode_throwsSqlException() {
        assertThrows(SQLException.class, () -> glService.ledger("NO-SUCH-CODE", null, null));
    }

    @Test
    void ledger_computesRunningBalanceCumulatively_forADebitNormalAccount() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(BigDecimal.ZERO);

        List<GlEntryLine> before = glService.ledger("1000", null, null);
        BigDecimal runningBefore = before.isEmpty() ? BigDecimal.ZERO : before.get(before.size() - 1).getRunningBalance();

        bankingService.deposit(fx.accountId, new BigDecimal("500.00"), fx.tellerId, "Ledger running-balance check");
        bankingService.withdraw(fx.accountId, new BigDecimal("200.00"), fx.tellerId, "Ledger running-balance check");

        List<GlEntryLine> after = glService.ledger("1000", null, null);
        BigDecimal runningAfter = after.get(after.size() - 1).getRunningBalance();

        assertEquals(0, runningBefore.add(new BigDecimal("300.00")).compareTo(runningAfter),
                "Cash's running balance should have grown by net +300.00 (500 debit, 200 credit) after our two postings");
    }

    @Test
    void ledger_withFromDateFilter_seedsRunningBalanceFromPriorActivity_regressionTest() throws Exception {
        // Regression test for the bug found in this review: GlService.ledger() used to always
        // start the running balance at zero, even when `from` narrowed the window -- so filtering
        // by date silently turned "Ending balance" into just that window's net change instead of
        // the account's real balance. This originally used GL code "9001" with hardcoded absolute
        // expected numbers, on the assumption that "9001" was touched by nothing else in the whole
        // suite -- but FinancialReportsIntegrationTest later added its own controlled postings to
        // "9001" too (at its own random historical dates), so a hardcoded "prior balance was
        // exactly 1500.00" assumption can be silently contaminated by that other test's activity
        // landing before `day`. Like the analogous cashFlow regression test, this is now written
        // as a delta test: it independently queries the account's TRUE running balance as of the
        // end of day.minusDays(1) (via the unfiltered from=null code path) as ground truth, then
        // asserts the windowed (from=day) running balance seeds from exactly that value plus this
        // test's own in-window posting -- correct no matter what else is sitting in "9001"'s history.
        LocalDate day = TestDatabase.uniqueHistoricalDate();

        // Prior activity BEFORE the filtered window -- must be carried forward, not dropped.
        TestDatabase.insertGlEntryAt("9001", new BigDecimal("1000.00"), BigDecimal.ZERO, "Prior activity 1", day.minusDays(10).atTime(9, 0));
        TestDatabase.insertGlEntryAt("1100", BigDecimal.ZERO, new BigDecimal("1000.00"), "Prior activity 1", day.minusDays(10).atTime(9, 0));
        TestDatabase.insertGlEntryAt("9001", new BigDecimal("500.00"), BigDecimal.ZERO, "Prior activity 2", day.minusDays(5).atTime(9, 0));
        TestDatabase.insertGlEntryAt("1100", BigDecimal.ZERO, new BigDecimal("500.00"), "Prior activity 2", day.minusDays(5).atTime(9, 0));

        // Ground truth: the account's real running balance as of the end of the day BEFORE the
        // window, established independently via the from=null (full history) code path.
        List<GlEntryLine> priorHistory = glService.ledger("9001", null, day.minusDays(1));
        BigDecimal balanceBeforeWindow = priorHistory.isEmpty()
                ? BigDecimal.ZERO
                : priorHistory.get(priorHistory.size() - 1).getRunningBalance();

        // Activity INSIDE the filtered window.
        TestDatabase.insertGlEntryAt("9001", new BigDecimal("300.00"), BigDecimal.ZERO, "In-window activity", day.atTime(9, 0));
        TestDatabase.insertGlEntryAt("1100", BigDecimal.ZERO, new BigDecimal("300.00"), "In-window activity", day.atTime(9, 0));

        List<GlEntryLine> windowed = glService.ledger("9001", day, day);

        assertEquals(1, windowed.size(), "Only the in-window leg should be returned as a row");
        assertEquals(0, balanceBeforeWindow.add(new BigDecimal("300.00")).compareTo(windowed.get(0).getRunningBalance()),
                "Running balance must carry forward whatever was truly posted before the window, plus the window's own 300.00 -- " +
                        "NOT reset to just the window's 300.00 net change");
    }

    @Test
    void ledger_withNoFromDate_startsRunningBalanceAtZero_sinceThereIsNoPriorWindowToCarryForward() throws Exception {
        // Sanity check that the fix above didn't break the "show all history" case (from == null),
        // which correctly has no prior window and must still start at zero. Uses the shared
        // "1000"/"1100" codes (posted as a balanced pair, preserving the trial-balance invariant)
        // -- safe here because the assertion below is a relative delta against the immediately
        // preceding row, not an absolute number, so it can't be contaminated by other tests'
        // activity on the same account the way an absolute assertion could.
        LocalDate day = TestDatabase.uniqueHistoricalDate();
        TestDatabase.insertGlEntryAt("1000", new BigDecimal("42.00"), BigDecimal.ZERO, "Solo-owned historical leg", day.atTime(9, 0));
        TestDatabase.insertGlEntryAt("1100", BigDecimal.ZERO, new BigDecimal("42.00"), "Solo-owned historical leg", day.atTime(9, 0));

        List<GlEntryLine> all = glService.ledger("1000", null, null);
        int idx = -1;
        for (int i = 0; i < all.size(); i++) {
            if ("Solo-owned historical leg".equals(all.get(i).getDescription())) { idx = i; break; }
        }
        assertTrue(idx >= 0, "Our uniquely-described leg must appear in the unbounded ledger");
        BigDecimal previousRunning = idx == 0 ? BigDecimal.ZERO : all.get(idx - 1).getRunningBalance();
        assertEquals(0, previousRunning.add(new BigDecimal("42.00")).compareTo(all.get(idx).getRunningBalance()));
    }
}
