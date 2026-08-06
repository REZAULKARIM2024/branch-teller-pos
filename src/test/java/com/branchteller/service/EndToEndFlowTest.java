package com.branchteller.service;

import com.branchteller.model.Account;
import com.branchteller.model.PendingApproval;
import com.branchteller.model.SuspiciousActivityFlag;
import com.branchteller.model.Transaction;
import com.branchteller.model.User;
import com.branchteller.support.TestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests that drive the real service layer (BankingService, CustomerService,
 * ApprovalService, InterestService, HoldService, AmlService) against a shared H2 database
 * (see support/TestDatabase + the surefire environmentVariables in pom.xml), the same way
 * a teller session touches the app: onboard a customer, open an account, move money,
 * request/approve/reject, accrue interest, place/release a hold. Each test seeds its own
 * fixture so they can run in any order without interfering with each other.
 */
class EndToEndFlowTest {

    private final CustomerService customerService = new CustomerService();
    private final BankingService bankingService = new BankingService();
    private final ApprovalService approvalService = new ApprovalService();
    private final InterestService interestService = new InterestService();
    private final HoldService holdService = new HoldService();
    private final AmlService amlService = new AmlService();

    @BeforeAll
    static void setUpSchema() throws Exception {
        TestDatabase.ensureSchema();
    }

    @Test
    void customerOnboarding_pendingCustomerCannotOpenAnAccount() throws Exception {
        var customer = customerService.register("Onboarding Test", "555-" + TestDatabase.nextSeq(),
                "onboard@example.test", "1 Test St");
        assertEquals("PENDING", customer.getKycStatus());

        int branchId = TestDatabase.insertBranch("Branch " + TestDatabase.nextSeq());
        assertThrows(IllegalStateException.class,
                () -> customerService.openAccount(customer.getId(), branchId, "SAVINGS", BigDecimal.ZERO, 1));
    }

    @Test
    void customerOnboarding_verifiedCustomerCanOpenAnAccount() throws Exception {
        var customer = customerService.register("Verified Test", "555-" + TestDatabase.nextSeq(),
                "verified@example.test", "2 Test St");
        int actorId = TestDatabase.insertUser("manager", "MANAGER");
        customerService.verifyKyc(customer.getId(), actorId);

        int branchId = TestDatabase.insertBranch("Branch " + TestDatabase.nextSeq());
        Account account = customerService.openAccount(customer.getId(), branchId, "SAVINGS", new BigDecimal("2.50"), actorId);

        assertEquals("SAVINGS", account.getAccountType());
        assertEquals(0, BigDecimal.ZERO.compareTo(account.getBalance()));
        assertNotNull(account.getAccountNumber());
    }

    @Test
    void customerOnboarding_rejectedCustomerCannotOpenAnAccount() throws Exception {
        var customer = customerService.register("Rejected Test", "555-" + TestDatabase.nextSeq(),
                "rejected@example.test", "3 Test St");
        int actorId = TestDatabase.insertUser("manager", "MANAGER");
        customerService.rejectKyc(customer.getId(), actorId);

        int branchId = TestDatabase.insertBranch("Branch " + TestDatabase.nextSeq());
        assertThrows(IllegalStateException.class,
                () -> customerService.openAccount(customer.getId(), branchId, "SAVINGS", BigDecimal.ZERO, actorId));
    }

    @Test
    void depositThenWithdraw_updatesBalanceAndCreatesLedgerEntries() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("1000.00"));

        Transaction deposit = bankingService.deposit(fx.accountId, new BigDecimal("250.00"), fx.tellerId, "Test deposit");
        assertEquals(0, new BigDecimal("1250.00").compareTo(deposit.getBalanceAfter()));

        Transaction withdraw = bankingService.withdraw(fx.accountId, new BigDecimal("400.00"), fx.tellerId, "Test withdraw");
        assertEquals(0, new BigDecimal("850.00").compareTo(withdraw.getBalanceAfter()));

        assertEquals(0, new BigDecimal("850.00").compareTo(TestDatabase.balanceOf(fx.accountId)));
    }

    @Test
    void withdrawExceedingBalance_isInterruptedByInsufficientFunds_andLeavesBalanceUnchanged() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));

        assertThrows(InsufficientFundsException.class,
                () -> bankingService.withdraw(fx.accountId, new BigDecimal("500.00"), fx.tellerId, "Too much"));

        // Simulates an interrupted transaction: the whole attempt must roll back cleanly,
        // leaving the balance exactly where it started rather than partially applied.
        assertEquals(0, new BigDecimal("100.00").compareTo(TestDatabase.balanceOf(fx.accountId)));
    }

    @Test
    void transferBetweenAccounts_bothBalancesUpdateAtomically() throws Exception {
        TestDatabase.Fixture from = TestDatabase.standardFixture(new BigDecimal("500.00"));
        int toAccountId = TestDatabase.insertAccount(from.customerId, from.branchId, "SAVINGS", new BigDecimal("50.00"));

        bankingService.transfer(from.accountId, toAccountId, new BigDecimal("200.00"), from.tellerId, "Test transfer");

        assertEquals(0, new BigDecimal("300.00").compareTo(TestDatabase.balanceOf(from.accountId)));
        assertEquals(0, new BigDecimal("250.00").compareTo(TestDatabase.balanceOf(toAccountId)));
    }

    @Test
    void depositAtAmlThreshold_createsAReviewableFlag_thenMarkReviewedClearsIt() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("0.00"));

        bankingService.deposit(fx.accountId, new BigDecimal("10000.00"), fx.tellerId, "Large deposit");

        List<SuspiciousActivityFlag> unreviewed = amlService.unreviewed();
        boolean found = unreviewed.stream().anyMatch(f -> f.getAccountId() == fx.accountId);
        assertTrue(found, "A $10,000 deposit should create an unreviewed AML flag");

        int reviewerId = TestDatabase.insertUser("reviewer", "MANAGER");
        SuspiciousActivityFlag flag = unreviewed.stream().filter(f -> f.getAccountId() == fx.accountId).findFirst().orElseThrow();
        amlService.markReviewed(flag.getId(), reviewerId);

        boolean stillUnreviewed = amlService.unreviewed().stream().anyMatch(f -> f.getId() == flag.getId());
        assertFalse(stillUnreviewed, "Flag should no longer appear in the unreviewed queue after review");
    }

    @Test
    void approvalFlow_submitThenApprove_executesTheWithdrawal() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("50000.00"));

        PendingApproval request = approvalService.submitWithdrawal(fx.accountId, new BigDecimal("20000.00"),
                fx.tellerId, "Over teller limit");
        assertEquals("PENDING", request.getStatus());
        // Submitting alone must not move any money yet.
        assertEquals(0, new BigDecimal("50000.00").compareTo(TestDatabase.balanceOf(fx.accountId)));

        int managerId = TestDatabase.insertUser("manager", "MANAGER");
        User manager = new User(managerId, "manager", "Manager", "MANAGER", fx.branchId);
        approvalService.approve(request.getId(), manager, "Looks fine");

        assertEquals(0, new BigDecimal("30000.00").compareTo(TestDatabase.balanceOf(fx.accountId)));
        List<PendingApproval> approved = approvalService.history("APPROVED");
        assertTrue(approved.stream().anyMatch(a -> a.getId() == request.getId()));
    }

    @Test
    void approvalFlow_submitThenReject_leavesBalanceUnchanged() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("50000.00"));

        PendingApproval request = approvalService.submitWithdrawal(fx.accountId, new BigDecimal("20000.00"),
                fx.tellerId, "Over teller limit");

        int managerId = TestDatabase.insertUser("manager", "MANAGER");
        User manager = new User(managerId, "manager", "Manager", "MANAGER", fx.branchId);
        approvalService.reject(request.getId(), manager, "Not authorized");

        assertEquals(0, new BigDecimal("50000.00").compareTo(TestDatabase.balanceOf(fx.accountId)),
                "A rejected request must never move funds");
        List<PendingApproval> rejected = approvalService.history("REJECTED");
        assertTrue(rejected.stream().anyMatch(a -> a.getId() == request.getId()));
    }

    @Test
    void interestAccrual_isIdempotentAcrossTwoRunsOfTheSamePeriod() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("10000.00"));
        String period = "2099-0" + (1 + (TestDatabase.nextSeq() % 9)); // unique-ish period per run

        interestService.runMonthlyAccrual(period, fx.tellerId);
        BigDecimal balanceAfterFirstRun = TestDatabase.balanceOf(fx.accountId);
        assertTrue(balanceAfterFirstRun.compareTo(new BigDecimal("10000.00")) > 0, "Interest should have been credited");

        interestService.runMonthlyAccrual(period, fx.tellerId);
        BigDecimal balanceAfterSecondRun = TestDatabase.balanceOf(fx.accountId);

        assertEquals(0, balanceAfterFirstRun.compareTo(balanceAfterSecondRun),
                "Re-running the same period must not double-credit interest");
    }

    @Test
    void holdReducesAvailableBalance_withdrawalBlockedUntilReleased() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("1000.00"));
        int placedBy = TestDatabase.insertUser("compliance", "MANAGER");

        var hold = holdService.placeHold(fx.accountId, new BigDecimal("900.00"), "Fraud investigation", placedBy);

        // Only $100 is available (1000 balance - 900 hold), so a $500 withdrawal must fail.
        assertThrows(InsufficientFundsException.class,
                () -> bankingService.withdraw(fx.accountId, new BigDecimal("500.00"), fx.tellerId, "Blocked by hold"));

        holdService.releaseHold(hold.getId(), placedBy);

        // Now the full balance is available again.
        Transaction txn = bankingService.withdraw(fx.accountId, new BigDecimal("500.00"), fx.tellerId, "After release");
        assertEquals(0, new BigDecimal("500.00").compareTo(txn.getBalanceAfter()));
    }

    @Test
    void accountLookup_findsBySeededAccountNumber() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("42.00"));
        String accountNumber = TestDatabase.accountNumberFor(fx.accountId);

        Optional<Account> found = bankingService.lookupAccount(accountNumber);
        assertTrue(found.isPresent());
        assertEquals(0, new BigDecimal("42.00").compareTo(found.get().getBalance()));
    }
}
