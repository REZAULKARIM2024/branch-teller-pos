package com.branchteller.service;

import com.branchteller.model.Account;
import com.branchteller.model.AuditLog;
import com.branchteller.model.PendingApproval;
import com.branchteller.model.Transaction;
import com.branchteller.model.User;
import com.branchteller.support.TestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Senior-QA-style integration coverage for the Teller Counter feature (BankingService's
 * deposit/withdraw/transfer, backing the Teller Counter tab), closing the gaps this review
 * found on top of the extensive existing coverage in {@link BankingServiceValidationTest}
 * (negative-amount / self-transfer input validation) and {@link EndToEndFlowTest} (happy-path
 * deposit/withdraw/transfer, AML flagging, the approve/reject flow, holds blocking a
 * withdrawal).
 *
 * <p>This review found two real, related bugs, both fixed:</p>
 *
 * <ol>
 * <li>{@link BankingService#deposit}/{@code withdraw}/{@code transfer} never checked {@code
 * Account.status} at all -- a CLOSED account's balance could still be moved. The status was
 * only ever *displayed* (in {@code TellerPanel}'s account-info label and on Correspondence
 * letters), never enforced. Grepping the whole test suite for "CLOSED" before this review
 * found only {@code InterestAccrualIntegrationTest}, which checks a completely different job.
 * Fixed with a {@code requireNotClosed} guard on every account touched by a money movement,
 * including the destination side of a transfer, while deliberately still allowing DORMANT
 * accounts to transact (that's normally how a dormant account gets reactivated). See the tests
 * below prefixed {@code closedAccount}.</li>
 * <li>{@link ApprovalService#approve}'s revert-to-PENDING safety net only caught {@code
 * SQLException}/{@code InsufficientFundsException} -- the new (and the DAO's pre-existing
 * "account not found") {@code IllegalArgumentException} slipped straight through uncaught,
 * leaving a request stuck claimed as APPROVED with no funds moved and no revert. Fixed by
 * broadening that catch to any {@code RuntimeException}. See {@code
 * approvingAWithdrawal_whoseAccountWasClosedAfterQueuing_revertsToPending_regressionTest}.</li>
 * </ol>
 */
class TellerCounterIntegrationTest {

    private final BankingService bankingService = new BankingService();
    private final ApprovalService approvalService = new ApprovalService();
    private final AuditService auditService = new AuditService();

    @BeforeAll
    static void setUpSchema() throws Exception {
        TestDatabase.ensureSchema();
    }

    // ------------------------------------------------------------------
    // CLOSED-account guard -- the headline finding
    // ------------------------------------------------------------------

    @Test
    void closedAccount_cannotReceiveADeposit_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        TestDatabase.setAccountStatus(fx.accountId, "CLOSED");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> bankingService.deposit(fx.accountId, new BigDecimal("50.00"), fx.tellerId, "test"));
        assertTrue(ex.getMessage().contains("closed"), "Message should explain why: " + ex.getMessage());

        assertEquals(0, new BigDecimal("100.00").compareTo(TestDatabase.balanceOf(fx.accountId)),
                "Balance must be completely untouched, not partially applied");
    }

    @Test
    void closedAccount_cannotBeWithdrawnFrom_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        TestDatabase.setAccountStatus(fx.accountId, "CLOSED");

        assertThrows(IllegalArgumentException.class,
                () -> bankingService.withdraw(fx.accountId, new BigDecimal("50.00"), fx.tellerId, "test"));

        assertEquals(0, new BigDecimal("100.00").compareTo(TestDatabase.balanceOf(fx.accountId)));
    }

    @Test
    void closedAccount_cannotSendATransfer_regressionTest() throws Exception {
        TestDatabase.Fixture from = TestDatabase.standardFixture(new BigDecimal("500.00"));
        int toAccountId = TestDatabase.insertAccount(from.customerId, from.branchId, "SAVINGS", new BigDecimal("0.00"));
        TestDatabase.setAccountStatus(from.accountId, "CLOSED");

        assertThrows(IllegalArgumentException.class,
                () -> bankingService.transfer(from.accountId, toAccountId, new BigDecimal("100.00"), from.tellerId, "test"));

        assertEquals(0, new BigDecimal("500.00").compareTo(TestDatabase.balanceOf(from.accountId)));
        assertEquals(0, new BigDecimal("0.00").compareTo(TestDatabase.balanceOf(toAccountId)));
    }

    /** The destination side matters just as much as the source -- a transfer must not be able
     *  to dump funds into a closed account either. */
    @Test
    void closedAccount_cannotReceiveATransfer_regressionTest() throws Exception {
        TestDatabase.Fixture from = TestDatabase.standardFixture(new BigDecimal("500.00"));
        int closedToAccountId = TestDatabase.insertAccount(from.customerId, from.branchId, "SAVINGS", new BigDecimal("0.00"));
        TestDatabase.setAccountStatus(closedToAccountId, "CLOSED");

        assertThrows(IllegalArgumentException.class,
                () -> bankingService.transfer(from.accountId, closedToAccountId, new BigDecimal("100.00"), from.tellerId, "test"));

        assertEquals(0, new BigDecimal("500.00").compareTo(TestDatabase.balanceOf(from.accountId)),
                "The source account must not be debited if the destination leg is rejected");
    }

    @Test
    void dormantAccount_canStillTransact_regressionTest() throws Exception {
        // Deliberately the opposite of the CLOSED tests above -- DORMANT is not a hard stop,
        // since transacting is normally how a dormant account gets reactivated in real banking.
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        TestDatabase.setAccountStatus(fx.accountId, "DORMANT");

        Transaction txn = bankingService.deposit(fx.accountId, new BigDecimal("25.00"), fx.tellerId, "Reactivating");

        assertEquals(0, new BigDecimal("125.00").compareTo(txn.getBalanceAfter()));
    }

    // ------------------------------------------------------------------
    // Approval safety-net regression (the second finding)
    // ------------------------------------------------------------------

    @Test
    void approvingAWithdrawal_whoseAccountWasClosedAfterQueuing_revertsToPending_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("50000.00"));

        PendingApproval request = approvalService.submitWithdrawal(fx.accountId, new BigDecimal("20000.00"),
                fx.tellerId, "Over teller limit");

        // The account gets closed for some unrelated reason while the request is still sitting
        // in the queue, before a manager gets to it.
        TestDatabase.setAccountStatus(fx.accountId, "CLOSED");

        int managerId = TestDatabase.insertUser("manager", "MANAGER");
        User manager = new User(managerId, "manager", "Manager", "MANAGER", fx.branchId);

        assertThrows(IllegalArgumentException.class,
                () -> approvalService.approve(request.getId(), manager, "Looks fine"));

        // The critical assertion: this must NOT be left stuck as "APPROVED" with no funds
        // moved -- it must be reverted back to PENDING, exactly like the InsufficientFunds
        // case this safety net was originally built for.
        List<PendingApproval> pending = approvalService.pending();
        assertTrue(pending.stream().anyMatch(a -> a.getId() == request.getId()),
                "A request whose execution failed must revert to PENDING, not stay stuck APPROVED");

        assertEquals(0, new BigDecimal("50000.00").compareTo(TestDatabase.balanceOf(fx.accountId)),
                "No funds should have moved");
    }

    // ------------------------------------------------------------------
    // Audit trail completeness (sanity check, not previously in doubt but worth pinning)
    // ------------------------------------------------------------------

    @Test
    void everyMoneyMovement_writesAnAuditTrailEntry() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("1000.00"));

        Transaction deposit = bankingService.deposit(fx.accountId, new BigDecimal("50.00"), fx.tellerId, "Audit check");

        List<AuditLog> logs = auditService.byEntityType("account", 500);
        boolean found = logs.stream().anyMatch(l -> l.getEntityId() == fx.accountId && "DEPOSIT".equals(l.getAction()));
        assertTrue(found, "Expected a DEPOSIT audit entry for the account");
        assertNotNull(deposit.getId());
    }
}
