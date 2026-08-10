package com.branchteller.service;

import com.branchteller.config.DBConnection;
import com.branchteller.dao.CashDrawerDAO;
import com.branchteller.model.CashDrawerLog;
import com.branchteller.model.User;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

/**
 * Cash drawer operations -- paid-in/paid-out/cash-pull/no-sale/till-count. These don't
 * touch account balances (that's BankingService); they're a standalone log of physical
 * cash movement in and out of a teller's drawer, same as the POS project's drawer functions.
 */
public class CashDrawerService {

    private final CashDrawerDAO drawerDAO = new CashDrawerDAO();

    /** The only actions the {@code cash_drawer_logs.action} ENUM column in the real schema
     *  accepts. Mirrored here so bad input gets rejected with a clear message before it ever
     *  reaches the database. */
    private static final Set<String> VALID_ACTIONS =
            Set.of("PAID_IN", "PAID_OUT", "CASH_PULL", "NO_SALE", "TILL_COUNT");

    public CashDrawerLog record(User teller, String action, BigDecimal amount, String note) throws SQLException {
        validate(action, amount);
        try (Connection conn = DBConnection.getConnection()) {
            CashDrawerLog log = new CashDrawerLog(teller.getId(), teller.getBranchId(), action, amount, note);
            int id = drawerDAO.insert(conn, log);
            log.setId(id);
            return log;
        }
    }

    /**
     * QA finding (fixed): this method used to insert whatever the caller passed straight into
     * the database with zero validation. A negative PAID_IN, a $0.00 PAID_OUT, or a completely
     * made-up action string would all have been accepted by this service layer -- the only thing
     * stopping a bad action string was the database's own ENUM column rejecting it at insert
     * time, which surfaces as a raw, confusing {@code SQLException} instead of a clear message,
     * and doesn't stop backwards-signed or nonsensical amounts at all (the ENUM has no opinion on
     * {@code amount}). {@code DrawerPanel}'s combo box only ever offers the five known actions
     * with a plain BigDecimal amount field, so this gap was invisible from the GUI alone --
     * it only shows up when the service itself is called directly, e.g. by a test, or by any
     * future caller such as a REST endpoint.
     *
     * <p>This log exists specifically "for reconciliation at end of shift" (see the Cash Drawer
     * Help topic) -- a teller and their manager are meant to be able to trust it as an honest
     * record of physical cash movement. Letting garbage or backwards-signed entries into it
     * silently defeats that whole purpose. Enforces:</p>
     * <ul>
     * <li>{@code action} must be one of the five known values.</li>
     * <li>{@code amount} must not be null, and can never be negative -- there's no such thing as
     * a negative amount of physical cash.</li>
     * <li>{@code NO_SALE} isn't a cash movement at all (it just logs that the drawer was opened
     * with no transaction), so its amount must be exactly zero.</li>
     * <li>{@code PAID_IN}, {@code PAID_OUT}, and {@code CASH_PULL} are real movements, so a zero
     * amount is rejected as not actually being one -- the same "zero isn't a real movement"
     * standard {@link BankingService} already enforces for deposit/withdraw/transfer.</li>
     * <li>{@code TILL_COUNT} is a point-in-time count of the whole drawer, not a movement, so
     * zero is a legitimate value (e.g. a drawer counted out and handed back empty at end of
     * shift) -- only negative is rejected for it.</li>
     * </ul>
     */
    private void validate(String action, BigDecimal amount) {
        if (action == null || !VALID_ACTIONS.contains(action)) {
            throw new IllegalArgumentException("Unknown cash drawer action: " + action);
        }
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Amount can't be negative");
        }
        if ("NO_SALE".equals(action) && amount.signum() != 0) {
            throw new IllegalArgumentException("NO_SALE isn't a cash movement -- amount must be 0.00");
        }
        if (amount.signum() == 0
                && ("PAID_IN".equals(action) || "PAID_OUT".equals(action) || "CASH_PULL".equals(action))) {
            throw new IllegalArgumentException(action + " must have an amount greater than zero");
        }
    }

    public List<CashDrawerLog> recentActivity(User teller, int limit) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return drawerDAO.findRecentByTeller(conn, teller.getId(), limit);
        }
    }
}
