package com.branchteller.service;

import com.branchteller.config.DBConnection;
import com.branchteller.dao.CashDrawerDAO;
import com.branchteller.model.CashDrawerLog;
import com.branchteller.model.User;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Cash drawer operations -- paid-in/paid-out/cash-pull/no-sale/till-count. These don't
 * touch account balances (that's BankingService); they're a standalone log of physical
 * cash movement in and out of a teller's drawer, same as the POS project's drawer functions.
 */
public class CashDrawerService {

    private final CashDrawerDAO drawerDAO = new CashDrawerDAO();

    public CashDrawerLog record(User teller, String action, BigDecimal amount, String note) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            CashDrawerLog log = new CashDrawerLog(teller.getId(), teller.getBranchId(), action, amount, note);
            int id = drawerDAO.insert(conn, log);
            log.setId(id);
            return log;
        }
    }

    public List<CashDrawerLog> recentActivity(User teller, int limit) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return drawerDAO.findRecentByTeller(conn, teller.getId(), limit);
        }
    }
}
