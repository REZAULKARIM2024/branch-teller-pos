package com.branchteller.service;

import com.branchteller.config.DBConnection;
import com.branchteller.dao.HoldDAO;
import com.branchteller.model.AccountHold;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Account holds/liens. Available balance for withdrawals/transfers = ledger balance minus
 * the sum of all ACTIVE holds on the account (court order, fraud investigation, uncleared
 * cheque, etc). BankingService consults activeHoldsTotal() before releasing funds.
 */
public class HoldService {

    private final HoldDAO holdDAO = new HoldDAO();

    public AccountHold placeHold(int accountId, BigDecimal amount, String reason, int placedBy) throws SQLException {
        if (amount.signum() <= 0) throw new IllegalArgumentException("Hold amount must be positive");
        try (Connection conn = DBConnection.getConnection()) {
            AccountHold h = new AccountHold();
            h.setAccountId(accountId);
            h.setAmount(amount);
            h.setReason(reason);
            h.setPlacedBy(placedBy);
            int id = holdDAO.insert(conn, h);
            h.setId(id);
            h.setStatus("ACTIVE");
            return h;
        }
    }

    public void releaseHold(int holdId, int releasedBy) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            holdDAO.release(conn, holdId, releasedBy);
        }
    }

    public BigDecimal activeHoldsTotal(Connection conn, int accountId) throws SQLException {
        return holdDAO.activeHoldsTotal(conn, accountId);
    }

    public BigDecimal activeHoldsTotal(int accountId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return holdDAO.activeHoldsTotal(conn, accountId);
        }
    }

    public List<AccountHold> activeHolds() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return holdDAO.findActive(conn);
        }
    }

    public List<AccountHold> byAccount(int accountId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return holdDAO.findByAccount(conn, accountId);
        }
    }
}
