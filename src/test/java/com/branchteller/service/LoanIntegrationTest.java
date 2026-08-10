package com.branchteller.service;

import com.branchteller.model.Loan;
import com.branchteller.model.LoanRepayment;
import com.branchteller.model.User;
import com.branchteller.support.TestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Senior-QA-style integration coverage for the Loans feature (LoanService, backing the Loans
 * tab). There was zero prior test coverage of LoanService itself -- {@code TestDatabase.insertLoan}
 * exists, but only as a bypass helper for {@code CreditScoreIntegrationTest}, never exercising
 * {@code LoanService}'s own apply/approve/reject/disburse/payNextInstallment logic.
 *
 * <p>Two real, related bugs found and fixed:</p>
 * <ol>
 * <li>{@link LoanService#approve}/{@link LoanService#reject} had no state-machine guard at all --
 * an already-REJECTED or already-DISBURSED loan could be "approved" or "rejected" again, writing
 * an audit entry that lies about the before-value. Fixed with the same PENDING-only guard {@code
 * ApprovalService}/{@code ChequeService} already had.</li>
 * <li>{@link LoanService#apply}, {@link LoanService#disburse}, and {@link
 * LoanService#payNextInstallment} never checked the linked account's status -- a loan could be
 * applied for, approved (real manager review effort spent), and disbursed into (or repaid from) a
 * CLOSED account. Fixed with the same "CLOSED blocks, DORMANT doesn't" rule established for
 * Teller Counter/Cheques.</li>
 * </ol>
 */
class LoanIntegrationTest {

    private final LoanService loanService = new LoanService();

    @BeforeAll
    static void setUpSchema() throws Exception {
        TestDatabase.ensureSchema();
    }

    private User teller(TestDatabase.Fixture fx) {
        return new User(fx.tellerId, "teller", "Test Teller", "TELLER", fx.branchId);
    }

    // ------------------------------------------------------------------
    // Happy path
    // ------------------------------------------------------------------

    @Test
    void applyApproveDisburse_creditsAccountAndBuildsFullSchedule() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));

        Loan loan = loanService.apply(fx.customerId, fx.accountId, "PERSONAL",
                new BigDecimal("1200.00"), new BigDecimal("12.00"), 12);
        assertEquals("APPLIED", loan.getStatus());

        loanService.approve(loan.getId(), fx.tellerId);
        loanService.disburse(loan.getId(), teller(fx));

        assertEquals(0, new BigDecimal("1300.00").compareTo(TestDatabase.balanceOf(fx.accountId)));
        List<LoanRepayment> schedule = loanService.schedule(loan.getId());
        assertEquals(12, schedule.size());
        assertTrue(schedule.stream().allMatch(r -> "PENDING".equals(r.getStatus())));
    }

    @Test
    void payNextInstallment_debitsAccountAndMarksInstallmentPaid() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("5000.00"));
        Loan loan = loanService.apply(fx.customerId, fx.accountId, "PERSONAL",
                new BigDecimal("1200.00"), new BigDecimal("0.00"), 12);
        loanService.approve(loan.getId(), fx.tellerId);
        loanService.disburse(loan.getId(), teller(fx));
        BigDecimal balanceAfterDisbursement = TestDatabase.balanceOf(fx.accountId);

        loanService.payNextInstallment(loan.getId(), fx.tellerId);

        List<LoanRepayment> schedule = loanService.schedule(loan.getId());
        assertEquals("PAID", schedule.get(0).getStatus());
        assertEquals("PENDING", schedule.get(1).getStatus());
        assertTrue(TestDatabase.balanceOf(fx.accountId).compareTo(balanceAfterDisbursement) < 0);
    }

    // ------------------------------------------------------------------
    // apply() validation
    // ------------------------------------------------------------------

    @Test
    void apply_nonPositivePrincipalOrTenure_isRejected() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));

        assertThrows(IllegalArgumentException.class, () -> loanService.apply(
                fx.customerId, fx.accountId, "PERSONAL", BigDecimal.ZERO, new BigDecimal("5.00"), 12));
        assertThrows(IllegalArgumentException.class, () -> loanService.apply(
                fx.customerId, fx.accountId, "PERSONAL", new BigDecimal("1000.00"), new BigDecimal("5.00"), 0));
    }

    @Test
    void apply_negativeInterestRate_isRejected_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));

        assertThrows(IllegalArgumentException.class, () -> loanService.apply(
                fx.customerId, fx.accountId, "PERSONAL", new BigDecimal("1000.00"), new BigDecimal("-1.00"), 12));
    }

    @Test
    void apply_closedAccount_isRejected_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        TestDatabase.setAccountStatus(fx.accountId, "CLOSED");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> loanService.apply(
                fx.customerId, fx.accountId, "PERSONAL", new BigDecimal("1000.00"), new BigDecimal("5.00"), 12));
        assertTrue(ex.getMessage().contains("closed"), "Message should explain why: " + ex.getMessage());
    }

    @Test
    void apply_dormantAccount_isAllowed_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        TestDatabase.setAccountStatus(fx.accountId, "DORMANT");

        Loan loan = loanService.apply(fx.customerId, fx.accountId, "PERSONAL",
                new BigDecimal("1000.00"), new BigDecimal("5.00"), 12);

        assertEquals("APPLIED", loan.getStatus());
    }

    // ------------------------------------------------------------------
    // approve() / reject() -- the state-machine guard finding
    // ------------------------------------------------------------------

    @Test
    void approvingAnAlreadyRejectedLoan_isRejected_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        Loan loan = loanService.apply(fx.customerId, fx.accountId, "PERSONAL",
                new BigDecimal("1000.00"), new BigDecimal("5.00"), 12);
        loanService.reject(loan.getId(), fx.tellerId);

        assertThrows(IllegalStateException.class, () -> loanService.approve(loan.getId(), fx.tellerId));
    }

    @Test
    void rejectingAnAlreadyApprovedLoan_isRejected_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        Loan loan = loanService.apply(fx.customerId, fx.accountId, "PERSONAL",
                new BigDecimal("1000.00"), new BigDecimal("5.00"), 12);
        loanService.approve(loan.getId(), fx.tellerId);

        assertThrows(IllegalStateException.class, () -> loanService.reject(loan.getId(), fx.tellerId));
    }

    @Test
    void approvingAnAlreadyDisbursedLoan_isRejected_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        Loan loan = loanService.apply(fx.customerId, fx.accountId, "PERSONAL",
                new BigDecimal("1000.00"), new BigDecimal("5.00"), 12);
        loanService.approve(loan.getId(), fx.tellerId);
        loanService.disburse(loan.getId(), teller(fx));

        assertThrows(IllegalStateException.class, () -> loanService.approve(loan.getId(), fx.tellerId));
    }

    // ------------------------------------------------------------------
    // disburse() / payNextInstallment() -- CLOSED-account guard
    // ------------------------------------------------------------------

    @Test
    void disbursingIntoAClosedAccount_isRejected_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        Loan loan = loanService.apply(fx.customerId, fx.accountId, "PERSONAL",
                new BigDecimal("1000.00"), new BigDecimal("5.00"), 12);
        loanService.approve(loan.getId(), fx.tellerId);

        TestDatabase.setAccountStatus(fx.accountId, "CLOSED");

        assertThrows(IllegalArgumentException.class, () -> loanService.disburse(loan.getId(), teller(fx)));
        assertEquals(0, new BigDecimal("100.00").compareTo(TestDatabase.balanceOf(fx.accountId)), "No funds should have moved");
    }

    @Test
    void payingAnInstallmentFromAClosedAccount_isRejected_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("5000.00"));
        Loan loan = loanService.apply(fx.customerId, fx.accountId, "PERSONAL",
                new BigDecimal("1200.00"), new BigDecimal("0.00"), 12);
        loanService.approve(loan.getId(), fx.tellerId);
        loanService.disburse(loan.getId(), teller(fx));
        BigDecimal balanceAfterDisbursement = TestDatabase.balanceOf(fx.accountId);

        TestDatabase.setAccountStatus(fx.accountId, "CLOSED");

        assertThrows(IllegalArgumentException.class, () -> loanService.payNextInstallment(loan.getId(), fx.tellerId));
        assertEquals(0, balanceAfterDisbursement.compareTo(TestDatabase.balanceOf(fx.accountId)), "No funds should have moved");
    }

    @Test
    void payNextInstallment_insufficientFunds_isRejected() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("0.00"));
        // Zero interest -> calculateEmi's flat-split branch: EMI = 1200.00 / 12 = 100.00 exactly.
        Loan loan = loanService.apply(fx.customerId, fx.accountId, "PERSONAL",
                new BigDecimal("1200.00"), new BigDecimal("0.00"), 12);
        loanService.approve(loan.getId(), fx.tellerId);
        loanService.disburse(loan.getId(), teller(fx));
        assertEquals(0, new BigDecimal("1200.00").compareTo(TestDatabase.balanceOf(fx.accountId)));

        // Drain the account back down below the 100.00 EMI so the next installment can't be covered.
        BankingService bankingService = new BankingService();
        bankingService.withdraw(fx.accountId, new BigDecimal("1150.00"), fx.tellerId, "drain for test");
        assertEquals(0, new BigDecimal("50.00").compareTo(TestDatabase.balanceOf(fx.accountId)));

        assertThrows(InsufficientFundsException.class, () -> loanService.payNextInstallment(loan.getId(), fx.tellerId));
        // Balance and schedule must be untouched by the failed attempt.
        assertEquals(0, new BigDecimal("50.00").compareTo(TestDatabase.balanceOf(fx.accountId)));
        assertEquals("PENDING", loanService.schedule(loan.getId()).get(0).getStatus());
    }
}
