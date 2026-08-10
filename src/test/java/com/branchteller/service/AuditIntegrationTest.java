package com.branchteller.service;

import com.branchteller.config.DBConnection;
import com.branchteller.model.AuditLog;
import com.branchteller.support.TestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Senior-QA-style integration coverage for the Audit Log feature, which had ZERO existing
 * coverage before this class -- no AuditServiceTest/AuditIntegrationTest file existed anywhere
 * in the suite (the only other hits for "audit" were the shared {@code audit_trail} schema/fixture
 * helpers in TestDatabase, used incidentally by other features' tests to check THEIR audit rows).
 *
 * <p>This review found one real defect, now fixed in {@link com.branchteller.gui.AuditLogPanel}:
 * the entity-type filter dropdown only listed {@code "account"}, {@code "cheque"}, and
 * {@code "loan"}, but a full grep of every {@code auditService.log(...)} call site across the
 * codebase shows 11 distinct {@code entity_type} values actually get written -- also
 * {@code aml_flag}, {@code card}, {@code complaint}, {@code customer}, {@code employee},
 * {@code pending_approval}, {@code regulatory_report}, and {@code user}. With 8 of those missing
 * from the dropdown, an Admin investigating "who did what" for, say, card actions or AML flag
 * reviews had no way to filter down to them and had to scan the unfiltered "ALL" list by eye.
 * The dropdown is now kept in sync with every entity_type string actually in use.
 *
 * <p>This class tests {@link AuditService}, the layer AuditLogPanel actually calls (recent() /
 * byEntityType()), rather than re-deriving assertions from the shared audit_trail table directly.
 * Since audit_trail is written to by essentially every other feature's tests in this shared,
 * whole-JVM database, filtering tests use a deliberately distinctive entity_type string
 * ("qa_audit_test_entity") that no production code ever writes, guaranteeing zero contamination
 * regardless of what else is happening in the suite.
 */
class AuditIntegrationTest {

    private final AuditService auditService = new AuditService();

    @BeforeAll
    static void setUpSchema() throws SQLException {
        TestDatabase.ensureSchema();
    }

    @Test
    void log_selfContainedVariant_persistsAndIsReadableViaByEntityType() throws Exception {
        long seq = TestDatabase.nextSeq();
        String entityType = "qa_audit_test_entity";

        auditService.log(1, "QA_TEST_ACTION", entityType, (int) seq, "before-" + seq, "after-" + seq);

        List<AuditLog> found = auditService.byEntityType(entityType, 500).stream()
                .filter(l -> l.getEntityId() != null && l.getEntityId() == (int) seq)
                .toList();

        assertEquals(1, found.size());
        AuditLog log = found.get(0);
        assertEquals("QA_TEST_ACTION", log.getAction());
        assertEquals("before-" + seq, log.getBeforeValue());
        assertEquals("after-" + seq, log.getAfterValue());
        assertEquals(1, log.getActorId());
        assertNotNull(log.getCreatedAt());
    }

    @Test
    void log_withNullActor_recordsASystemActionWithNoActorNameJoined() throws Exception {
        long seq = TestDatabase.nextSeq();
        String entityType = "qa_audit_test_entity";

        auditService.log(null, "QA_SYSTEM_ACTION", entityType, (int) seq, null, "system-initiated");

        AuditLog log = auditService.byEntityType(entityType, 500).stream()
                .filter(l -> l.getEntityId() != null && l.getEntityId() == (int) seq)
                .findFirst().orElseThrow();

        assertNull(log.getActorId(), "A system action has no actor id");
        assertNull(log.getActorName(), "With no actor_id, the LEFT JOIN to users must yield a null actor name -- "
                + "AuditLogPanel is what substitutes the 'System' label for display, not the service/DAO layer");
    }

    @Test
    void byEntityType_onlyReturnsRowsMatchingThatExactEntityType() throws Exception {
        long seq = TestDatabase.nextSeq();
        String thisTestsType = "qa_audit_test_entity_" + seq;

        auditService.log(1, "QA_TEST_ACTION", thisTestsType, 1, null, "row for this type");
        auditService.log(1, "QA_TEST_ACTION", "some_other_unrelated_type_" + seq, 1, null, "row for a different type");

        List<AuditLog> results = auditService.byEntityType(thisTestsType, 500);

        assertFalse(results.isEmpty());
        assertTrue(results.stream().allMatch(l -> thisTestsType.equals(l.getEntityType())),
                "byEntityType() must never leak rows from a different entity_type");
    }

    @Test
    void recent_respectsTheLimitParameter() throws Exception {
        // Write several rows so there's guaranteed to be more than 1 candidate in the shared table.
        for (int i = 0; i < 3; i++) {
            auditService.log(1, "QA_TEST_ACTION", "qa_audit_test_entity", (int) TestDatabase.nextSeq(), null, "x");
        }

        List<AuditLog> mostRecentOne = auditService.recent(1);

        assertEquals(1, mostRecentOne.size());
    }

    @Test
    void recent_ordersNewestFirst() throws Exception {
        List<AuditLog> recent = auditService.recent(50);

        assertFalse(recent.isEmpty());
        for (int i = 0; i < recent.size() - 1; i++) {
            java.time.LocalDateTime current = recent.get(i).getCreatedAt();
            java.time.LocalDateTime next = recent.get(i + 1).getCreatedAt();
            assertFalse(current.isBefore(next),
                    "recent() must be ordered newest-first: row " + i + " (" + current + ") is before row " + (i + 1) + " (" + next + ")");
        }
    }

    @Test
    void log_connectionVariant_commitsOrRollsBackWithTheCallersTransaction() throws Exception {
        // Regression test for AuditService's own documented contract: "Uses the caller's existing
        // connection/transaction -- so the audit row commits or rolls back together with the
        // balance change it's describing." If a caller's transaction rolls back, the audit row
        // must NOT survive as an orphaned entry describing a change that never actually happened.
        long seq = TestDatabase.nextSeq();
        String entityType = "qa_audit_test_entity";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            auditService.log(conn, 1, "QA_ACTION_THAT_GETS_ROLLED_BACK", entityType, (int) seq, null, "should never persist");
            conn.rollback();
            conn.setAutoCommit(true);
        }

        boolean survived = auditService.byEntityType(entityType, 500).stream()
                .anyMatch(l -> l.getEntityId() != null && l.getEntityId() == (int) seq);

        assertFalse(survived, "An audit row written via the connection-sharing log() overload must roll back with its caller's transaction");
    }

    @Test
    void log_connectionVariant_persistsWhenTheCallersTransactionCommits() throws Exception {
        long seq = TestDatabase.nextSeq();
        String entityType = "qa_audit_test_entity";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            auditService.log(conn, 1, "QA_ACTION_THAT_COMMITS", entityType, (int) seq, null, "should persist");
            conn.commit();
            conn.setAutoCommit(true);
        }

        boolean persisted = auditService.byEntityType(entityType, 500).stream()
                .anyMatch(l -> l.getEntityId() != null && l.getEntityId() == (int) seq);

        assertTrue(persisted, "An audit row written via the connection-sharing log() overload must survive its caller's commit");
    }
}
