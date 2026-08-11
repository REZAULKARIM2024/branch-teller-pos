package com.branchteller.service;

import com.branchteller.model.AuditLog;
import com.branchteller.model.Card;
import com.branchteller.support.TestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Senior-QA-style integration coverage for the Cards feature (CardService, backing the Card
 * Management tab). No dedicated test class existed for this feature before this review, and the
 * H2 test schema didn't even have a {@code cards} table -- meaning Cards could never have been
 * integration-tested against a real (if in-memory) database at all.
 *
 * <p>Findings, all fixed:</p>
 * <ol>
 * <li>{@link CardService#issue} accepted any accountId (raw FK SQLException on a bad one instead
 * of a clear message), any cardType string at all (never validated against DEBIT/CREDIT), a
 * blank cardholder name, and a negative credit limit. It also never blocked issuing a card
 * against a CLOSED account -- unlike a Hold, a card enables future spending, so it's treated like
 * Loans/Cheques here rather than like Holds' deliberate exception.</li>
 * <li>{@link CardService#block}/{@link CardService#unblock}/{@link CardService#cancel} had no
 * existence check and no state-machine guard at all. Worst of the three: {@code unblock()} would
 * flip a CANCELLED card back to ACTIVE -- un-cancelling what's supposed to be a permanent,
 * terminal state.</li>
 * <li>{@link CardService#resetPin} would issue a "working" PIN for a nonexistent or CANCELLED
 * card.</li>
 * <li>{@link CardService#setLimits} accepted negative limits with no existence check.</li>
 * </ol>
 */
class CardIntegrationTest {

    private final CardService cardService = new CardService();
    private final AuditService auditService = new AuditService();

    @BeforeAll
    static void setUpSchema() throws Exception {
        TestDatabase.ensureSchema();
    }

    // ------------------------------------------------------------------
    // issue() happy path + audit trail
    // ------------------------------------------------------------------

    @Test
    void issueDebitCard_hasNoCreditLimitAndIsAudited() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));

        Card c = cardService.issue(fx.accountId, "DEBIT", "Jane Doe", null, fx.tellerId);

        assertEquals("ACTIVE", c.getStatus());
        assertNull(c.getCreditLimit());
        assertEquals(16, c.getCardNumber().length());

        List<AuditLog> logs = auditService.byEntityType("card", 500);
        assertTrue(logs.stream().anyMatch(l -> l.getEntityId() == c.getId() && "CARD_ISSUED".equals(l.getAction())));
    }

    @Test
    void issueCreditCard_defaultsCreditLimitWhenNotGiven() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));

        Card c = cardService.issue(fx.accountId, "CREDIT", "Jane Doe", null, fx.tellerId);

        assertEquals(0, new BigDecimal("5000").compareTo(c.getCreditLimit()));
    }

    @Test
    void issueCreditCard_honorsExplicitCreditLimit() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));

        Card c = cardService.issue(fx.accountId, "CREDIT", "Jane Doe", new BigDecimal("2500.00"), fx.tellerId);

        assertEquals(0, new BigDecimal("2500.00").compareTo(c.getCreditLimit()));
    }

    // ------------------------------------------------------------------
    // issue() validation
    // ------------------------------------------------------------------

    @Test
    void invalidCardType_isRejected_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));

        assertThrows(IllegalArgumentException.class,
                () -> cardService.issue(fx.accountId, "PREPAID", "Jane Doe", null, fx.tellerId));
        assertThrows(IllegalArgumentException.class,
                () -> cardService.issue(fx.accountId, null, "Jane Doe", null, fx.tellerId));
    }

    @Test
    void blankCardholderName_isRejected_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));

        assertThrows(IllegalArgumentException.class,
                () -> cardService.issue(fx.accountId, "DEBIT", "   ", null, fx.tellerId));
        assertThrows(IllegalArgumentException.class,
                () -> cardService.issue(fx.accountId, "DEBIT", null, null, fx.tellerId));
    }

    @Test
    void negativeCreditLimit_isRejected_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));

        assertThrows(IllegalArgumentException.class,
                () -> cardService.issue(fx.accountId, "CREDIT", "Jane Doe", new BigDecimal("-500.00"), fx.tellerId));
    }

    @Test
    void unknownAccount_isRejectedWithAClearMessage_regressionTest() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> cardService.issue(999_999, "DEBIT", "Jane Doe", null, 1));
        assertTrue(ex.getMessage().contains("not found"), "Message should explain why: " + ex.getMessage());
    }

    @Test
    void closedAccount_cannotHaveACardIssued_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        TestDatabase.setAccountStatus(fx.accountId, "CLOSED");

        assertThrows(IllegalArgumentException.class,
                () -> cardService.issue(fx.accountId, "DEBIT", "Jane Doe", null, fx.tellerId));
    }

    // ------------------------------------------------------------------
    // block() / unblock() / cancel() state machine
    // ------------------------------------------------------------------

    @Test
    void blockingActiveCard_thenUnblocking_worksAndIsAudited() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        Card c = cardService.issue(fx.accountId, "DEBIT", "Jane Doe", null, fx.tellerId);

        cardService.block(c.getId(), fx.tellerId);
        cardService.unblock(c.getId(), fx.tellerId);

        List<Card> cards = cardService.byAccount(fx.accountId);
        assertEquals("ACTIVE", cards.get(0).getStatus());

        List<AuditLog> logs = auditService.byEntityType("card", 500);
        assertTrue(logs.stream().anyMatch(l -> l.getEntityId() == c.getId() && "CARD_BLOCKED".equals(l.getAction())));
        assertTrue(logs.stream().anyMatch(l -> l.getEntityId() == c.getId() && "CARD_UNBLOCKED".equals(l.getAction())));
    }

    @Test
    void blockingAnAlreadyBlockedCard_isRejected_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        Card c = cardService.issue(fx.accountId, "DEBIT", "Jane Doe", null, fx.tellerId);
        cardService.block(c.getId(), fx.tellerId);

        assertThrows(IllegalStateException.class, () -> cardService.block(c.getId(), fx.tellerId));
    }

    @Test
    void unblockingAnActiveCard_isRejected_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        Card c = cardService.issue(fx.accountId, "DEBIT", "Jane Doe", null, fx.tellerId);

        assertThrows(IllegalStateException.class, () -> cardService.unblock(c.getId(), fx.tellerId));
    }

    @Test
    void cannotUnblockACancelledCard_regressionTest() throws Exception {
        // This was the real security bug: unblock() used to flip a CANCELLED card straight back
        // to ACTIVE with no guard at all, effectively un-cancelling a lost/stolen card.
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        Card c = cardService.issue(fx.accountId, "DEBIT", "Jane Doe", null, fx.tellerId);
        cardService.cancel(c.getId(), fx.tellerId);

        assertThrows(IllegalStateException.class, () -> cardService.unblock(c.getId(), fx.tellerId));

        List<Card> cards = cardService.byAccount(fx.accountId);
        assertEquals("CANCELLED", cards.get(0).getStatus());
    }

    @Test
    void cancellingFromActiveOrBlocked_bothWork_butNotTwice() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        Card active = cardService.issue(fx.accountId, "DEBIT", "Jane Doe", null, fx.tellerId);
        Card blocked = cardService.issue(fx.accountId, "DEBIT", "Jane Doe", null, fx.tellerId);
        cardService.block(blocked.getId(), fx.tellerId);

        cardService.cancel(active.getId(), fx.tellerId);
        cardService.cancel(blocked.getId(), fx.tellerId);

        assertThrows(IllegalStateException.class, () -> cardService.cancel(active.getId(), fx.tellerId));
    }

    @Test
    void unknownCard_operationsAreRejectedWithAClearMessage_regressionTest() {
        assertThrows(IllegalArgumentException.class, () -> cardService.block(999_999, 1));
        assertThrows(IllegalArgumentException.class, () -> cardService.unblock(999_999, 1));
        assertThrows(IllegalArgumentException.class, () -> cardService.cancel(999_999, 1));
        assertThrows(IllegalArgumentException.class, () -> cardService.resetPin(999_999, 1));
    }

    // ------------------------------------------------------------------
    // resetPin()
    // ------------------------------------------------------------------

    @Test
    void resetPin_worksForActiveAndBlocked_butNotCancelled_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        Card c = cardService.issue(fx.accountId, "DEBIT", "Jane Doe", null, fx.tellerId);

        String pin1 = cardService.resetPin(c.getId(), fx.tellerId);
        assertEquals(4, pin1.length());

        cardService.block(c.getId(), fx.tellerId);
        String pin2 = cardService.resetPin(c.getId(), fx.tellerId);
        assertEquals(4, pin2.length());

        cardService.unblock(c.getId(), fx.tellerId);
        cardService.cancel(c.getId(), fx.tellerId);
        assertThrows(IllegalStateException.class, () -> cardService.resetPin(c.getId(), fx.tellerId));
    }

    // ------------------------------------------------------------------
    // setLimits()
    // ------------------------------------------------------------------

    @Test
    void setLimits_rejectsNegativeValues_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        Card c = cardService.issue(fx.accountId, "DEBIT", "Jane Doe", null, fx.tellerId);

        assertThrows(IllegalArgumentException.class,
                () -> cardService.setLimits(c.getId(), new BigDecimal("-1.00"), null));
        assertThrows(IllegalArgumentException.class,
                () -> cardService.setLimits(c.getId(), new BigDecimal("500.00"), new BigDecimal("-1.00")));
    }

    @Test
    void setLimits_updatesValuesForAnExistingCard() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        Card c = cardService.issue(fx.accountId, "CREDIT", "Jane Doe", null, fx.tellerId);

        cardService.setLimits(c.getId(), new BigDecimal("2000.00"), new BigDecimal("10000.00"));

        Card updated = cardService.byAccount(fx.accountId).get(0);
        assertEquals(0, new BigDecimal("2000.00").compareTo(updated.getDailyLimit()));
        assertEquals(0, new BigDecimal("10000.00").compareTo(updated.getCreditLimit()));
    }
}
