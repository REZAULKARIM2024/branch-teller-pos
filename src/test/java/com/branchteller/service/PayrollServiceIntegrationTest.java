package com.branchteller.service;

import com.branchteller.model.Employee;
import com.branchteller.model.PayrollRun;
import com.branchteller.model.TimeClockEntry;
import com.branchteller.support.TestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Senior-QA-style integration coverage for PayrollService, which had ZERO existing tests
 * before this class. Runs against the shared TestDatabase H2 instance (PayrollService opens its
 * own connections via DBConnection.getConnection(), same as every other feature's service, so
 * it can't be tested against a private schema the way GlDAO/GlService.post() are).
 *
 * <p>This review found one real defect in {@code PayrollService.runPayroll()}: unlike every
 * other caller of {@code GlService.post()} (BankingService, LoanService, PaymentsService,
 * InterestService -- all of which wrap their work in setAutoCommit(false)/commit()/rollback()),
 * runPayroll() ran its payroll-run insert, audit log, and two-leg GL post as independently
 * auto-committing statements with no surrounding transaction. If the GL post ever failed
 * partway through, the payroll_runs row and audit log entry would already be permanently saved
 * while the ledger was left unbalanced with no way to roll back -- silently violating the
 * double-entry invariant the whole General Ledger feature depends on. Now fixed by wrapping
 * runPayroll() in the same transaction pattern its siblings already use.
 * {@link #runPayroll_whenGlPostFails_rollsBackEverything_regressionTest()} guards against this
 * regressing.
 */
class PayrollServiceIntegrationTest {

    private final PayrollService payrollService = new PayrollService();

    @BeforeAll
    static void setUpSchema() throws Exception {
        TestDatabase.ensureSchema();
    }

    @Test
    void hire_createsAnActiveEmployee_retrievableViaActiveEmployees() throws Exception {
        Employee e = payrollService.hire("Test Hire " + System.nanoTime(), "Teller", new BigDecimal("18.50"));
        assertTrue(e.getId() > 0);
        assertTrue(e.isActive());

        List<Employee> active = payrollService.activeEmployees();
        assertTrue(active.stream().anyMatch(a -> a.getId() == e.getId()));
    }

    @Test
    void clockIn_whileAlreadyClockedIn_throwsIllegalStateException() throws Exception {
        int employeeId = TestDatabase.insertEmployee(new BigDecimal("20.00"));
        payrollService.clockIn(employeeId);
        assertThrows(IllegalStateException.class, () -> payrollService.clockIn(employeeId));
    }

    @Test
    void clockOut_whenNotClockedIn_throwsIllegalStateException() throws Exception {
        int employeeId = TestDatabase.insertEmployee(new BigDecimal("20.00"));
        assertThrows(IllegalStateException.class, () -> payrollService.clockOut(employeeId));
    }

    @Test
    void clockIn_thenClockOut_recordsAClosedPunchVisibleInRecentPunches() throws Exception {
        int employeeId = TestDatabase.insertEmployee(new BigDecimal("20.00"));
        payrollService.clockIn(employeeId);
        payrollService.clockOut(employeeId);

        List<TimeClockEntry> punches = payrollService.recentPunches(employeeId, 5);
        assertEquals(1, punches.size());
        assertNotNull(punches.get(0).getClockIn());
        assertNotNull(punches.get(0).getClockOut(), "clockOut() must close the open punch");
    }

    @Test
    void runPayroll_computesGrossTaxAndNetCorrectly_andPostsABalancedGlEntry() throws Exception {
        int employeeId = TestDatabase.insertEmployee(new BigDecimal("20.00"));
        LocalDate periodStart = LocalDate.now().minusDays(14);
        LocalDate periodEnd = LocalDate.now();
        // Exactly 8.00 hours worked, entirely inside [periodStart, periodEnd].
        TestDatabase.insertCompletedPunch(employeeId,
                periodEnd.atTime(9, 0), periodEnd.atTime(17, 0));

        int managerId = TestDatabase.insertUser("payrollmgr", "MANAGER");
        PayrollRun run = payrollService.runPayroll(employeeId, periodStart, periodEnd, managerId);

        assertEquals(0, new BigDecimal("8.00").compareTo(run.getHoursWorked()));
        assertEquals(0, new BigDecimal("160.00").compareTo(run.getGrossPay()), "8h * $20.00/h = $160.00 gross");
        assertEquals(0, new BigDecimal("32.00").compareTo(run.getTaxWithheld()), "20% flat withholding of $160.00 = $32.00");
        assertEquals(0, new BigDecimal("128.00").compareTo(run.getNetPay()), "$160.00 - $32.00 = $128.00 net");

        assertEquals(1, TestDatabase.payrollRunCountForEmployee(employeeId));
        assertEquals(1, TestDatabase.auditCountFor("employee", employeeId, "PAYROLL_RUN"));

        // The GL post debits Salaries Expense (5100) and credits Cash (1000), both for net pay --
        // txnId is null for payroll (no customer-facing transactions table row), so look it up via
        // the ledger's most recent legs for these codes carrying our exact net-pay amount instead.
        List<PayrollRun> history = payrollService.payrollHistory(employeeId);
        assertEquals(1, history.size());
        assertEquals(0, new BigDecimal("128.00").compareTo(history.get(0).getNetPay()));
    }

    @Test
    void runPayroll_forNonexistentEmployee_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> payrollService.runPayroll(9_999_999, LocalDate.now().minusDays(7), LocalDate.now(), 1));
    }

    @Test
    void runPayroll_whenGlPostFails_rollsBackEverything_regressionTest() throws Exception {
        // Regression test for the bug found in this review: before the fix, runPayroll() had no
        // surrounding transaction, so if the GL post failed after the payroll_runs insert and
        // audit log had already auto-committed, those rows would be permanently stuck while the
        // ledger was left unbalanced. Forces that exact failure by temporarily removing the
        // Salaries Expense (5100) GL account so GlService.post()'s debit leg lookup throws
        // SQLException, then proves NOTHING runPayroll() wrote survives the rollback.
        int employeeId = TestDatabase.insertEmployee(new BigDecimal("15.00"));
        LocalDate periodStart = LocalDate.now().minusDays(14);
        LocalDate periodEnd = LocalDate.now();
        TestDatabase.insertCompletedPunch(employeeId, periodEnd.atTime(9, 0), periodEnd.atTime(13, 0)); // 4h

        TestDatabase.temporarilyRemoveGlAccount("5100");
        try {
            assertThrows(SQLException.class,
                    () -> payrollService.runPayroll(employeeId, periodStart, periodEnd, 1));

            assertEquals(0, TestDatabase.payrollRunCountForEmployee(employeeId),
                    "A failed runPayroll() must not leave a payroll_runs row behind");
            assertEquals(0, TestDatabase.auditCountFor("employee", employeeId, "PAYROLL_RUN"),
                    "A failed runPayroll() must not leave an audit log entry behind");
        } finally {
            TestDatabase.restoreGlAccount("5100", "Salaries Expense", "EXPENSE", "DEBIT");
        }
    }
}
