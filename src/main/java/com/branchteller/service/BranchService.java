package com.branchteller.service;

import com.branchteller.config.DBConnection;
import com.branchteller.dao.BranchDAO;
import com.branchteller.model.Branch;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class BranchService {

    private final BranchDAO branchDAO = new BranchDAO();
    private final AuditService auditService = new AuditService();

    public List<Branch> allWithStats() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return branchDAO.findAllWithStats(conn);
        }
    }

    /**
     * QA findings (fixed): this had three gaps compared to every other "create" action in the
     * codebase (hire(), apply() for loans, register() for customers, etc.):
     *
     * <p>1. Zero server-side validation -- a blank/null name or routing code was silently
     * accepted and written straight to the branches table. The GUI's own dialog checked for
     * blank fields, but that was the *only* guard; since BranchService has no REST endpoint
     * today, this was latent rather than exploitable, but it's exactly the same
     * GUI-is-the-only-gate pattern that let a negative hourly rate through PayrollService.hire()
     * before that was fixed -- any future caller (a script, a test, a future REST endpoint)
     * would have bypassed validation entirely.
     *
     * <p>2. A duplicate routing code was not handled: routing_code has a UNIQUE constraint at
     * the DB level, so opening a second branch with an already-used code threw a raw
     * SQLException with a driver-specific constraint-violation message straight up to the
     * generic "Database error: ..." dialog -- confusing for an Admin who just needs to know
     * "that code's taken, pick another." Checking first and raising a clear
     * IllegalArgumentException lets the GUI show a friendly message instead.
     *
     * <p>3. No audit trail entry. Opening a branch is exactly the kind of administratively
     * significant, infrequent action (like hiring an employee or filing a SAR) that this
     * codebase logs everywhere else -- but AuditLogPanel's entity-type filter never even had a
     * "branch" option, because nothing ever logged one. Fixed by logging BRANCH_OPENED here and
     * adding "branch" to the filter dropdown, and wrapping the insert + audit log in a
     * transaction (same setAutoCommit(false)/commit()/rollback() pattern as every other
     * multi-write service method) so a failed audit write can't leave an unlogged branch behind.
     */
    public Branch openBranch(String name, String address, String routingCode, Integer actorId) throws SQLException {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Branch name is required");
        }
        if (routingCode == null || routingCode.isBlank()) {
            throw new IllegalArgumentException("Routing code is required");
        }

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (branchDAO.routingCodeExists(conn, routingCode)) {
                    throw new IllegalArgumentException("Routing code already in use: " + routingCode);
                }

                Branch b = new Branch();
                b.setName(name);
                b.setAddress(address);
                b.setRoutingCode(routingCode);
                int id = branchDAO.insert(conn, b);
                b.setId(id);

                auditService.log(conn, actorId, "BRANCH_OPENED", "branch", id, null,
                        name + " (" + routingCode + ")");

                conn.commit();
                return b;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
}
