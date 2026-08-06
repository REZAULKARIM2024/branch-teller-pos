package com.branchteller.service;

import com.branchteller.config.DBConnection;
import com.branchteller.dao.CardDAO;
import com.branchteller.model.Card;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class CardService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final CardDAO cardDAO = new CardDAO();
    private final AuditService auditService = new AuditService();

    public Card issue(int accountId, String cardType, String cardholderName, BigDecimal creditLimit, int issuedBy) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            Card c = new Card();
            c.setAccountId(accountId);
            c.setCardNumber(generateCardNumber());
            c.setCardType(cardType);
            c.setCardholderName(cardholderName);
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

    public void block(int cardId, int actorId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            cardDAO.updateStatus(conn, cardId, "BLOCKED");
            auditService.log(conn, actorId, "CARD_BLOCKED", "card", cardId, "ACTIVE", "BLOCKED");
        }
    }

    public void unblock(int cardId, int actorId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            cardDAO.updateStatus(conn, cardId, "ACTIVE");
            auditService.log(conn, actorId, "CARD_UNBLOCKED", "card", cardId, "BLOCKED", "ACTIVE");
        }
    }

    public void cancel(int cardId, int actorId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            cardDAO.updateStatus(conn, cardId, "CANCELLED");
            auditService.log(conn, actorId, "CARD_CANCELLED", "card", cardId, null, "CANCELLED");
        }
    }

    /** Simulated PIN reset -- no real PIN is stored; this just logs the security event. */
    public String resetPin(int cardId, int actorId) throws SQLException {
        String newPin = String.format("%04d", RANDOM.nextInt(10000));
        try (Connection conn = DBConnection.getConnection()) {
            auditService.log(conn, actorId, "CARD_PIN_RESET", "card", cardId, null, "PIN reset issued to cardholder");
        }
        return newPin;
    }

    public void setLimits(int cardId, BigDecimal dailyLimit, BigDecimal creditLimit) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
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

    private String generateCardNumber() {
        StringBuilder sb = new StringBuilder("4519");
        for (int i = 0; i < 12; i++) sb.append(RANDOM.nextInt(10));
        return sb.toString();
    }
}
