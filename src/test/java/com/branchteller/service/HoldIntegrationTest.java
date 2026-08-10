package com.branchteller.service;

import com.branchteller.model.AccountHold;
import com.branchteller.model.AuditLog;
import com.branchteller.support.TestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Senior-QA-style integration coverage for the Holds feature (HoldService, backing the Holds
 * tab). Prior coverage was limited to one happy-path scenario buried inside {@code
 * EndToEndFlowTest} (place a hold, confirm it blocks a withdrawal, release it) -- no negative
 * cases, no state-guard cases, and no audit-trail check existed before this review.
 *
 * <p>Findings, all fixed:</p>
 * <ol>
 * <li>{@link HoldService#placeHold} never looked the account up, so a bad account ID only failed
 * with a raw foreign-key SQLException, and never validated {@code reason}, so a blank reason was
 * accepted for what's supposed to be a compliance-significant restriction on customer funds.</li>
 * <li>{@link HoldService#releaseHold} had no existence or status guard at all -- releasing an
 * unknown hold silently updated zero rows with no error, and releasing an already-released hold
 * silently overwrote the audit-relevant {@code released_at}/{@code released_by} fields a second
 * time. Fixed with the same exists-and-still-ACTIVE guard used elsewhere in this codebase.</li>
 * <li>Neither method wrote an audit trail entry at all, despite holds existing specifically for
 * "court order, fraud investigation" style compliance actions.</li>
 * </ol>
 *
 * <p>Deliberately NOT testing a "CLOSED account rejected" case here, unlike Teller
 * Counter/Cheques/Loans -- see {@link HoldService#placeHold}'s javadoc for why a hold
 * intentionally still works against a CLOSED account.</p>
 */
class HoldIntegrationTest {

    private final HoldService holdService = new HoldService();
    private final AuditService auditService = new AuditService();

    @BeforeAll
    static void setUpSchema() throws Exception {
        TestDatabase.ensureSchema();
    }

    // ------------------------------------------------------------------
    // Happy path
    // ------------------------------------------------------------------

    @Test
    void placeThenRelease_writesAuditTrailForBoth() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("1000.00"));

        AccountHold hold = holdService.placeHold(fx.accountId, new BigDecimal("300.00"), "Fraud investigation", fx.tellerId);
        assertEquals("ACTIVE", hold.getStatus());
        assertEquals(0, new BigDecimal("300.00").compareTo(holdService.activeHoldsTotal(fx.accountId)));

        holdService.releaseHold(hold.getId(), fx.tellerId);
        assertEquals(0, BigDecimal.ZERO.compareTo(holdService.activeHoldsTotal(fx.accountId)));

        List<AuditLog> logs = auditService.byEntityType("account_hold", 500);
        assertTrue(logs.stream().anyMatch(l -> l.getEntityId() == hold.getId() && "HOLD_PLACED".equals(l.getAction())));
        assertTrue(logs.stream().anyMatch(l -> l.getEntityId() == hold.getId() && "HOLD_RELEASED".equals(l.getAction())));
    }

    @Test
    void multipleActiveHolds_sumTogether() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("1000.00"));

        holdService.placeHold(fx.accountId, new BigDecimal("100.00"), "Uncleared cheque", fx.tellerId);
        holdService.placeHold(fx.accountId, new BigDecimal("250.00"), "Court order", fx.tellerId);

        assertEquals(0, new BigDecimal("350.00").compareTo(holdService.activeHoldsTotal(fx.accountId)));
    }

    // ------------------------------------------------------------------
    // placeHold() validation
    // ------------------------------------------------------------------

    @Test
    void nonPositiveAmount_isRejected() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));

        assertThrows(IllegalArgumentException.class,
                () -> holdService.placeHold(fx.accountId, BigDecimal.ZERO, "test", fx.tellerId));
        assertThrows(IllegalArgumentException.class,
                () -> holdService.placeHold(fx.accountId, new BigDecimal("-50.00"), "test", fx.tellerId));
    }

    @Test
    void blankReason_isRejected_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));

        assertThrows(IllegalArgumentException.class,
                () -> holdService.placeHold(fx.accountId, new BigDecimal("50.00"), "   ", fx.tellerId));
        assertThrows(IllegalArgumentException.class,
                () -> holdService.placeHold(fx.accountId, new BigDecimal("50.00"), null, fx.tellerId));
    }

    @Test
    void unknownAccount_isRejectedWithAClearMessage_regressionTest() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> holdService.placeHold(999_999, new BigDecimal("50.00"), "test", 1));
        assertTrue(ex.getMessage().contains("not found"), "Message should explain why: " + ex.getMessage());
    }

    @Test
    void closedAccount_canStillHaveAHoldPlaced_regressionTest() throws Exception {
        // Deliberately the opposite of Teller Counter/Cheques/Loans -- see placeHold()'s javadoc.
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        TestDatabase.setAccountStatus(fx.accountId, "CLOSED");

        AccountHold hold = holdService.placeHold(fx.accountId, new BigDecimal("50.00"), "Fraud investigation after closure", fx.tellerId);

        assertEquals("ACTIVE", hold.getStatus());
    }

    // ------------------------------------------------------------------
    // releaseHold() -- the state-guard finding
    // ------------------------------------------------------------------

    @Test
    void releasingAnUnknownHold_isRejected_regressionTest() {
        assertThrows(IllegalArgumentException.class, () -> holdService.releaseHold(999_999, 1));
    }

    @Test
    void releasingAnAlreadyReleasedHold_isRejected_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        AccountHold hold = holdService.placeHold(fx.accountId, new BigDecimal("50.00"), "test", fx.tellerId);
        holdService.releaseHold(hold.getId(), fx.tellerId);

        assertThrows(IllegalStateException.class, () -> holdService.releaseHold(hold.getId(), fx.tellerId));
    }

    @Test
    void releasingOneHold_doesNotAffectAnotherActiveHoldOnTheSameAccount() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("1000.00"));
        AccountHold holdA = holdService.placeHold(fx.accountId, new BigDecimal("100.00"), "A", fx.tellerId);
        AccountHold holdB = holdService.placeHold(fx.accountId, new BigDecimal("200.00"), "B", fx.tellerId);

        holdService.releaseHold(holdA.getId(), fx.tellerId);

        assertEquals(0, new BigDecimal("200.00").compareTo(holdService.activeHoldsTotal(fx.accountId)));
        List<AccountHold> byAccount = holdService.byAccount(fx.accountId);
        assertTrue(byAccount.stream().anyMatch(h -> h.getId() == holdB.getId() && "ACTIVE".equals(h.getStatus())));
        assertTrue(byAccount.stream().anyMatch(h -> h.getId() == holdA.getId() && "RELEASED".equals(h.getStatus())));
    }
}
