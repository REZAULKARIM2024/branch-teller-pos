package com.branchteller.service;

import com.branchteller.model.Cheque;
import com.branchteller.support.TestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Senior-QA-style integration coverage for the Cheques feature (ChequeService, backing the
 * Cheques tab). There was zero prior test coverage -- grepping the whole test suite for "Cheque"
 * before this review found nothing, and the shared H2 test schema didn't even have a {@code
 * cheques} table (fixed in {@link TestDatabase#ensureSchema()} as part of this review).
 *
 * <p>The headline finding: {@link ChequeService#deposit} never looked the destination account up
 * at all, let alone checked its status -- a cheque could be queued (and later cleared, moving
 * real funds) against a CLOSED account, the same class of bug this review already found and fixed
 * in {@code BankingService}. {@link ChequeService#clear} also didn't guard against the account
 * being closed *after* the cheque was queued but before a manager/teller clears it. Both fixed
 * with the same "CLOSED blocks, DORMANT doesn't" rule already established for Teller Counter. Also
 * newly rejects a blank cheque number and depositing the exact same cheque number twice against
 * the same account while an earlier deposit is still active.</p>
 */
class ChequeIntegrationTest {

    private final ChequeService chequeService = new ChequeService();
    private final AuditService auditService = new AuditService();

    @BeforeAll
    static void setUpSchema() throws Exception {
        TestDatabase.ensureSchema();
    }

    // ------------------------------------------------------------------
    // Happy path
    // ------------------------------------------------------------------

    @Test
    void depositThenClear_creditsTheAccountAndWritesAuditTrail() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));

        Cheque cheque = chequeService.deposit(fx.accountId, "CHQ-1001", new BigDecimal("250.00"), fx.tellerId, "test");
        assertEquals("PENDING", cheque.getStatus());
        assertTrue(chequeService.pendingCheques().stream().anyMatch(c -> c.getId() == cheque.getId()));

        chequeService.clear(cheque.getId(), fx.tellerId);

        assertEquals(0, new BigDecimal("350.00").compareTo(TestDatabase.balanceOf(fx.accountId)));
        assertFalse(chequeService.pendingCheques().stream().anyMatch(c -> c.getId() == cheque.getId()),
                "A cleared cheque must drop out of the pending queue");
        assertTrue(auditService.byEntityType("cheque", 500).stream()
                .anyMatch(l -> l.getEntityId() == cheque.getId() && "CHEQUE_CLEARED".equals(l.getAction())));
    }

    @Test
    void bounce_leavesTheBalanceUntouched() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        Cheque cheque = chequeService.deposit(fx.accountId, "CHQ-BOUNCE", new BigDecimal("50.00"), fx.tellerId, "test");

        chequeService.bounce(cheque.getId(), fx.tellerId);

        assertEquals(0, new BigDecimal("100.00").compareTo(TestDatabase.balanceOf(fx.accountId)));
        assertFalse(chequeService.pendingCheques().stream().anyMatch(c -> c.getId() == cheque.getId()));
    }

    // ------------------------------------------------------------------
    // Validation -- deposit()
    // ------------------------------------------------------------------

    @Test
    void nonPositiveAmount_isRejected() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));

        assertThrows(IllegalArgumentException.class,
                () -> chequeService.deposit(fx.accountId, "CHQ-ZERO", BigDecimal.ZERO, fx.tellerId, "test"));
        assertThrows(IllegalArgumentException.class,
                () -> chequeService.deposit(fx.accountId, "CHQ-NEG", new BigDecimal("-10.00"), fx.tellerId, "test"));
    }

    @Test
    void blankChequeNumber_isRejected_regressionTest() throws Exception {
        // Previously only enforced by ChequePanel's own empty-string check, not the service --
        // any other caller could queue a cheque with no number at all.
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));

        assertThrows(IllegalArgumentException.class,
                () -> chequeService.deposit(fx.accountId, "  ", new BigDecimal("10.00"), fx.tellerId, "test"));
        assertThrows(IllegalArgumentException.class,
                () -> chequeService.deposit(fx.accountId, null, new BigDecimal("10.00"), fx.tellerId, "test"));
    }

    @Test
    void closedAccount_cannotHaveAChequeDeposited_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        TestDatabase.setAccountStatus(fx.accountId, "CLOSED");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> chequeService.deposit(fx.accountId, "CHQ-CLOSED", new BigDecimal("50.00"), fx.tellerId, "test"));
        assertTrue(ex.getMessage().contains("closed"), "Message should explain why: " + ex.getMessage());
        assertTrue(chequeService.pendingCheques().stream().noneMatch(c -> "CHQ-CLOSED".equals(c.getChequeNo())));
    }

    @Test
    void dormantAccount_canStillHaveAChequeDeposited_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        TestDatabase.setAccountStatus(fx.accountId, "DORMANT");

        Cheque cheque = chequeService.deposit(fx.accountId, "CHQ-DORMANT", new BigDecimal("50.00"), fx.tellerId, "Reactivating");

        assertEquals("PENDING", cheque.getStatus());
    }

    @Test
    void depositingTheSameChequeNumberTwice_whileTheFirstIsStillActive_isRejected_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        chequeService.deposit(fx.accountId, "CHQ-DUP", new BigDecimal("50.00"), fx.tellerId, "first deposit");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> chequeService.deposit(fx.accountId, "CHQ-DUP", new BigDecimal("50.00"), fx.tellerId, "second deposit"));
        assertTrue(ex.getMessage().contains("CHQ-DUP"), "Message should name the cheque: " + ex.getMessage());
    }

    @Test
    void aBouncedCheque_canBeRedeposited() throws Exception {
        // A genuinely re-presented cheque is a normal, legitimate flow -- only PENDING/CLEARED
        // should block a re-deposit of the same number, not BOUNCED.
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        Cheque first = chequeService.deposit(fx.accountId, "CHQ-REDEPOSIT", new BigDecimal("50.00"), fx.tellerId, "first");
        chequeService.bounce(first.getId(), fx.tellerId);

        Cheque second = chequeService.deposit(fx.accountId, "CHQ-REDEPOSIT", new BigDecimal("50.00"), fx.tellerId, "redeposit");

        assertEquals("PENDING", second.getStatus());
        assertNotEquals(first.getId(), second.getId());
    }

    // ------------------------------------------------------------------
    // Validation -- clear() / bounce()
    // ------------------------------------------------------------------

    @Test
    void clearingAnAlreadyClearedCheque_isRejected() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        Cheque cheque = chequeService.deposit(fx.accountId, "CHQ-DOUBLE-CLEAR", new BigDecimal("50.00"), fx.tellerId, "test");
        chequeService.clear(cheque.getId(), fx.tellerId);

        assertThrows(IllegalStateException.class, () -> chequeService.clear(cheque.getId(), fx.tellerId));
        // Balance must not be credited a second time.
        assertEquals(0, new BigDecimal("150.00").compareTo(TestDatabase.balanceOf(fx.accountId)));
    }

    @Test
    void clearingACheque_whoseAccountWasClosedAfterQueuing_isRejectedAndCantBeDoubleProcessed_regressionTest() throws Exception {
        // Mirrors the ApprovalService "closed after queuing" regression test for Teller Counter --
        // the cheque must stay safely PENDING, not silently succeed and not get stuck in limbo.
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        Cheque cheque = chequeService.deposit(fx.accountId, "CHQ-CLOSED-LATER", new BigDecimal("50.00"), fx.tellerId, "test");

        TestDatabase.setAccountStatus(fx.accountId, "CLOSED");

        assertThrows(IllegalArgumentException.class, () -> chequeService.clear(cheque.getId(), fx.tellerId));

        assertEquals(0, new BigDecimal("100.00").compareTo(TestDatabase.balanceOf(fx.accountId)), "No funds should have moved");
        assertTrue(chequeService.pendingCheques().stream().anyMatch(c -> c.getId() == cheque.getId()),
                "The cheque must still be sitting PENDING, not stuck or silently cleared");
    }

    @Test
    void bouncingAnAlreadyResolvedCheque_isRejected() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        Cheque cheque = chequeService.deposit(fx.accountId, "CHQ-DOUBLE-BOUNCE", new BigDecimal("50.00"), fx.tellerId, "test");
        chequeService.bounce(cheque.getId(), fx.tellerId);

        assertThrows(IllegalStateException.class, () -> chequeService.bounce(cheque.getId(), fx.tellerId));
    }

    @Test
    void clearingAnUnknownCheque_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> chequeService.clear(999_999, 1));
    }
}
