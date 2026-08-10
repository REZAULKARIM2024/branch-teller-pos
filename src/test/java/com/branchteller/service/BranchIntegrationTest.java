package com.branchteller.service;

import com.branchteller.model.AuditLog;
import com.branchteller.model.Branch;
import com.branchteller.support.TestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Senior-QA-style integration coverage for the Branches feature (BranchService.openBranch() /
 * allWithStats(), shown on the Branches tab), which had ZERO existing tests before this class.
 *
 * <p>This review found three real defects in {@link BranchService#openBranch}, all now fixed:
 *
 * <p>1. No server-side validation at all -- a blank/null name or routing code was silently
 * accepted and written straight to the branches table. The GUI's own dialog checked for blank
 * fields, but that was the *only* guard, exactly the same "GUI is the only gate" pattern that
 * let a negative hourly rate through {@code PayrollService.hire()} before that was fixed.
 * {@link #openBranch_withBlankName_throwsIllegalArgumentException()} and friends guard this.
 *
 * <p>2. A duplicate routing code (the {@code routing_code} column has a UNIQUE constraint) threw
 * a raw, driver-specific constraint-violation SQLException straight up to the GUI's generic
 * "Database error: ..." dialog instead of a clear message. {@link
 * #openBranch_withDuplicateRoutingCode_throwsIllegalArgumentException_regressionTest()} guards
 * this and also proves the failed attempt doesn't leave a duplicate row behind.
 *
 * <p>3. Opening a branch -- an administratively significant, infrequent action, exactly like
 * hiring an employee or filing a SAR -- never wrote an audit trail entry. Every other "create"
 * action in this codebase does. {@link #openBranch_writesAuditTrailEntry_regressionTest()} guards
 * this now that {@code BRANCH_OPENED} is logged.
 */
class BranchIntegrationTest {

    private final BranchService branchService = new BranchService();
    private final AuditService auditService = new AuditService();

    @BeforeAll
    static void setUpSchema() throws Exception {
        TestDatabase.ensureSchema();
    }

    // ------------------------------------------------------------------
    // openBranch -- happy path
    // ------------------------------------------------------------------

    @Test
    void openBranch_persistsAllFieldsAndReturnsTheGeneratedId() throws Exception {
        int managerId = TestDatabase.insertUser("branchmgr", "ADMIN");
        String routingCode = "RT" + TestDatabase.nextSeq();

        Branch created = branchService.openBranch("Gulshan Branch", "House 12, Road 5", routingCode, managerId);

        assertTrue(created.getId() > 0, "A generated branch ID should be returned");
        assertEquals("Gulshan Branch", created.getName());
        assertEquals("House 12, Road 5", created.getAddress());
        assertEquals(routingCode, created.getRoutingCode());

        Branch found = branchService.allWithStats().stream()
                .filter(b -> b.getId() == created.getId()).findFirst().orElseThrow();
        assertEquals("Gulshan Branch", found.getName());
        assertEquals(routingCode, found.getRoutingCode());
        assertEquals(0, found.getAccountCount(), "A brand-new branch should start with zero accounts");
        assertEquals(0, found.getEmployeeCount(), "A brand-new branch should start with zero staff");
        assertEquals(0, BigDecimal.ZERO.compareTo(found.getTotalDeposits()),
                "A brand-new branch should start with zero total deposits (not null)");
    }

    @Test
    void openBranch_withNullActorId_stillPersistsAndLogsANullActor() throws Exception {
        // Mirrors LoanService.apply()'s "actor may be null" contract -- opening a branch
        // shouldn't NPE just because no logged-in user is available to attribute it to.
        String routingCode = "RT" + TestDatabase.nextSeq();
        Branch created = branchService.openBranch("System Branch", "N/A", routingCode, null);

        List<AuditLog> logs = auditService.byEntityType("branch", 500);
        AuditLog entry = logs.stream().filter(l -> l.getEntityId() == created.getId()).findFirst().orElseThrow();
        assertNull(entry.getActorId());
    }

    // ------------------------------------------------------------------
    // openBranch -- validation (regression tests for the missing-validation gap)
    // ------------------------------------------------------------------

    @Test
    void openBranch_withBlankName_throwsIllegalArgumentException_regressionTest() {
        assertThrows(IllegalArgumentException.class,
                () -> branchService.openBranch("   ", "Some address", "RT" + TestDatabase.nextSeq(), 1));
    }

    @Test
    void openBranch_withNullName_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> branchService.openBranch(null, "Some address", "RT" + TestDatabase.nextSeq(), 1));
    }

    @Test
    void openBranch_withBlankRoutingCode_throwsIllegalArgumentException_regressionTest() {
        assertThrows(IllegalArgumentException.class,
                () -> branchService.openBranch("Some Branch", "Some address", "   ", 1));
    }

    @Test
    void openBranch_withNullRoutingCode_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> branchService.openBranch("Some Branch", "Some address", null, 1));
    }

    @Test
    void openBranch_rejectsBeforeTouchingTheDatabase_noBranchIsCreatedOnValidationFailure() throws Exception {
        int before = branchService.allWithStats().size();
        assertThrows(IllegalArgumentException.class, () -> branchService.openBranch("", "x", "", 1));
        int after = branchService.allWithStats().size();
        assertEquals(before, after, "A validation failure must not create a partial branch row");
    }

    // ------------------------------------------------------------------
    // openBranch -- duplicate routing code (regression test for the raw-SQLException gap)
    // ------------------------------------------------------------------

    @Test
    void openBranch_withDuplicateRoutingCode_throwsIllegalArgumentException_regressionTest() throws Exception {
        String routingCode = "RT" + TestDatabase.nextSeq();
        branchService.openBranch("First Branch", "Address 1", routingCode, 1);
        int countAfterFirst = branchService.allWithStats().size();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> branchService.openBranch("Second Branch", "Address 2", routingCode, 1),
                "A second branch reusing an already-taken routing code must be rejected with a clear error, " +
                        "not a raw SQLException from the DB's UNIQUE constraint");
        assertTrue(ex.getMessage().contains(routingCode), "The error should name the offending routing code");

        int countAfterDuplicateAttempt = branchService.allWithStats().size();
        assertEquals(countAfterFirst, countAfterDuplicateAttempt,
                "The rejected duplicate attempt must not leave a branch row behind");
    }

    // ------------------------------------------------------------------
    // openBranch -- audit trail (regression test for the missing-audit-entry gap)
    // ------------------------------------------------------------------

    @Test
    void openBranch_writesAuditTrailEntry_regressionTest() throws Exception {
        int adminId = TestDatabase.insertUser("branchadmin", "ADMIN");
        String routingCode = "RT" + TestDatabase.nextSeq();

        Branch created = branchService.openBranch("Audited Branch", "Somewhere", routingCode, adminId);

        List<AuditLog> logs = auditService.byEntityType("branch", 500);
        AuditLog entry = logs.stream().filter(l -> l.getEntityId() == created.getId()).findFirst()
                .orElseThrow(() -> new AssertionError("Expected a 'branch' audit entry for the new branch"));
        assertEquals("BRANCH_OPENED", entry.getAction());
        assertEquals(Integer.valueOf(adminId), entry.getActorId());
        assertNull(entry.getBeforeValue(), "Opening a branch has no 'before' state");
        assertTrue(entry.getAfterValue().contains("Audited Branch"));
        assertTrue(entry.getAfterValue().contains(routingCode));
    }

    // ------------------------------------------------------------------
    // allWithStats -- account/staff/deposit aggregation
    // ------------------------------------------------------------------

    @Test
    void allWithStats_reflectsAccountsStaffAndTotalDepositsForThatBranchOnly() throws Exception {
        int branchId = TestDatabase.insertBranch("Stats Branch " + TestDatabase.nextSeq());
        int otherBranchId = TestDatabase.insertBranch("Other Branch " + TestDatabase.nextSeq());

        int customerId = TestDatabase.insertCustomer("VERIFIED");
        TestDatabase.insertAccount(customerId, branchId, "SAVINGS", new BigDecimal("100.00"));
        TestDatabase.insertAccount(customerId, branchId, "SAVINGS", new BigDecimal("250.00"));
        // An account at a DIFFERENT branch must not leak into this branch's totals.
        TestDatabase.insertAccount(customerId, otherBranchId, "SAVINGS", new BigDecimal("999.00"));

        TestDatabase.insertUserWithBranch("teller", "TELLER", branchId);
        TestDatabase.insertUserWithBranch("teller", "TELLER", branchId);
        // A staff member at a different branch must not count towards this branch's staff count.
        TestDatabase.insertUserWithBranch("teller", "TELLER", otherBranchId);

        Branch stats = branchService.allWithStats().stream()
                .filter(b -> b.getId() == branchId).findFirst().orElseThrow();

        assertEquals(2, stats.getAccountCount());
        assertEquals(2, stats.getEmployeeCount());
        assertEquals(0, new BigDecimal("350.00").compareTo(stats.getTotalDeposits()));
    }

    @Test
    void allWithStats_includesBranchesWithNoActivityAtAll_zeroNotNull() throws Exception {
        int branchId = TestDatabase.insertBranch("Empty Branch " + TestDatabase.nextSeq());

        Branch stats = branchService.allWithStats().stream()
                .filter(b -> b.getId() == branchId).findFirst().orElseThrow();

        assertEquals(0, stats.getAccountCount());
        assertEquals(0, stats.getEmployeeCount());
        assertNotNull(stats.getTotalDeposits(), "COALESCE in the query must prevent a null total for an inactive branch");
        assertEquals(0, BigDecimal.ZERO.compareTo(stats.getTotalDeposits()));
    }
}
