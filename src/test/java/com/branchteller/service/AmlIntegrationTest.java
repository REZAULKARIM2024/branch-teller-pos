package com.branchteller.service;

import com.branchteller.model.SuspiciousActivityFlag;
import com.branchteller.model.Transaction;
import com.branchteller.support.TestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Senior-QA-style integration coverage for the AML Flags feature (AmlService's read/review
 * surface -- unreviewed(), all(), markReviewed() -- plus its wiring into BankingService).
 * {@code AmlServiceTest} already thoroughly covers {@code checkAndFlag}'s threshold-boundary
 * logic in isolation (self-contained H2 connection); this class exists to close the gaps a
 * senior QA review of that coverage would flag:
 *
 * <ul>
 *   <li>{@code markReviewed()} used to silently no-op (zero rows updated, no exception) when
 *       given a flag id that doesn't exist -- the same class of bug found and fixed in
 *       {@code CustomerService}. That's fixed here (now throws {@link IllegalArgumentException}),
 *       and this class is the regression coverage for it.</li>
 *   <li>{@code markReviewed()} wrote no audit trail entry at all -- every other
 *       compliance-sensitive decision in this app (KYC verify/reject, AML sanctions screening,
 *       SAR/CTR filing, card block/unblock, etc.) is audited, but reviewing a suspicious
 *       activity flag was not. That's fixed here too (an {@code AML_FLAG_REVIEWED} audit row is
 *       now written), and this class is the regression coverage for it.</li>
 *   <li>{@code unreviewed()} and {@code all(limit)} were never exercised against the shared
 *       database at all outside one incidental assertion in {@code EndToEndFlowTest}.</li>
 *   <li>The end-to-end wiring from {@code BankingService.deposit/withdraw/transfer} into
 *       {@code AmlService.checkAndFlag} was only proven for deposits; withdraw and transfer
 *       were untested, as was the (intentional) fact that only the outgoing leg of a transfer
 *       is monitored, not the incoming one.</li>
 * </ul>
 */
class AmlIntegrationTest {

    private final AmlService amlService = new AmlService();
    private final BankingService bankingService = new BankingService();

    @BeforeAll
    static void setUpSchema() throws Exception {
        TestDatabase.ensureSchema();
    }

    private int seedFlag(int accountId, BigDecimal amount) throws SQLException {
        // DBConnection.getConnection() returns a plain autoCommit=true connection (see
        // CustomerService.register/etc. for the same pattern), so each statement commits
        // itself -- no explicit commit() call needed or valid here.
        try (Connection conn = com.branchteller.config.DBConnection.getConnection()) {
            amlService.checkAndFlag(conn, accountId, null, amount, "DEPOSIT");
        }
        List<SuspiciousActivityFlag> flags = amlService.unreviewed();
        return flags.stream()
                .filter(f -> f.getAccountId() == accountId)
                .max((a, b) -> Integer.compare(a.getId(), b.getId()))
                .orElseThrow()
                .getId();
    }

    // ------------------------------------------------------------------
    // markReviewed
    // ------------------------------------------------------------------

    @Test
    void markReviewed_removesFromUnreviewedList_andRecordsReviewerAndAuditTrail() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(BigDecimal.ZERO);
        int flagId = seedFlag(fx.accountId, new BigDecimal("12000.00"));
        int reviewerId = TestDatabase.insertUser("reviewer", "MANAGER");

        assertTrue(amlService.unreviewed().stream().anyMatch(f -> f.getId() == flagId));

        amlService.markReviewed(flagId, reviewerId);

        assertFalse(amlService.unreviewed().stream().anyMatch(f -> f.getId() == flagId),
                "Flag must no longer appear in the unreviewed queue");

        SuspiciousActivityFlag reviewed = amlService.all(500).stream()
                .filter(f -> f.getId() == flagId).findFirst().orElseThrow();
        assertTrue(reviewed.isReviewed());
        assertEquals(reviewerId, reviewed.getReviewedBy());
        assertNotNull(reviewed.getReviewDate());

        assertEquals(1, TestDatabase.auditCountFor("aml_flag", flagId, "AML_FLAG_REVIEWED"));
        assertEquals("UNREVIEWED", TestDatabase.auditBeforeValue("aml_flag", flagId, "AML_FLAG_REVIEWED"));
        assertEquals("REVIEWED", TestDatabase.auditAfterValue("aml_flag", flagId, "AML_FLAG_REVIEWED"));
    }

    @Test
    void markReviewed_onNonexistentFlag_throwsIllegalArgumentException() throws Exception {
        int reviewerId = TestDatabase.insertUser("reviewer", "MANAGER");
        assertThrows(IllegalArgumentException.class, () -> amlService.markReviewed(9_999_999, reviewerId));
    }

    @Test
    void markReviewed_onAlreadyReviewedFlag_isPermittedAndUpdatesReviewer() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(BigDecimal.ZERO);
        int flagId = seedFlag(fx.accountId, new BigDecimal("11000.00"));
        int firstReviewer = TestDatabase.insertUser("reviewer", "MANAGER");
        int secondReviewer = TestDatabase.insertUser("reviewer", "MANAGER");

        amlService.markReviewed(flagId, firstReviewer);
        amlService.markReviewed(flagId, secondReviewer); // no state-machine guard today -- documents current behavior

        SuspiciousActivityFlag flag = amlService.all(500).stream()
                .filter(f -> f.getId() == flagId).findFirst().orElseThrow();
        assertEquals(secondReviewer, flag.getReviewedBy(), "Second review should overwrite the first reviewer");
        assertEquals(2, TestDatabase.auditCountFor("aml_flag", flagId, "AML_FLAG_REVIEWED"));
    }

    // ------------------------------------------------------------------
    // unreviewed() / all()
    // ------------------------------------------------------------------

    @Test
    void unreviewed_excludesReviewedFlags_includesOnlyPendingOnes() throws Exception {
        TestDatabase.Fixture fx1 = TestDatabase.standardFixture(BigDecimal.ZERO);
        TestDatabase.Fixture fx2 = TestDatabase.standardFixture(BigDecimal.ZERO);
        int flagToReview = seedFlag(fx1.accountId, new BigDecimal("15000.00"));
        int flagToLeavePending = seedFlag(fx2.accountId, new BigDecimal("20000.00"));
        int reviewerId = TestDatabase.insertUser("reviewer", "MANAGER");

        amlService.markReviewed(flagToReview, reviewerId);

        List<SuspiciousActivityFlag> unreviewed = amlService.unreviewed();
        assertFalse(unreviewed.stream().anyMatch(f -> f.getId() == flagToReview));
        assertTrue(unreviewed.stream().anyMatch(f -> f.getId() == flagToLeavePending));
    }

    @Test
    void all_includesBothReviewedAndUnreviewedFlags() throws Exception {
        TestDatabase.Fixture fx1 = TestDatabase.standardFixture(BigDecimal.ZERO);
        TestDatabase.Fixture fx2 = TestDatabase.standardFixture(BigDecimal.ZERO);
        int reviewedFlag = seedFlag(fx1.accountId, new BigDecimal("13000.00"));
        int unreviewedFlag = seedFlag(fx2.accountId, new BigDecimal("14000.00"));
        int reviewerId = TestDatabase.insertUser("reviewer", "MANAGER");
        amlService.markReviewed(reviewedFlag, reviewerId);

        List<Integer> allIds = amlService.all(1000).stream().map(SuspiciousActivityFlag::getId).toList();
        assertTrue(allIds.contains(reviewedFlag));
        assertTrue(allIds.contains(unreviewedFlag));
    }

    @Test
    void all_respectsLimitParameter() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(BigDecimal.ZERO);
        for (int i = 0; i < 5; i++) {
            seedFlag(fx.accountId, new BigDecimal("10500.00"));
        }
        List<SuspiciousActivityFlag> capped = amlService.all(2);
        assertEquals(2, capped.size());
    }

    // ------------------------------------------------------------------
    // BankingService wiring (deposit / withdraw / transfer)
    // ------------------------------------------------------------------

    @Test
    void bankingServiceDeposit_atThreshold_createsFlagReferencingTheDepositTransaction() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(BigDecimal.ZERO);

        Transaction txn = bankingService.deposit(fx.accountId, new BigDecimal("10000.00"), fx.tellerId, "Large cash deposit");

        assertEquals(1, TestDatabase.flagCountForAccount(fx.accountId));
        SuspiciousActivityFlag flag = amlService.unreviewed().stream()
                .filter(f -> f.getAccountId() == fx.accountId).findFirst().orElseThrow();
        assertEquals(txn.getId(), flag.getTxnId());
        assertTrue(flag.getReason().contains("DEPOSIT"));
    }

    @Test
    void bankingServiceDeposit_belowThreshold_createsNoFlag() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(BigDecimal.ZERO);

        bankingService.deposit(fx.accountId, new BigDecimal("9999.99"), fx.tellerId, "Just under threshold");

        assertEquals(0, TestDatabase.flagCountForAccount(fx.accountId));
    }

    @Test
    void bankingServiceWithdraw_aboveThreshold_createsFlagWithWithdrawReason() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("50000.00"));

        bankingService.withdraw(fx.accountId, new BigDecimal("15000.00"), fx.tellerId, "Large cash withdrawal");

        assertEquals(1, TestDatabase.flagCountForAccount(fx.accountId));
        SuspiciousActivityFlag flag = amlService.unreviewed().stream()
                .filter(f -> f.getAccountId() == fx.accountId).findFirst().orElseThrow();
        assertTrue(flag.getReason().contains("WITHDRAW"));
    }

    @Test
    void bankingServiceTransfer_flagsOnlyTheSourceAccount_notTheDestination() throws Exception {
        TestDatabase.Fixture from = TestDatabase.standardFixture(new BigDecimal("50000.00"));
        int toAccountId = TestDatabase.insertAccount(from.customerId, from.branchId, "SAVINGS", BigDecimal.ZERO);

        bankingService.transfer(from.accountId, toAccountId, new BigDecimal("20000.00"), from.tellerId, "Large transfer");

        assertEquals(1, TestDatabase.flagCountForAccount(from.accountId),
                "The outgoing (source) side of a large transfer should be flagged");
        assertEquals(0, TestDatabase.flagCountForAccount(toAccountId),
                "The incoming (destination) side of an internal transfer is not separately flagged");
    }
}
