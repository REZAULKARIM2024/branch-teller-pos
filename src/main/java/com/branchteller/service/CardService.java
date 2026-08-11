package com.branchteller.service;

import com.branchteller.config.DBConnection;
import com.branchteller.dao.AccountDAO;
import com.branchteller.dao.CardDAO;
import com.branchteller.model.Account;
import com.branchteller.model.Card;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public class CardService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> VALID_CARD_TYPES = Set.of("DEBIT", "CREDIT");

    private final CardDAO cardDAO = new CardDAO();
    private final AccountDAO accountDAO = new AccountDAO();
    private final AuditService auditService = new AuditService();

    /**
     * QA finding (fixed): this method used to accept any accountId and any cardType string at
     * all -- a bad account only failed with a raw foreign-key SQLException instead of a clear
     * message, and an invalid cardType (anything other than DEBIT/CREDIT) was never rejected by
     * the app itself -- only by production MySQL's ENUM constraint, and not at all against this
     * project's H2 test schema, which mirrors that column as a plain VARCHAR. A blank cardholder
     * name and a negative credit limit were also both accepted outright. All four are now
     * rejected up front with a clear message.
     *
     * <p>Also newly rejects issuing a card against a CLOSED account, the same "CLOSED blocks,
     * DORMANT doesn't" rule already applied to Teller Counter/Cheques/Loans -- unlike a Hold
     * (which only restricts money that's already there), a card is a new instrument that enables
     * future spending or withdrawal, so it belongs with the money-moving features here rather
     * than with Holds' deliberate exception.</p>
     */
    public Card issue(int accountId, String cardType, String cardholderName, BigDecimal creditLimit, int issuedBy) throws SQLException {
        if (cardType == null || !VALID_CARD_TYPES.contains(cardType)) {
            throw new IllegalArgumentException("Card type must be DEBIT or CREDIT, got: " + cardType);
        }
        if (cardholderName == null || cardholderName.isBlank()) {
            throw new IllegalArgumentException("Cardholder name is required");
        }
        if (creditLimit != null && creditLimit.signum() < 0) {
            throw new IllegalArgumentException("Credit limit can't be negative");
        }

        try (Connection conn = DBConnection.getConnection()) {
            Account acct = accountDAO.findByIdForUpdate(conn, accountId)
                    .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
            if ("CLOSED".equals(acct.getStatus())) {
                throw new IllegalArgumentException(
                        "Account " + acct.getAccountNumber() + " is closed and cannot have a card issued against it");
            }

            Card c = new Card();
            c.setAccountId(accountId);
            c.setCardNumber(generateCardNumber());
            c.setCardType(cardType);
            c.setCardholderName(cardholderName.trim());
            c.setExpiryDate(LocalDate.now().plusYears(4));
            c.setCreditLimit("CREDIT".equals(cardType) ? (creditLimit == null ? BigDecimal.valueOf(5000) : creditLimit) : null);
            c.setDailyLimit(BigDecimal.valueOf(1000));
            c.setIssuedDate(LocalDate.now());
            int id = cardDAO.insert(conn, c);
            c.setId(id);
            c.setStatus("ACTIVE");
            auditService.log(conn, issuedBy, "CARD_ISSUED", "card", id, null, cardType + " card for account " + accountId);
            return c;
        }
    }

    /**
     * QA finding (fixed): block()/unblock()/cancel() used to call {@code updateStatus()}
     * completely unconditionally -- no check the card even existed (silently updating zero rows
     * with no error), and no state-machine guard at all. Worst of the three: {@code unblock()}
     * would happily flip a CANCELLED card back to ACTIVE, effectively un-cancelling what's
     * supposed to be a permanent, terminal state -- a real security gap for a lost/stolen-card
     * workflow. Fixed with the same exists-and-in-expected-state guard used elsewhere in this
     * codebase (ApprovalService/ChequeService/LoanService/HoldService): {@code block()} only from
     * ACTIVE, {@code unblock()} only from BLOCKED, {@code cancel()} from either ACTIVE or BLOCKED
     * but never from an already-CANCELLED card.
     */
    public void block(int cardId, int actorId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            Card card = requireCard(conn, cardId);
            if (!"ACTIVE".equals(card.getStatus())) {
                throw new IllegalStateException("Card " + cardId + " can't be blocked (current status: " + card.getStatus() + ")");
            }
            cardDAO.updateStatus(conn, cardId, "BLOCKED");
            auditService.log(conn, actorId, "CARD_BLOCKED", "card", cardId, "ACTIVE", "BLOCKED");
        }
    }

    public void unblock(int cardId, int actorId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            Card card = requireCard(conn, cardId);
            if (!"BLOCKED".equals(card.getStatus())) {
                throw new IllegalStateException("Card " + cardId + " can't be unblocked (current status: " + card.getStatus() + ")");
            }
            cardDAO.updateStatus(conn, cardId, "ACTIVE");
            auditService.log(conn, actorId, "CARD_UNBLOCKED", "card", cardId, "BLOCKED", "ACTIVE");
        }
    }

    public void cancel(int cardId, int actorId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            Card card = requireCard(conn, cardId);
            if ("CANCELLED".equals(card.getStatus())) {
                throw new IllegalStateException("Card " + cardId + " is already cancelled");
            }
            String previousStatus = card.getStatus();
            cardDAO.updateStatus(conn, cardId, "CANCELLED");
            auditService.log(conn, actorId, "CARD_CANCELLED", "card", cardId, previousStatus, "CANCELLED");
        }
    }

    /**
     * Simulated PIN reset -- no real PIN is stored; this just logs the security event.
     *
     * <p>QA finding (fixed): used to reset a PIN for any cardId at all, including one that
     * didn't exist or was already CANCELLED -- generating and "issuing" a working PIN for a card
     * that either doesn't exist or was permanently terminated. Fixed to require the card exist
     * and not be CANCELLED (a BLOCKED card can still have its PIN reset, since blocking is
     * reversible and the cardholder may get unblocked later).</p>
     */
    public String resetPin(int cardId, int actorId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            Card card = requireCard(conn, cardId);
            if ("CANCELLED".equals(card.getStatus())) {
                throw new IllegalStateException("Card " + cardId + " is cancelled and can't have its PIN reset");
            }
            String newPin = String.format("%04d", RANDOM.nextInt(10000));
            auditService.log(conn, actorId, "CARD_PIN_RESET", "card", cardId, null, "PIN reset issued to cardholder");
            return newPin;
        }
    }

    /**
     * QA finding (fixed): previously accepted any dailyLimit/creditLimit at all, including
     * negative values, and never checked the card existed. Not currently wired to a GUI button,
     * but it's a public method other callers (a future REST endpoint, for instance) could reach
     * directly, so it's validated the same as everything else here.
     */
    public void setLimits(int cardId, BigDecimal dailyLimit, BigDecimal creditLimit) throws SQLException {
        if (dailyLimit == null || dailyLimit.signum() < 0) {
            throw new IllegalArgumentException("Daily limit can't be negative");
        }
        if (creditLimit != null && creditLimit.signum() < 0) {
            throw new IllegalArgumentException("Credit limit can't be negative");
        }
        try (Connection conn = DBConnection.getConnection()) {
            requireCard(conn, cardId);
            cardDAO.updateLimits(conn, cardId, dailyLimit, creditLimit);
        }
    }

    public List<Card> all() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return cardDAO.findAll(conn);
        }
    }

    public List<Card> byAccount(int accountId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return cardDAO.findByAccount(conn, accountId);
        }
    }

    private Card requireCard(Connection conn, int cardId) throws SQLException {
        return cardDAO.findById(conn, cardId)
                .orElseThrow(() -> new IllegalArgumentException("Card not found: " + cardId));
    }

    private String generateCardNumber() {
        StringBuilder sb = new StringBuilder("4519");
        for (int i = 0; i < 12; i++) sb.append(RANDOM.nextInt(10));
        return sb.toString();
    }
}
