package com.branchteller.service;

import com.branchteller.support.TestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Senior-QA-style integration coverage for the Reports feature (ReportService), which had
 * ZERO existing tests before this class -- no bug was found or fixed here (unlike the
 * Interest Accrual / Accounts &amp; KYC / AML Flags reviews, which each turned up a real
 * defect); the gap was simply that this feature was completely unverified.
 *
 * <p>The tricky part of testing this service honestly: {@code dailySummary}/{@code
 * flagCountForDate} group by the real {@code created_at}/{@code flagged_at} columns, which
 * default to {@code CURRENT_TIMESTAMP} -- and this test suite shares ONE H2 database for the
 * entire JVM (see {@link TestDatabase}), so by the time this class runs, dozens of other
 * tests have already posted transactions and flags stamped with today's real date. Two
 * strategies are used to keep assertions exact despite that:
 * <ul>
 *   <li>For grouping/boundary tests, seed rows directly at a {@link TestDatabase#uniqueHistoricalDate()}
 *       that no other test could ever touch, via {@link TestDatabase#insertTransactionAt} /
 *       {@link TestDatabase#insertFlagAt} (bypassing the services' own NOW()-based inserts).</li>
 *   <li>For the "is this actually wired to the real teller-facing services" tests, take a
 *       before/after snapshot around a single real {@code BankingService}/{@code AmlService}
 *       call and assert the delta, rather than asserting an absolute count for "today".</li>
 * </ul>
 */
class ReportServiceIntegrationTest {

    private final ReportService reportService = new ReportService();
    private final BankingService bankingService = new BankingService();

    @BeforeAll
    static void setUpSchema() throws Exception {
        TestDatabase.ensureSchema();
    }

    private static Optional<ReportService.DailySummaryLine> lineFor(List<ReportService.DailySummaryLine> summary, String type) {
        return summary.stream().filter(l -> l.txnType.equals(type)).findFirst();
    }

    // ------------------------------------------------------------------
    // dailySummary -- grouping and day-boundary correctness
    // ------------------------------------------------------------------

    @Test
    void dailySummary_aggregatesMultipleTypesWithCorrectCountsAndTotals_orderedByType() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("1000.00"));
        LocalDate day = TestDatabase.uniqueHistoricalDate();
        LocalDateTime noon = day.atTime(12, 0);

        TestDatabase.insertTransactionAt(fx.accountId, "DEPOSIT", new BigDecimal("100.00"), new BigDecimal("1100.00"), fx.tellerId, noon);
        TestDatabase.insertTransactionAt(fx.accountId, "DEPOSIT", new BigDecimal("200.00"), new BigDecimal("1300.00"), fx.tellerId, noon);
        TestDatabase.insertTransactionAt(fx.accountId, "DEPOSIT", new BigDecimal("50.00"), new BigDecimal("1350.00"), fx.tellerId, noon);
        TestDatabase.insertTransactionAt(fx.accountId, "WITHDRAW", new BigDecimal("30.00"), new BigDecimal("1320.00"), fx.tellerId, noon);
        TestDatabase.insertTransactionAt(fx.accountId, "WITHDRAW", new BigDecimal("70.00"), new BigDecimal("1250.00"), fx.tellerId, noon);

        List<ReportService.DailySummaryLine> summary = reportService.dailySummary(day);

        assertEquals(2, summary.size(), "Only DEPOSIT and WITHDRAW lines should appear on this exclusively-owned day");
        assertEquals("DEPOSIT", summary.get(0).txnType, "ORDER BY txn_type should put DEPOSIT before WITHDRAW alphabetically");
        assertEquals("WITHDRAW", summary.get(1).txnType);

        ReportService.DailySummaryLine deposits = lineFor(summary, "DEPOSIT").orElseThrow();
        assertEquals(3, deposits.count);
        assertEquals(0, new BigDecimal("350.00").compareTo(deposits.totalAmount));

        ReportService.DailySummaryLine withdrawals = lineFor(summary, "WITHDRAW").orElseThrow();
        assertEquals(2, withdrawals.count);
        assertEquals(0, new BigDecimal("100.00").compareTo(withdrawals.totalAmount));
    }

    @Test
    void dailySummary_excludesTransactionsOutsideTheDayBoundary() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("1000.00"));
        LocalDate day = TestDatabase.uniqueHistoricalDate();

        TestDatabase.insertTransactionAt(fx.accountId, "DEPOSIT", new BigDecimal("11.00"), new BigDecimal("1011.00"), fx.tellerId,
                day.atStartOfDay()); // exactly midnight -- start of the day, must be included
        TestDatabase.insertTransactionAt(fx.accountId, "DEPOSIT", new BigDecimal("22.00"), new BigDecimal("1033.00"), fx.tellerId,
                day.atTime(23, 59, 59)); // last second of the day, must be included
        TestDatabase.insertTransactionAt(fx.accountId, "DEPOSIT", new BigDecimal("999.00"), new BigDecimal("2032.00"), fx.tellerId,
                day.plusDays(1).atStartOfDay()); // midnight of the NEXT day -- must be excluded
        TestDatabase.insertTransactionAt(fx.accountId, "DEPOSIT", new BigDecimal("999.00"), new BigDecimal("3031.00"), fx.tellerId,
                day.minusDays(1).atTime(23, 59, 59)); // last second of the PREVIOUS day -- must be excluded

        ReportService.DailySummaryLine deposits = lineFor(reportService.dailySummary(day), "DEPOSIT").orElseThrow();
        assertEquals(2, deposits.count, "Only the two same-day transactions should be counted");
        assertEquals(0, new BigDecimal("33.00").compareTo(deposits.totalAmount));
    }

    // ------------------------------------------------------------------
    // flagCountForDate
    // ------------------------------------------------------------------

    @Test
    void flagCountForDate_countsOnlyFlagsFlaggedOnThatDay() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(BigDecimal.ZERO);
        LocalDate day = TestDatabase.uniqueHistoricalDate();

        TestDatabase.insertFlagAt(fx.accountId, new BigDecimal("15000.00"), day.atTime(9, 0));
        TestDatabase.insertFlagAt(fx.accountId, new BigDecimal("20000.00"), day.atTime(17, 30));
        TestDatabase.insertFlagAt(fx.accountId, new BigDecimal("30000.00"), day.plusDays(1).atStartOfDay()); // next day -- excluded

        assertEquals(2, reportService.flagCountForDate(day));
    }

    // ------------------------------------------------------------------
    // exportDailyReportCsv
    // ------------------------------------------------------------------

    @Test
    void exportDailyReportCsv_writesAccurateHeaderTotalsAndFlagCount() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("5000.00"));
        LocalDate day = TestDatabase.uniqueHistoricalDate();
        LocalDateTime noon = day.atTime(12, 0);

        TestDatabase.insertTransactionAt(fx.accountId, "DEPOSIT", new BigDecimal("100.00"), new BigDecimal("5100.00"), fx.tellerId, noon);
        TestDatabase.insertTransactionAt(fx.accountId, "WITHDRAW", new BigDecimal("40.00"), new BigDecimal("5060.00"), fx.tellerId, noon);
        TestDatabase.insertFlagAt(fx.accountId, new BigDecimal("12000.00"), noon);

        Path csvPath = Files.createTempFile("daily-report-test", ".csv");
        try {
            reportService.exportDailyReportCsv(day, csvPath.toString());
            String content = Files.readString(csvPath);

            assertTrue(content.contains("NY Financial Bank - Daily Branch Report"));
            assertTrue(content.contains("Date," + day));
            assertTrue(content.contains("DEPOSIT,1,100.00"));
            assertTrue(content.contains("WITHDRAW,1,40.00"));
            assertTrue(content.contains("TOTAL,2,140.00"));
            assertTrue(content.contains("AML flags raised,1"));
        } finally {
            Files.deleteIfExists(csvPath);
        }
    }

    @Test
    void exportDailyReportCsv_onDayWithNoActivity_stillWritesAValidZeroReport() throws Exception {
        LocalDate emptyDay = TestDatabase.uniqueHistoricalDate();

        Path csvPath = Files.createTempFile("daily-report-empty-test", ".csv");
        try {
            assertDoesNotThrow(() -> reportService.exportDailyReportCsv(emptyDay, csvPath.toString()));
            String content = Files.readString(csvPath);
            assertTrue(content.contains("TOTAL,0,0"));
            assertTrue(content.contains("AML flags raised,0"));
        } finally {
            Files.deleteIfExists(csvPath);
        }
    }

    // ------------------------------------------------------------------
    // End-to-end wiring: proves the report actually reflects what the real
    // teller-facing services post, not just what's inserted directly in tests above.
    // ------------------------------------------------------------------

    @Test
    void dailySummary_reflectsRealDepositsPostedThroughBankingService() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(BigDecimal.ZERO);
        LocalDate today = LocalDate.now();

        BigDecimal depositCountBefore = BigDecimal.valueOf(
                lineFor(reportService.dailySummary(today), "DEPOSIT").map(l -> l.count).orElse(0));
        BigDecimal depositTotalBefore = lineFor(reportService.dailySummary(today), "DEPOSIT")
                .map(l -> l.totalAmount).orElse(BigDecimal.ZERO);

        bankingService.deposit(fx.accountId, new BigDecimal("321.55"), fx.tellerId, "Report wiring check");

        ReportService.DailySummaryLine after = lineFor(reportService.dailySummary(today), "DEPOSIT").orElseThrow(
                () -> new AssertionError("DEPOSIT line must exist in today's summary after posting a deposit"));

        assertEquals(0, BigDecimal.ONE.compareTo(BigDecimal.valueOf(after.count).subtract(depositCountBefore)),
                "Deposit count for today should have increased by exactly 1");
        assertEquals(0, new BigDecimal("321.55").compareTo(after.totalAmount.subtract(depositTotalBefore)),
                "Deposit total for today should have increased by exactly the new deposit's amount");
    }

    @Test
    void flagCountForDate_reflectsRealFlagsRaisedThroughAmlService() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("50000.00"));
        LocalDate today = LocalDate.now();

        int before = reportService.flagCountForDate(today);
        bankingService.withdraw(fx.accountId, new BigDecimal("18000.00"), fx.tellerId, "Report wiring AML check");
        int after = reportService.flagCountForDate(today);

        assertEquals(before + 1, after, "A qualifying withdrawal should raise exactly one new flag counted for today");
    }
}
