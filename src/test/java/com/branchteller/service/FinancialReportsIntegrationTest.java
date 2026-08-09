package com.branchteller.service;

import com.branchteller.support.TestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Senior-QA-style integration coverage for the Financial Reports feature (GlService's
 * balanceSheet/incomeStatement/cashFlow, shown under the Balance Sheet / Income Statement /
 * Cash Flow sub-tabs), which had ZERO existing tests before this class.
 *
 * <p>This review found one real defect, now fixed in {@link GlService#cashFlow}: it had the
 * exact same off-by-one already found and fixed in {@link GlService#ledger} -- the "Beginning
 * Cash Balance" calculation used a `to` bound of `from` itself, which (per GlDAO's "inclusive of
 * the whole day" semantics) wrongly folded `from`'s own day into the beginning balance, which was
 * THEN double-counted again by the period's own Operating/Investing/Financing lines (which
 * correctly include `from`'s day). {@link #cashFlow_beginningCash_doesNotDoubleCountTheWindowsOwnStartDay_regressionTest()}
 * guards against this regressing.
 *
 * <p>Separately, this review also surfaced (but did NOT "fix", since it's a business-logic gap
 * rather than a code defect) that no service anywhere in this codebase ever posts to an INCOME-
 * class GL account (4000 Interest Income / 4100 Fee Income) -- only PayrollService and
 * InterestService post to EXPENSE accounts. This means the Income Statement's Income section
 * will always show $0.00 and Net Income will always be negative (a "Net Loss") in the current
 * system, which is worth knowing but is a product/feature-completeness gap, not something this
 * QA pass changes.
 *
 * <p>Like GlServiceIntegrationTest, this class avoids absolute assertions on the shared "1000"/
 * "1100" codes in favor of delta assertions or the dedicated, exclusively-owned "9001" test
 * account (seeded in {@link TestDatabase}) for anything requiring an exact expected number.
 */
class FinancialReportsIntegrationTest {

    private final GlService glService = new GlService();
    private final BankingService bankingService = new BankingService();
    private final PayrollService payrollService = new PayrollService();

    @BeforeAll
    static void setUpSchema() throws Exception {
        TestDatabase.ensureSchema();
    }

    // ------------------------------------------------------------------
    // Balance Sheet
    // ------------------------------------------------------------------

    @Test
    void balanceSheet_alwaysBalances_totalAssetsEqualsTotalLiabilitiesAndEquity() throws Exception {
        // The fundamental accounting identity (Assets = Liabilities + Equity) must hold no
        // matter what every other test in this JVM has posted, since it falls straight out of
        // double-entry bookkeeping plus folding Net Income To Date into the equity side.
        GlService.BalanceSheet bs = glService.balanceSheet();
        assertEquals(0, bs.totalAssets.compareTo(bs.totalLiabilitiesAndEquity),
                "Total Assets (" + bs.totalAssets + ") must equal Total Liabilities + Equity (" + bs.totalLiabilitiesAndEquity + ")");
    }

    @Test
    void balanceSheet_classifiesKnownAccountsIntoTheCorrectSections() throws Exception {
        GlService.BalanceSheet bs = glService.balanceSheet();
        assertTrue(bs.assets.stream().anyMatch(a -> "1000".equals(a.getCode())), "Cash (1000) must appear under Assets");
        assertTrue(bs.liabilities.stream().anyMatch(a -> "1100".equals(a.getCode())), "Customer Deposits (1100) must appear under Liabilities");
        assertTrue(bs.equity.stream().anyMatch(a -> "3000".equals(a.getCode())), "Owners Equity (3000) must appear under Equity");
        assertFalse(bs.assets.stream().anyMatch(a -> "1100".equals(a.getCode())), "A liability must never leak into the Assets section");
    }

    @Test
    void balanceSheet_reflectsARealDepositPostedThroughBankingService() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(BigDecimal.ZERO);
        BigDecimal totalAssetsBefore = glService.balanceSheet().totalAssets;

        bankingService.deposit(fx.accountId, new BigDecimal("650.00"), fx.tellerId, "Balance sheet wiring check");

        BigDecimal totalAssetsAfter = glService.balanceSheet().totalAssets;
        assertEquals(0, new BigDecimal("650.00").compareTo(totalAssetsAfter.subtract(totalAssetsBefore)),
                "Total Assets should grow by exactly the deposit amount (Cash is an Asset)");
    }

    // ------------------------------------------------------------------
    // Income Statement
    // ------------------------------------------------------------------

    @Test
    void incomeStatement_excludesActivityOutsideTheDateBoundary() throws Exception {
        LocalDate day = TestDatabase.uniqueHistoricalDate();

        TestDatabase.insertGlEntryAt("5000", new BigDecimal("40.00"), BigDecimal.ZERO, "IS boundary test", day.atTime(10, 0));
        TestDatabase.insertGlEntryAt("9001", BigDecimal.ZERO, new BigDecimal("40.00"), "IS boundary test", day.atTime(10, 0));
        // Outside the window -- must be excluded.
        TestDatabase.insertGlEntryAt("5000", new BigDecimal("999.00"), BigDecimal.ZERO, "IS boundary test", day.plusDays(1).atTime(10, 0));
        TestDatabase.insertGlEntryAt("9001", BigDecimal.ZERO, new BigDecimal("999.00"), "IS boundary test", day.plusDays(1).atTime(10, 0));

        GlService.IncomeStatement is = glService.incomeStatement(day, day);
        assertEquals(0, new BigDecimal("40.00").compareTo(is.totalExpense),
                "Only the in-window 40.00 expense leg should count, not the next day's 999.00");
    }

    @Test
    void incomeStatement_netIncomeIsTotalIncomeMinusTotalExpense() throws Exception {
        // Controlled income + expense postings via the dedicated "9001" contra account, so the
        // delta is exact regardless of what every other test has posted globally.
        GlService.IncomeStatement before = glService.incomeStatement(null, null);

        // +500.00 income: credit 4000 (Interest Income), debit 9001 (contra).
        TestDatabase.insertGlEntryAt("4000", BigDecimal.ZERO, new BigDecimal("500.00"), "IS net income test income", LocalDate.now().atStartOfDay());
        TestDatabase.insertGlEntryAt("9001", new BigDecimal("500.00"), BigDecimal.ZERO, "IS net income test income", LocalDate.now().atStartOfDay());
        // +200.00 expense: debit 5000 (Interest Expense), credit 9001 (contra).
        TestDatabase.insertGlEntryAt("5000", new BigDecimal("200.00"), BigDecimal.ZERO, "IS net income test expense", LocalDate.now().atStartOfDay());
        TestDatabase.insertGlEntryAt("9001", BigDecimal.ZERO, new BigDecimal("200.00"), "IS net income test expense", LocalDate.now().atStartOfDay());

        GlService.IncomeStatement after = glService.incomeStatement(null, null);

        BigDecimal incomeDelta = after.totalIncome.subtract(before.totalIncome);
        BigDecimal expenseDelta = after.totalExpense.subtract(before.totalExpense);
        BigDecimal netIncomeDelta = after.netIncome.subtract(before.netIncome);

        assertEquals(0, new BigDecimal("500.00").compareTo(incomeDelta));
        assertEquals(0, new BigDecimal("200.00").compareTo(expenseDelta));
        assertEquals(0, new BigDecimal("300.00").compareTo(netIncomeDelta), "Net income delta must be income delta minus expense delta (500 - 200 = 300)");
    }

    @Test
    void incomeStatement_reflectsARealPayrollExpensePostedThroughPayrollService() throws Exception {
        // PayrollService.runPayroll() posts to 5100 (Salaries Expense) scoped to a single,
        // freshly-created employee -- unlike InterestService.runMonthlyAccrual(), which sweeps
        // every active savings account in the shared database, so this is the only real service
        // that can safely prove Income Statement wiring with an exact delta assertion.
        int employeeId = TestDatabase.insertEmployee(new BigDecimal("25.00"));
        LocalDate periodEnd = LocalDate.now();
        TestDatabase.insertCompletedPunch(employeeId, periodEnd.atTime(9, 0), periodEnd.atTime(15, 0)); // 6h * 25.00 = 150.00 gross, 120.00 net

        BigDecimal totalExpenseBefore = glService.incomeStatement(null, null).totalExpense;

        int managerId = TestDatabase.insertUser("finrepmgr", "MANAGER");
        var run = payrollService.runPayroll(employeeId, periodEnd.minusDays(14), periodEnd, managerId);

        BigDecimal totalExpenseAfter = glService.incomeStatement(null, null).totalExpense;
        assertEquals(0, run.getNetPay().compareTo(totalExpenseAfter.subtract(totalExpenseBefore)),
                "Total Expense should grow by exactly the payroll run's net pay");
    }

    // ------------------------------------------------------------------
    // Cash Flow
    // ------------------------------------------------------------------

    @Test
    void cashFlow_beginningCash_doesNotDoubleCountTheWindowsOwnStartDay_regressionTest() throws Exception {
        // Regression test for the bug found in this review: cashFlow()'s beginningCash used to
        // fold `from`'s own day into the "beginning" balance AND count it again via the period's
        // own cash-touching entries -- double-counting any cash activity posted exactly on
        // `from`'s date. Uses distinct, exclusively-described legs on the real "1000" Cash
        // account at an exclusively-owned historical date, but asserts via before/after DELTAS
        // rather than absolute numbers: beginningCash's "prior" window is intentionally unbounded
        // (everything before `day`), so it can legitimately already be non-zero because of other
        // tests' own historical-date activity on the same shared "1000" account -- deltas are
        // immune to that the same way the rest of this suite's wiring tests are.
        LocalDate day = TestDatabase.uniqueHistoricalDate();
        GlService.CashFlowStatement before = glService.cashFlow(day, day);

        // Prior activity, strictly BEFORE the window -- must be folded into beginningCash.
        TestDatabase.insertGlEntryAt("1000", new BigDecimal("1000.00"), BigDecimal.ZERO, "CF regression prior", day.minusDays(3).atTime(9, 0));
        TestDatabase.insertGlEntryAt("1100", BigDecimal.ZERO, new BigDecimal("1000.00"), "CF regression prior", day.minusDays(3).atTime(9, 0));
        // Activity ON the window's own start day -- must be counted exactly ONCE, as part of the
        // period's Operating line, NOT folded into beginningCash too.
        TestDatabase.insertGlEntryAt("1000", new BigDecimal("400.00"), BigDecimal.ZERO, "CF regression on-start-day", day.atTime(9, 0));
        TestDatabase.insertGlEntryAt("1100", BigDecimal.ZERO, new BigDecimal("400.00"), "CF regression on-start-day", day.atTime(9, 0));

        GlService.CashFlowStatement after = glService.cashFlow(day, day);

        assertEquals(0, new BigDecimal("1000.00").compareTo(after.beginningCash.subtract(before.beginningCash)),
                "Beginning Cash Balance must increase by exactly the 1000.00 prior activity, NOT also include the window's own start-day activity");
        assertEquals(0, new BigDecimal("400.00").compareTo(after.netChangeInCash.subtract(before.netChangeInCash)),
                "Net change in cash for the window must increase by exactly the 400.00 posted on the start day, counted once");
        assertEquals(0, new BigDecimal("1400.00").compareTo(after.endingCash.subtract(before.endingCash)),
                "Ending Cash must increase by beginningCash's delta (1000) + netChangeInCash's delta (400) = 1400, not double-counting the start day");
        assertEquals(0, after.endingCash.compareTo(after.reconciledLedgerBalance),
                "Ending Cash must reconcile exactly with the Cash account's real ledger balance");
    }

    @Test
    void cashFlow_classifiesEachLineByItsContraAccountsClass() throws Exception {
        LocalDate day = TestDatabase.uniqueHistoricalDate();

        // Contra = 1200 (ASSET, Loans Receivable) -> Investing.
        TestDatabase.insertGlEntryAt("1000", new BigDecimal("300.00"), BigDecimal.ZERO, "CF classify investing", day.atTime(9, 0));
        TestDatabase.insertGlEntryAt("1200", BigDecimal.ZERO, new BigDecimal("300.00"), "CF classify investing", day.atTime(9, 0));
        // Contra = 3000 (EQUITY, Owners Equity) -> Financing.
        TestDatabase.insertGlEntryAt("1000", new BigDecimal("500.00"), BigDecimal.ZERO, "CF classify financing", day.atTime(10, 0));
        TestDatabase.insertGlEntryAt("3000", BigDecimal.ZERO, new BigDecimal("500.00"), "CF classify financing", day.atTime(10, 0));
        // Contra = 1100 (LIABILITY, Customer Deposits) -> Operating.
        TestDatabase.insertGlEntryAt("1000", new BigDecimal("70.00"), BigDecimal.ZERO, "CF classify operating", day.atTime(11, 0));
        TestDatabase.insertGlEntryAt("1100", BigDecimal.ZERO, new BigDecimal("70.00"), "CF classify operating", day.atTime(11, 0));

        GlService.CashFlowStatement cf = glService.cashFlow(day, day);

        assertTrue(cf.investing.stream().anyMatch(l -> l.label.contains("1200") && 0 == new BigDecimal("300.00").compareTo(l.amount)),
                "The 1200-contra leg must be classified as Investing");
        assertTrue(cf.financing.stream().anyMatch(l -> l.label.contains("3000") && 0 == new BigDecimal("500.00").compareTo(l.amount)),
                "The 3000-contra leg must be classified as Financing");
        assertTrue(cf.operating.stream().anyMatch(l -> l.label.contains("1100") && 0 == new BigDecimal("70.00").compareTo(l.amount)),
                "The 1100-contra leg must be classified as Operating");
    }

    @Test
    void cashFlow_endingCashAlwaysReconcilesWithTheLedgerBalance_globalInvariant() throws Exception {
        // Regardless of what every other test in this JVM has posted, the statement's own
        // internal math (beginningCash + netChangeInCash) must always agree with independently
        // summing every debit/credit ever posted to the Cash account -- this is the self-check
        // the feature itself displays as "(Reconciled)" / "(NOT reconciled...)" in the GUI.
        GlService.CashFlowStatement cf = glService.cashFlow(null, null);
        assertEquals(0, cf.endingCash.compareTo(cf.reconciledLedgerBalance),
                "endingCash (" + cf.endingCash + ") must equal the independently-computed ledger balance (" + cf.reconciledLedgerBalance + ")");
    }

    @Test
    void cashFlow_reflectsARealDepositPostedThroughBankingService_classifiedAsOperating() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(BigDecimal.ZERO);
        GlService.CashFlowStatement before = glService.cashFlow(null, null);

        bankingService.deposit(fx.accountId, new BigDecimal("222.00"), fx.tellerId, "Cash flow wiring check");

        GlService.CashFlowStatement after = glService.cashFlow(null, null);
        assertEquals(0, new BigDecimal("222.00").compareTo(after.netOperating.subtract(before.netOperating)),
                "A real deposit's contra account (1100, a LIABILITY) should classify it as Operating and grow netOperating by exactly the deposit amount");
        assertEquals(0, after.endingCash.compareTo(after.reconciledLedgerBalance));
    }
}
