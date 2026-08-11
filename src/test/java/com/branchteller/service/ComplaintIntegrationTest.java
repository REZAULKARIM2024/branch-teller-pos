package com.branchteller.service;

import com.branchteller.model.AuditLog;
import com.branchteller.model.Complaint;
import com.branchteller.support.TestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Senior-QA-style integration coverage for the Complaints feature (ComplaintService, backing the
 * Complaints tab's lightweight CRM: log, assign, resolve, close). No dedicated test class existed
 * for this feature before this review, and the H2 test schema didn't even have a {@code
 * complaints} table -- meaning Complaints could never have been integration-tested against a real
 * database at all.
 *
 * <p>Findings, all fixed:</p>
 * <ol>
 * <li>{@link ComplaintService#log} accepted any customerId, category, description, or priority at
 * all -- an unknown customer only failed with a raw FK-violation SQLException, a blank description
 * was only guarded in the GUI (not the service itself), and an invalid priority string was only
 * ever going to be rejected by MySQL's ENUM in production, with a confusing raw error, and not at
 * all against the H2 test schema.</li>
 * <li>{@link ComplaintService#assign} and {@link ComplaintService#resolve} had no state guard at
 * all -- a CLOSED complaint (a terminal state, same as a CANCELLED card or standing instruction
 * elsewhere in this app) could be reassigned or "re-resolved" with a different note, quietly
 * un-closing it.</li>
 * <li>{@link ComplaintService#assign}, {@link ComplaintService#resolve}, and {@link
 * ComplaintService#close} never checked that the complaint ID actually existed -- an unknown ID
 * silently updated zero rows instead of telling the caller anything went wrong. {@code assign()}
 * also never checked that the assignedTo user existed, so a typo'd staff ID only failed with a
 * raw FK-violation SQLException.</li>
 * </ol>
 */
class ComplaintIntegrationTest {

    private final ComplaintService complaintService = new ComplaintService();
    private final AuditService auditService = new AuditService();

    @BeforeAll
    static void setUpSchema() throws Exception {
        TestDatabase.ensureSchema();
    }

    // ------------------------------------------------------------------
    // log() happy path + audit trail
    // ------------------------------------------------------------------

    @Test
    void log_createsAnOpenComplaintAndIsAudited() throws Exception {
        int customerId = TestDatabase.insertCustomer("VERIFIED");
        int tellerId = TestDatabase.insertUser("teller", "TELLER");

        Complaint c = complaintService.log(customerId, "Fees", "Charged an unexpected fee", "HIGH", tellerId);

        assertEquals("OPEN", c.getStatus());
        assertEquals("Fees", c.getCategory());

        List<AuditLog> logs = auditService.byEntityType("complaint", 500);
        assertTrue(logs.stream().anyMatch(l -> l.getEntityId() == c.getId() && "COMPLAINT_LOGGED".equals(l.getAction())));
    }

    // ------------------------------------------------------------------
    // log() validation
    // ------------------------------------------------------------------

    @Test
    void blankCategory_isRejected_regressionTest() throws Exception {
        int customerId = TestDatabase.insertCustomer("VERIFIED");
        int tellerId = TestDatabase.insertUser("teller", "TELLER");

        assertThrows(IllegalArgumentException.class,
                () -> complaintService.log(customerId, "  ", "Some issue", "LOW", tellerId));
    }

    @Test
    void blankDescription_isRejected_regressionTest() throws Exception {
        int customerId = TestDatabase.insertCustomer("VERIFIED");
        int tellerId = TestDatabase.insertUser("teller", "TELLER");

        assertThrows(IllegalArgumentException.class,
                () -> complaintService.log(customerId, "Fees", "   ", "LOW", tellerId));
        assertThrows(IllegalArgumentException.class,
                () -> complaintService.log(customerId, "Fees", null, "LOW", tellerId));
    }

    @Test
    void descriptionOverLimit_isRejected_regressionTest() throws Exception {
        int customerId = TestDatabase.insertCustomer("VERIFIED");
        int tellerId = TestDatabase.insertUser("teller", "TELLER");
        String tooLong = "x".repeat(501);

        assertThrows(IllegalArgumentException.class,
                () -> complaintService.log(customerId, "Fees", tooLong, "LOW", tellerId));
    }

    @Test
    void invalidPriority_isRejected_regressionTest() throws Exception {
        int customerId = TestDatabase.insertCustomer("VERIFIED");
        int tellerId = TestDatabase.insertUser("teller", "TELLER");

        assertThrows(IllegalArgumentException.class,
                () -> complaintService.log(customerId, "Fees", "Some issue", "URGENT", tellerId));
        assertThrows(IllegalArgumentException.class,
                () -> complaintService.log(customerId, "Fees", "Some issue", null, tellerId));
    }

    @Test
    void unknownCustomer_isRejectedWithAClearMessage_regressionTest() throws Exception {
        int tellerId = TestDatabase.insertUser("teller", "TELLER");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> complaintService.log(999_999, "Fees", "Some issue", "LOW", tellerId));
        assertTrue(ex.getMessage().contains("not found"), "Message should explain why: " + ex.getMessage());
    }

    // ------------------------------------------------------------------
    // assign() state machine + validation
    // ------------------------------------------------------------------

    @Test
    void assign_movesStatusToInProgress() throws Exception {
        int customerId = TestDatabase.insertCustomer("VERIFIED");
        int tellerId = TestDatabase.insertUser("teller", "TELLER");
        Complaint c = complaintService.log(customerId, "Fees", "Some issue", "LOW", tellerId);

        complaintService.assign(c.getId(), tellerId, tellerId);

        Complaint reloaded = onlyMatching(complaintService.all(), c.getId());
        assertEquals("IN_PROGRESS", reloaded.getStatus());
    }

    @Test
    void unknownComplaint_isRejectedWithAClearMessage_regressionTest() throws Exception {
        int tellerId = TestDatabase.insertUser("teller", "TELLER");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> complaintService.assign(999_999, tellerId, tellerId));
        assertTrue(ex.getMessage().contains("not found"), "Message should explain why: " + ex.getMessage());
    }

    @Test
    void unknownAssignee_isRejectedWithAClearMessage_regressionTest() throws Exception {
        int customerId = TestDatabase.insertCustomer("VERIFIED");
        int tellerId = TestDatabase.insertUser("teller", "TELLER");
        Complaint c = complaintService.log(customerId, "Fees", "Some issue", "LOW", tellerId);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> complaintService.assign(c.getId(), 999_999, tellerId));
        assertTrue(ex.getMessage().contains("not found"), "Message should explain why: " + ex.getMessage());
    }

    @Test
    void cannotAssignAClosedComplaint_regressionTest() throws Exception {
        int customerId = TestDatabase.insertCustomer("VERIFIED");
        int tellerId = TestDatabase.insertUser("teller", "TELLER");
        Complaint c = complaintService.log(customerId, "Fees", "Some issue", "LOW", tellerId);
        complaintService.close(c.getId(), tellerId);

        assertThrows(IllegalStateException.class, () -> complaintService.assign(c.getId(), tellerId, tellerId));
    }

    // ------------------------------------------------------------------
    // resolve() state machine + validation
    // ------------------------------------------------------------------

    @Test
    void resolve_movesStatusToResolvedAndRecordsTheNote() throws Exception {
        int customerId = TestDatabase.insertCustomer("VERIFIED");
        int tellerId = TestDatabase.insertUser("teller", "TELLER");
        Complaint c = complaintService.log(customerId, "Fees", "Some issue", "LOW", tellerId);

        complaintService.resolve(c.getId(), "Fee reversed and apologized", tellerId);

        Complaint reloaded = onlyMatching(complaintService.all(), c.getId());
        assertEquals("RESOLVED", reloaded.getStatus());
        assertEquals("Fee reversed and apologized", reloaded.getResolutionNote());
    }

    @Test
    void blankResolutionNote_isRejected_regressionTest() throws Exception {
        int customerId = TestDatabase.insertCustomer("VERIFIED");
        int tellerId = TestDatabase.insertUser("teller", "TELLER");
        Complaint c = complaintService.log(customerId, "Fees", "Some issue", "LOW", tellerId);

        assertThrows(IllegalArgumentException.class, () -> complaintService.resolve(c.getId(), "  ", tellerId));
        assertThrows(IllegalArgumentException.class, () -> complaintService.resolve(c.getId(), null, tellerId));
    }

    @Test
    void cannotResolveAClosedComplaint_regressionTest() throws Exception {
        int customerId = TestDatabase.insertCustomer("VERIFIED");
        int tellerId = TestDatabase.insertUser("teller", "TELLER");
        Complaint c = complaintService.log(customerId, "Fees", "Some issue", "LOW", tellerId);
        complaintService.close(c.getId(), tellerId);

        assertThrows(IllegalStateException.class, () -> complaintService.resolve(c.getId(), "Too late", tellerId));
    }

    // ------------------------------------------------------------------
    // close() state machine
    // ------------------------------------------------------------------

    @Test
    void close_movesStatusToClosed() throws Exception {
        int customerId = TestDatabase.insertCustomer("VERIFIED");
        int tellerId = TestDatabase.insertUser("teller", "TELLER");
        Complaint c = complaintService.log(customerId, "Fees", "Some issue", "LOW", tellerId);

        complaintService.close(c.getId(), tellerId);

        Complaint reloaded = onlyMatching(complaintService.all(), c.getId());
        assertEquals("CLOSED", reloaded.getStatus());
    }

    @Test
    void cannotCloseAnAlreadyClosedComplaint_regressionTest() throws Exception {
        int customerId = TestDatabase.insertCustomer("VERIFIED");
        int tellerId = TestDatabase.insertUser("teller", "TELLER");
        Complaint c = complaintService.log(customerId, "Fees", "Some issue", "LOW", tellerId);
        complaintService.close(c.getId(), tellerId);

        assertThrows(IllegalStateException.class, () -> complaintService.close(c.getId(), tellerId));
    }

    @Test
    void unknownComplaint_cannotBeClosed_regressionTest() throws Exception {
        int tellerId = TestDatabase.insertUser("teller", "TELLER");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> complaintService.close(999_999, tellerId));
        assertTrue(ex.getMessage().contains("not found"), "Message should explain why: " + ex.getMessage());
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** {@link ComplaintService#all} returns every complaint in the shared, process-wide H2 test
     *  database (not scoped to the current test method) -- same shared-DB caveat documented in
     *  StandingInstructionIntegrationTest. Filters down to the one this test created instead of
     *  asserting on list size or position. */
    private static Complaint onlyMatching(List<Complaint> all, int complaintId) {
        return all.stream().filter(c -> c.getId() == complaintId).findFirst()
                .orElseThrow(() -> new AssertionError("No complaint #" + complaintId + " in " + all.size() + " results"));
    }
}
