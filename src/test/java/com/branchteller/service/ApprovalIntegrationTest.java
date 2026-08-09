package com.branchteller.service;

import com.branchteller.model.PendingApproval;
import com.branchteller.model.User;
import com.branchteller.support.TestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Senior-QA-style integration coverage for Approvals / Maker-Checker (ApprovalService).
 * {@code ApprovalServiceTest} already covers {@code requiresApproval}'s threshold logic in
 * isolation, and {@code EndToEndFlowTest} has one happy-path submit-then-approve and one
 * happy-path submit-then-reject test. This class exists to close the gaps a senior QA review
 * of that coverage would flag -- and it found two real defects, both fixed in this same
 * change:
 *
 * <ul>
 *   <li>{@code reject()} had NO existence check and NO status guard at all (unlike {@code
 *       approve()}, which already checked both) -- you could reject a request that didn't
 *       exist (silent no-op) or, worse, reject a request that had ALREADY been approved and
 *       executed, silently flipping a real, already-moved withdrawal/transfer to look
 *       REJECTED in the queue and audit trail. Fixed: {@code reject()} now mirrors {@code
 *       approve()}'s existence + PENDING-status guard exactly.</li>
 *   <li>{@code approve()} had a check-then-act race: it read the status, and only AFTER
 *       executing the real withdrawal/transfer did it mark the request APPROVED. Two
 *       concurrent {@code approve()} calls on the same still-PENDING request could both pass
 *       the stale check and both execute the money movement -- a double-spend. Fixed: the
 *       status transition is now claimed atomically (an UPDATE ... WHERE status='PENDING')
 *       BEFORE the money movement runs, so only one concurrent caller can ever execute it;
 *       if the money movement then fails, the claim is reverted back to PENDING.</li>
 * </ul>
 */
class ApprovalIntegrationTest {

    private final ApprovalService approvalService = new ApprovalService();
    private final BankingService bankingService = new BankingService();

    @BeforeAll
    static void setUpSchema() throws Exception {
        TestDatabase.ensureSchema();
    }

    private static User managerUser(int id) {
        return new User(id, "manager" + id, "Manager " + id, "MANAGER", 1);
    }

    // ------------------------------------------------------------------
    // submit
    // ------------------------------------------------------------------

    @Test
    void submitWithdrawal_createsPendingRequestWithAccurateAuditTrail() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("10000.00"));

        PendingApproval a = approvalService.submitWithdrawal(fx.accountId, new BigDecimal("6000.00"), fx.tellerId, "Over limit");

        assertEquals("PENDING", a.getStatus());
        assertEquals("WITHDRAW", a.getRequestType());
        assertEquals(1, TestDatabase.auditCountFor("pending_approval", a.getId(), "APPROVAL_REQUESTED"));
        assertEquals(0, new BigDecimal("10000.00").compareTo(TestDatabase.balanceOf(fx.accountId)),
                "Submitting alone must not move any funds");
    }

    @Test
    void submitTransfer_createsPendingRequestWithToAccount() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("10000.00"));
        int toAccountId = TestDatabase.insertAccount(fx.customerId, fx.branchId, "SAVINGS", BigDecimal.ZERO);

        PendingApproval a = approvalService.submitTransfer(fx.accountId, toAccountId, new BigDecimal("7000.00"), fx.tellerId, "Big transfer");

        assertEquals("TRANSFER", a.getRequestType());
        assertEquals(toAccountId, a.getToAccountId());
    }

    // ------------------------------------------------------------------
    // approve
    // ------------------------------------------------------------------

    @Test
    void approve_withdrawal_executesMoneyMovementAndMarksApproved_withAuditTrail() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("10000.00"));
        PendingApproval a = approvalService.submitWithdrawal(fx.accountId, new BigDecimal("6000.00"), fx.tellerId, "Over limit");
        int managerId = TestDatabase.insertUser("manager", "MANAGER");

        approvalService.approve(a.getId(), managerUser(managerId), "Looks fine");

        assertEquals(0, new BigDecimal("4000.00").compareTo(TestDatabase.balanceOf(fx.accountId)));
        PendingApproval approved = approvalService.history("APPROVED").stream()
                .filter(x -> x.getId() == a.getId()).findFirst().orElseThrow();
        assertEquals(managerId, approved.getApprovedBy());
        assertEquals(1, TestDatabase.auditCountFor("pending_approval", a.getId(), "APPROVAL_GRANTED"));
        assertEquals("PENDING", TestDatabase.auditBeforeValue("pending_approval", a.getId(), "APPROVAL_GRANTED"));
    }

    @Test
    void approve_transfer_executesTransferAndMarksApproved() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("10000.00"));
        int toAccountId = TestDatabase.insertAccount(fx.customerId, fx.branchId, "SAVINGS", new BigDecimal("500.00"));
        PendingApproval a = approvalService.submitTransfer(fx.accountId, toAccountId, new BigDecimal("7000.00"), fx.tellerId, "Big transfer");
        int managerId = TestDatabase.insertUser("manager", "MANAGER");

        approvalService.approve(a.getId(), managerUser(managerId), "Approved");

        assertEquals(0, new BigDecimal("3000.00").compareTo(TestDatabase.balanceOf(fx.accountId)));
        assertEquals(0, new BigDecimal("7500.00").compareTo(TestDatabase.balanceOf(toAccountId)));
    }

    @Test
    void approve_onAlreadyApprovedRequest_throwsIllegalStateException_andDoesNotDoubleExecute() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("10000.00"));
        PendingApproval a = approvalService.submitWithdrawal(fx.accountId, new BigDecimal("6000.00"), fx.tellerId, "Over limit");
        int managerId = TestDatabase.insertUser("manager", "MANAGER");

        approvalService.approve(a.getId(), managerUser(managerId), "First decision");
        assertThrows(IllegalStateException.class, () -> approvalService.approve(a.getId(), managerUser(managerId), "Second attempt"));

        assertEquals(0, new BigDecimal("4000.00").compareTo(TestDatabase.balanceOf(fx.accountId)),
                "The withdrawal must have executed exactly once, not twice");
    }

    @Test
    void approve_onNonexistentRequest_throwsIllegalArgumentException() throws Exception {
        int managerId = TestDatabase.insertUser("manager", "MANAGER");
        assertThrows(IllegalArgumentException.class, () -> approvalService.approve(9_999_999, managerUser(managerId), "note"));
    }

    @Test
    void approve_insufficientFunds_revertsToPending_leavingBalanceUnchanged() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        // Submitted against a balance that will no longer cover it by the time it's reviewed.
        PendingApproval a = approvalService.submitWithdrawal(fx.accountId, new BigDecimal("50.00"), fx.tellerId, "Will be underfunded");
        // Drain the account below the requested amount before the manager reviews it.
        bankingService.withdraw(fx.accountId, new BigDecimal("80.00"), fx.tellerId, "Drains balance first");
        int managerId = TestDatabase.insertUser("manager", "MANAGER");

        assertThrows(InsufficientFundsException.class, () -> approvalService.approve(a.getId(), managerUser(managerId), "Try anyway"));

        PendingApproval stillPending = approvalService.pending().stream()
                .filter(x -> x.getId() == a.getId()).findFirst().orElseThrow(
                        () -> new AssertionError("Request must revert back to PENDING, not stay stuck as claimed"));
        assertEquals("PENDING", stillPending.getStatus());
        assertEquals(0, new BigDecimal("20.00").compareTo(TestDatabase.balanceOf(fx.accountId)),
                "Balance should reflect only the successful drain withdrawal, not the failed approval");
    }

    // ------------------------------------------------------------------
    // reject
    // ------------------------------------------------------------------

    @Test
    void reject_marksRejectedWithAccurateAuditTrail_andNeverMovesFunds() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("10000.00"));
        PendingApproval a = approvalService.submitWithdrawal(fx.accountId, new BigDecimal("6000.00"), fx.tellerId, "Over limit");
        int managerId = TestDatabase.insertUser("manager", "MANAGER");

        approvalService.reject(a.getId(), managerUser(managerId), "Not authorized");

        assertEquals(0, new BigDecimal("10000.00").compareTo(TestDatabase.balanceOf(fx.accountId)));
        assertEquals(1, TestDatabase.auditCountFor("pending_approval", a.getId(), "APPROVAL_REJECTED"));
        assertEquals("PENDING", TestDatabase.auditBeforeValue("pending_approval", a.getId(), "APPROVAL_REJECTED"));
    }

    @Test
    void reject_onAlreadyApprovedRequest_throwsIllegalStateException_regressionForMissingGuard() throws Exception {
        // Regression test for the bug found in this review: reject() used to have NO guard
        // at all, so it would silently flip an already-approved (and already-executed)
        // request to REJECTED, misrepresenting a real, already-moved withdrawal.
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("10000.00"));
        PendingApproval a = approvalService.submitWithdrawal(fx.accountId, new BigDecimal("6000.00"), fx.tellerId, "Over limit");
        int managerId = TestDatabase.insertUser("manager", "MANAGER");
        approvalService.approve(a.getId(), managerUser(managerId), "Approved first");

        assertThrows(IllegalStateException.class, () -> approvalService.reject(a.getId(), managerUser(managerId), "Trying to undo"));

        PendingApproval stillApproved = approvalService.history("APPROVED").stream()
                .filter(x -> x.getId() == a.getId()).findFirst().orElseThrow();
        assertEquals("APPROVED", stillApproved.getStatus(), "Status must remain APPROVED, not be overwritten to REJECTED");
        assertEquals(0, new BigDecimal("4000.00").compareTo(TestDatabase.balanceOf(fx.accountId)),
                "The already-executed withdrawal must not be affected by the rejected reject() attempt");
    }

    @Test
    void reject_onNonexistentRequest_throwsIllegalArgumentException() throws Exception {
        int managerId = TestDatabase.insertUser("manager", "MANAGER");
        assertThrows(IllegalArgumentException.class, () -> approvalService.reject(9_999_999, managerUser(managerId), "note"));
    }

    // ------------------------------------------------------------------
    // Concurrency regression: the double-execution race
    // ------------------------------------------------------------------

    @Test
    void concurrentApprove_onSameRequest_onlyOneSucceeds_moneyMovedExactlyOnce() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("50000.00"));
        PendingApproval a = approvalService.submitWithdrawal(fx.accountId, new BigDecimal("20000.00"), fx.tellerId, "Race test");
        int managerAId = TestDatabase.insertUser("managerA", "MANAGER");
        int managerBId = TestDatabase.insertUser("managerB", "MANAGER");

        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<Exception> attempt = () -> {
            barrier.await(5, TimeUnit.SECONDS);
            try {
                approvalService.approve(a.getId(), managerUser(managerAId), "Racing");
                return null;
            } catch (Exception ex) {
                return ex;
            }
        };
        Callable<Exception> attemptB = () -> {
            barrier.await(5, TimeUnit.SECONDS);
            try {
                approvalService.approve(a.getId(), managerUser(managerBId), "Racing");
                return null;
            } catch (Exception ex) {
                return ex;
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Exception> f1 = pool.submit(attempt);
            Future<Exception> f2 = pool.submit(attemptB);
            Exception r1 = f1.get(10, TimeUnit.SECONDS);
            Exception r2 = f2.get(10, TimeUnit.SECONDS);

            // Arrays.asList (not List.of) -- List.of throws NPE on a null element, but a
            // successful approve() intentionally returns null here (see the Callables above),
            // so exactly one of r1/r2 being null is the expected, correct outcome being tested.
            long successes = Arrays.asList(r1, r2).stream().filter(e -> e == null).count();
            long failures = Arrays.asList(r1, r2).stream().filter(e -> e instanceof IllegalStateException).count();

            assertEquals(1, successes, "Exactly one of the two concurrent approve() calls should succeed");
            assertEquals(1, failures, "The other should lose the race with IllegalStateException, not also execute");

            assertEquals(0, new BigDecimal("30000.00").compareTo(TestDatabase.balanceOf(fx.accountId)),
                    "The withdrawal must have executed exactly once (50000 - 20000), never twice");
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------
    // pending() / history()
    // ------------------------------------------------------------------

    @Test
    void pending_and_history_listCorrectStatusBuckets() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("10000.00"));
        int managerId = TestDatabase.insertUser("manager", "MANAGER");

        PendingApproval toApprove = approvalService.submitWithdrawal(fx.accountId, new BigDecimal("1000.00"), fx.tellerId, "a");
        PendingApproval toReject = approvalService.submitWithdrawal(fx.accountId, new BigDecimal("2000.00"), fx.tellerId, "b");
        PendingApproval leftPending = approvalService.submitWithdrawal(fx.accountId, new BigDecimal("3000.00"), fx.tellerId, "c");

        approvalService.approve(toApprove.getId(), managerUser(managerId), "ok");
        approvalService.reject(toReject.getId(), managerUser(managerId), "no");

        List<Integer> pendingIds = approvalService.pending().stream().map(PendingApproval::getId).toList();
        assertTrue(pendingIds.contains(leftPending.getId()));
        assertFalse(pendingIds.contains(toApprove.getId()));
        assertFalse(pendingIds.contains(toReject.getId()));

        assertTrue(approvalService.history("APPROVED").stream().anyMatch(x -> x.getId() == toApprove.getId()));
        assertTrue(approvalService.history("REJECTED").stream().anyMatch(x -> x.getId() == toReject.getId()));
    }
}
