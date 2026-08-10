package com.branchteller.service;

import com.branchteller.model.RegulatoryReport;
import com.branchteller.model.ScreeningResult;
import com.branchteller.model.SuspiciousActivityFlag;
import com.branchteller.support.TestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Senior-QA-style integration coverage for the Compliance feature (sanctions/PEP screening plus
 * SAR/CTR regulatory report filing), which had ZERO existing coverage before this class -- no
 * ComplianceService*Test file existed anywhere in the suite, and the "compliance" hits in
 * AmlIntegrationTest/EndToEndFlowTest were just a doc comment and an unrelated username prefix.
 *
 * <p>This review found one real defect, now fixed in {@link ComplianceService#fileReport}: filing
 * a SAR/CTR and marking the underlying AML flag reviewed used to be two separate, independently-
 * failable service calls, orchestrated only by CompliancePanel's button handler (fileReport(),
 * then a second amlService.markReviewed() call). If the first succeeded but the second failed for
 * any reason, a regulatory report would exist referencing a flag that was STILL unreviewed -- so
 * it would keep showing up in the Unreviewed Flags list and could be filed a second time as a
 * duplicate SAR/CTR for the same suspicious activity. fileReport() now does both writes on one
 * connection/transaction, so either both land or neither does.
 * {@link #fileReport_rollsBackTheReportInsert_whenTheUnderlyingFlagCannotBeMarkedReviewed()} and
 * {@link #fileReport_atomicallyMarksTheUnderlyingFlagReviewed()} guard against this regressing.
 *
 * <p>sanctions_list is a shared, whole-JVM seeded table (five fixed fictional entries, matching
 * production's database/schema.sql), so screening tests use customer names constructed to overlap
 * with exactly one seeded entry's tokens and nothing else, rather than assuming an empty list.
 */
class ComplianceIntegrationTest {

    private final ComplianceService complianceService = new ComplianceService();
    private final AmlService amlService = new AmlService();

    @BeforeAll
    static void setUpSchema() throws SQLException {
        TestDatabase.ensureSchema();
    }

    @Test
    void screenCustomer_exactNameMatch_isConfirmedMatchAgainstTheCorrectSanctionsEntry() throws Exception {
        // "Viktor Bout" is one of the five fixed sanctions_list seed entries -- an exact-name
        // customer scores 1.0 (both tokens found), which is >= the 0.8 CONFIRMED_MATCH threshold.
        int customerId = TestDatabase.insertCustomerNamed("Viktor Bout", "VERIFIED");

        ScreeningResult r = complianceService.screenCustomer(customerId, 1);

        assertEquals("CONFIRMED_MATCH", r.getStatus());
        assertNotNull(r.getMatchedEntryId(), "A CONFIRMED_MATCH must record which sanctions entry it matched");
        assertEquals(100.0, r.getMatchScore(), 0.001, "Exact name overlap should score a full 100.00%");
    }

    @Test
    void screenCustomer_partialNameOverlap_isPotentialMatch() throws Exception {
        // "Ali Rahman" shares exactly 1 of its 2 tokens ("ALI") with the seeded "Ali Khamenei"
        // entry and nothing with any other seeded entry -- word-overlap score = 1/2 = 0.50,
        // which lands in the POTENTIAL_MATCH band (0.4 <= score < 0.8).
        int customerId = TestDatabase.insertCustomerNamed("Ali Rahman", "VERIFIED");

        ScreeningResult r = complianceService.screenCustomer(customerId, 1);

        assertEquals("POTENTIAL_MATCH", r.getStatus());
        assertEquals(50.0, r.getMatchScore(), 0.001);
        assertNotNull(r.getMatchedEntryId());
    }

    @Test
    void screenCustomer_noNameOverlapWithAnySanctionsEntry_isClearWithNoMatchedEntry() throws Exception {
        int customerId = TestDatabase.insertCustomerNamed("Zzyzx Quorvath", "VERIFIED");

        ScreeningResult r = complianceService.screenCustomer(customerId, 1);

        assertEquals("CLEAR", r.getStatus());
        assertEquals(0.0, r.getMatchScore(), 0.001);
        assertNull(r.getMatchedEntryId(), "CLEAR results must not record a matched entry id, even if some entry scored above zero");
    }

    @Test
    void screenCustomer_unknownCustomerId_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> complianceService.screenCustomer(-999999, 1));
    }

    @Test
    void screenCustomer_persistsTheResultAndItAppearsInAllScreeningResults() throws Exception {
        int customerId = TestDatabase.insertCustomerNamed("Nicolas Maduro", "VERIFIED");

        List<ScreeningResult> before = complianceService.allScreeningResults();
        ScreeningResult r = complianceService.screenCustomer(customerId, 1);
        List<ScreeningResult> after = complianceService.allScreeningResults();

        assertEquals(before.size() + 1, after.size(), "Screening must persist exactly one new row");
        boolean found = after.stream().anyMatch(x -> x.getId() == r.getId()
                && "CONFIRMED_MATCH".equals(x.getStatus())
                && customerId == x.getCustomerId());
        assertTrue(found, "The newly-screened result must be readable back via allScreeningResults()");
    }

    @Test
    void screenCustomer_writesAnAuditTrailEntry() throws Exception {
        int customerId = TestDatabase.insertCustomerNamed("Jane Smith PEP", "VERIFIED");

        int before = TestDatabase.auditCountFor("customer", customerId, "AML_SCREENING");
        complianceService.screenCustomer(customerId, 42);
        int after = TestDatabase.auditCountFor("customer", customerId, "AML_SCREENING");

        assertEquals(before + 1, after, "Every screening call must write exactly one AML_SCREENING audit row");
    }

    @Test
    void fileReport_atomicallyMarksTheUnderlyingFlagReviewed() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("500.00"));
        SuspiciousActivityFlag flag = insertUnreviewedFlag(fx.accountId, new BigDecimal("12000.00"));

        RegulatoryReport r = complianceService.fileReport("SAR", flag, fx.tellerId, "Large cash deposit, unclear source of funds.");

        assertNotNull(r.getReferenceNo());
        assertTrue(r.getReferenceNo().startsWith("SAR-"));

        Optional<SuspiciousActivityFlag> reloaded = amlService.all(500).stream()
                .filter(f -> f.getId() == flag.getId()).findFirst();
        assertTrue(reloaded.isPresent());
        assertTrue(reloaded.get().isReviewed(),
                "Regression: filing a report must mark the underlying flag reviewed in the same transaction");
        assertEquals(fx.tellerId, reloaded.get().getReviewedBy());
    }

    @Test
    void fileReport_rollsBackTheReportInsert_whenTheUnderlyingFlagCannotBeMarkedReviewed() throws Exception {
        // Build a flag object pointing at a flag_id that doesn't exist in the DB (simulates the
        // flag having been deleted/already-processed between the GUI reading it and the user
        // clicking File). markReviewed() will affect zero rows, so fileReport() must throw AND
        // must not leave a dangling regulatory_reports row behind.
        SuspiciousActivityFlag ghostFlag = new SuspiciousActivityFlag();
        ghostFlag.setId(-999999);
        ghostFlag.setAccountId(TestDatabase.standardFixture(new BigDecimal("100.00")).accountId);

        List<RegulatoryReport> before = complianceService.allReports();

        assertThrows(IllegalArgumentException.class,
                () -> complianceService.fileReport("SAR", ghostFlag, 1, "narrative"));

        List<RegulatoryReport> after = complianceService.allReports();
        assertEquals(before.size(), after.size(),
                "Regression: a failed fileReport() (flag not found) must not leave an orphaned regulatory_reports row");
    }

    @Test
    void fileReport_persistsAndIsReadableViaAllReports_withAccountAndFilerNamesJoined() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("500.00"));
        SuspiciousActivityFlag flag = insertUnreviewedFlag(fx.accountId, new BigDecimal("15000.00"));

        RegulatoryReport r = complianceService.fileReport("CTR", flag, fx.tellerId, "Structuring suspected.");

        RegulatoryReport reloaded = complianceService.allReports().stream()
                .filter(x -> x.getId() == r.getId()).findFirst().orElse(null);
        assertNotNull(reloaded, "Filed report must be readable back via allReports()");
        assertEquals("CTR", reloaded.getReportType());
        assertEquals(r.getReferenceNo(), reloaded.getReferenceNo());
        assertEquals(TestDatabase.accountNumberFor(fx.accountId), reloaded.getRelatedAccountNumber(),
                "allReports() must join through to the related account's account_number");
        assertNotNull(reloaded.getFiledByName(), "allReports() must join through to the filer's name");
    }

    @Test
    void fileReport_writesFiledAndReviewedAuditTrailEntries() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("500.00"));
        SuspiciousActivityFlag flag = insertUnreviewedFlag(fx.accountId, new BigDecimal("20000.00"));

        RegulatoryReport r = complianceService.fileReport("SAR", flag, fx.tellerId, "narrative");

        assertEquals(1, TestDatabase.auditCountFor("regulatory_report", r.getId(), "SAR_FILED"));
        assertEquals(1, TestDatabase.auditCountFor("aml_flag", flag.getId(), "AML_FLAG_REVIEWED"));
    }

    @Test
    void fileReport_generatesDistinctReferenceNumbersForConsecutiveFilings() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("500.00"));
        SuspiciousActivityFlag flag1 = insertUnreviewedFlag(fx.accountId, new BigDecimal("11000.00"));
        SuspiciousActivityFlag flag2 = insertUnreviewedFlag(fx.accountId, new BigDecimal("13000.00"));

        RegulatoryReport r1 = complianceService.fileReport("SAR", flag1, fx.tellerId, "first");
        RegulatoryReport r2 = complianceService.fileReport("SAR", flag2, fx.tellerId, "second");

        assertNotEquals(r1.getReferenceNo(), r2.getReferenceNo(),
                "Two consecutive filings must not collide on the same reference number");
    }

    /** Inserts an unreviewed flag directly (bypassing AmlService.checkAndFlag()'s $10k threshold
     *  gate, which isn't what's under test here) and returns a fully-populated model matching
     *  what CompliancePanel would pass into fileReport() after selecting a row from its
     *  Unreviewed Flags table. */
    private SuspiciousActivityFlag insertUnreviewedFlag(int accountId, BigDecimal amount) throws SQLException {
        TestDatabase.insertFlagAt(accountId, amount, java.time.LocalDateTime.now());
        SuspiciousActivityFlag flag = amlService.unreviewed().stream()
                .filter(f -> f.getAccountId() == accountId && amount.compareTo(f.getAmount()) == 0)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Fixture setup failed: inserted flag not found among unreviewed"));
        return flag;
    }
}
