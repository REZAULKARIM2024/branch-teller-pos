package com.branchteller.service;

import com.branchteller.model.CreditScoreHistory;
import com.branchteller.support.TestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Senior-QA-style integration coverage for the Credit Scoring feature, which had ZERO existing
 * coverage before this class -- no CreditScoreServiceTest/IntegrationTest file existed anywhere
 * in the suite.
 *
 * <p>This review found one real defect, now fixed in {@link CreditScoreService#computeScore}:
 * the score-history insert, the customer's stored credit_score column update, and the audit log
 * entry used to run as three independent autocommit statements rather than one transaction, so a
 * failure between them could leave credit_score_history and customers.credit_score disagreeing
 * about a customer's latest score. Nothing in the UI currently displays customers.credit_score
 * (only the history feed is shown), so this was low-impact today, but it's the same class of
 * atomicity gap fixed in ComplianceService#fileReport this session, and just as cheap to close.
 * {@link #computeScore_persistsScoreToBothTheCustomerRecordAndHistory()} guards the intended
 * (now-atomic) behavior.
 *
 * <p>This review also confirms (deliberately, not as a bug) a real characteristic of the scoring
 * formula: {@link #computeScore_noLoanHistoryAtAll_scoresIdenticallyToAPerfectOnTimeRepaymentHistory()}
 * documents that a customer who has NEVER taken a loan scores the full 200 repayment-history
 * points -- identically to a customer with a flawless on-time repayment record -- because
 * {@code loanOnTimeRatio()} treats "nothing to judge yet" as 1.0. That means a customer with a
 * genuinely mixed repayment record scores strictly worse than one with no borrowing history at
 * all, which is worth knowing when reading a score, even though it isn't a code defect to fix.
 *
 * <p>Since customers/accounts/loans are shared, whole-JVM tables, every test below either builds
 * a customer with zero accounts (balance and tenure are then unconditionally 0, safe regardless
 * of what else is in the shared DB) or attaches a dedicated $0.00-balance account opened "today"
 * (tenure 0, balance 0) so the only variable under test moves.
 */
class CreditScoreIntegrationTest {

    private final CreditScoreService creditScoreService = new CreditScoreService();

    @BeforeAll
    static void setUpSchema() throws SQLException {
        TestDatabase.ensureSchema();
    }

    @Test
    void computeScore_unknownCustomerId_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> creditScoreService.computeScore(-999999, 1));
    }

    @Test
    void computeScore_newCustomerWithNothingOnRecord_scores520AndRatesPoor() throws Exception {
        // No accounts (balance=0, tenure=0), PENDING KYC (+0), no loans (neutral +200), no flags.
        // 300 + round(0 + 0 + 200 + 0 + 20) = 520.
        int customerId = TestDatabase.insertCustomer("PENDING");

        CreditScoreHistory h = creditScoreService.computeScore(customerId, 1);

        assertEquals(520, h.getScore());
        assertEquals("POOR", h.getRating());
    }

    @Test
    void computeScore_bestPossibleInputs_scoresExactly850AndRatesExcellent() throws Exception {
        int branchId = TestDatabase.insertBranch("Test Branch");
        int customerId = TestDatabase.insertCustomer("VERIFIED");
        int accountId = TestDatabase.insertAccount(customerId, branchId, "SAVINGS", new BigDecimal("60000.00"));
        TestDatabase.setAccountOpenedDate(accountId, LocalDate.now().minusMonths(130));

        // balance: min(150, 60000/50000*150) = 150 (saturated)
        // tenure: min(120, 130/120*120) = 120 (saturated)
        // repayment: no loans -> neutral 200
        // kyc: verified -> 60
        // base: 20
        // 300 + round(150+120+200+60+20) = 850
        CreditScoreHistory h = creditScoreService.computeScore(customerId, 1);

        assertEquals(850, h.getScore());
        assertEquals("EXCELLENT", h.getRating());
    }

    @Test
    void computeScore_verifiedKyc_scoresExactly60PointsHigherThanUnverified() throws Exception {
        int pendingId = TestDatabase.insertCustomer("PENDING");
        int verifiedId = TestDatabase.insertCustomer("VERIFIED");

        CreditScoreHistory pending = creditScoreService.computeScore(pendingId, 1);
        CreditScoreHistory verified = creditScoreService.computeScore(verifiedId, 1);

        assertEquals(520, pending.getScore());
        assertEquals("POOR", pending.getRating());
        assertEquals(580, verified.getScore());
        assertEquals("FAIR", verified.getRating(), "580 is exactly the FAIR boundary");
        assertEquals(60, verified.getScore() - pending.getScore());
    }

    @Test
    void computeScore_balanceHeld_addsPointsProportionally() throws Exception {
        int branchId = TestDatabase.insertBranch("Test Branch");
        int zeroBalanceId = TestDatabase.insertCustomer("PENDING");
        int partialBalanceId = TestDatabase.insertCustomer("PENDING");
        TestDatabase.insertAccount(partialBalanceId, branchId, "SAVINGS", new BigDecimal("25000.00"));

        CreditScoreHistory zero = creditScoreService.computeScore(zeroBalanceId, 1);
        CreditScoreHistory partial = creditScoreService.computeScore(partialBalanceId, 1);

        // 25000/50000*150 = 75 exactly (half the saturating balance).
        assertEquals(520, zero.getScore());
        assertEquals(595, partial.getScore());
        assertEquals("FAIR", partial.getRating());
        assertEquals(75, partial.getScore() - zero.getScore());
    }

    @Test
    void computeScore_balancePoints_saturateAt50000_noBonusBeyondThat() throws Exception {
        int branchId = TestDatabase.insertBranch("Test Branch");
        int atSaturationId = TestDatabase.insertCustomer("PENDING");
        int beyondSaturationId = TestDatabase.insertCustomer("PENDING");
        TestDatabase.insertAccount(atSaturationId, branchId, "SAVINGS", new BigDecimal("50000.00"));
        TestDatabase.insertAccount(beyondSaturationId, branchId, "SAVINGS", new BigDecimal("100000.00"));

        CreditScoreHistory at = creditScoreService.computeScore(atSaturationId, 1);
        CreditScoreHistory beyond = creditScoreService.computeScore(beyondSaturationId, 1);

        assertEquals(670, at.getScore(), "300 + round(150+0+200+0+20) = 670, exactly the GOOD boundary");
        assertEquals("GOOD", at.getRating());
        assertEquals(at.getScore(), beyond.getScore(), "$100k must score no higher than $50k -- balance points cap at 150");
    }

    @Test
    void computeScore_tenure_addsPointsProportionally() throws Exception {
        int branchId = TestDatabase.insertBranch("Test Branch");
        int newAccountId = TestDatabase.insertCustomer("PENDING");
        TestDatabase.insertAccount(newAccountId, branchId, "SAVINGS", BigDecimal.ZERO);

        int halfTenureCustomerId = TestDatabase.insertCustomer("PENDING");
        int halfTenureAccountId = TestDatabase.insertAccount(halfTenureCustomerId, branchId, "SAVINGS", BigDecimal.ZERO);
        TestDatabase.setAccountOpenedDate(halfTenureAccountId, LocalDate.now().minusMonths(60));

        CreditScoreHistory fresh = creditScoreService.computeScore(newAccountId, 1);
        CreditScoreHistory halfTenure = creditScoreService.computeScore(halfTenureCustomerId, 1);

        // 60/120*120 = 60 exactly (half the saturating tenure).
        assertEquals(520, fresh.getScore());
        assertEquals(580, halfTenure.getScore());
        assertEquals("FAIR", halfTenure.getRating(), "580 is exactly the FAIR boundary");
        assertEquals(60, halfTenure.getScore() - fresh.getScore());
    }

    @Test
    void computeScore_tenurePoints_saturateAt120Months_noBonusBeyondThat() throws Exception {
        int branchId = TestDatabase.insertBranch("Test Branch");
        int atSaturationCustomerId = TestDatabase.insertCustomer("PENDING");
        int atSaturationAccountId = TestDatabase.insertAccount(atSaturationCustomerId, branchId, "SAVINGS", BigDecimal.ZERO);
        TestDatabase.setAccountOpenedDate(atSaturationAccountId, LocalDate.now().minusMonths(120));

        int beyondSaturationCustomerId = TestDatabase.insertCustomer("PENDING");
        int beyondSaturationAccountId = TestDatabase.insertAccount(beyondSaturationCustomerId, branchId, "SAVINGS", BigDecimal.ZERO);
        TestDatabase.setAccountOpenedDate(beyondSaturationAccountId, LocalDate.now().minusMonths(240));

        CreditScoreHistory at = creditScoreService.computeScore(atSaturationCustomerId, 1);
        CreditScoreHistory beyond = creditScoreService.computeScore(beyondSaturationCustomerId, 1);

        assertEquals(at.getScore(), beyond.getScore(), "20 years tenure must score no higher than 10 years -- tenure points cap at 120");
    }

    @Test
    void computeScore_noLoanHistoryAtAll_scoresIdenticallyToAPerfectOnTimeRepaymentHistory() throws Exception {
        int branchId = TestDatabase.insertBranch("Test Branch");

        int noLoansId = TestDatabase.insertCustomer("PENDING");

        int perfectHistoryId = TestDatabase.insertCustomer("PENDING");
        int perfectHistoryAccountId = TestDatabase.insertAccount(perfectHistoryId, branchId, "SAVINGS", BigDecimal.ZERO);
        int loanId = TestDatabase.insertLoan(perfectHistoryId, perfectHistoryAccountId);
        LocalDate due = LocalDate.now().minusMonths(1);
        TestDatabase.insertRepayment(loanId, 1, due, "PAID", due);
        TestDatabase.insertRepayment(loanId, 2, due.plusMonths(1), "PAID", due.plusMonths(1));

        CreditScoreHistory noLoans = creditScoreService.computeScore(noLoansId, 1);
        CreditScoreHistory perfectHistory = creditScoreService.computeScore(perfectHistoryId, 1);

        assertEquals(520, noLoans.getScore());
        assertEquals(noLoans.getScore(), perfectHistory.getScore(),
                "Documented quirk: no loan history at all scores identically to a flawless on-time repayment history");
    }

    @Test
    void computeScore_partialOnTimeRatio_scoresWorseThanHavingNoLoanHistoryAtAll() throws Exception {
        int branchId = TestDatabase.insertBranch("Test Branch");
        int customerId = TestDatabase.insertCustomer("PENDING");
        int accountId = TestDatabase.insertAccount(customerId, branchId, "SAVINGS", BigDecimal.ZERO);
        int loanId = TestDatabase.insertLoan(customerId, accountId);

        LocalDate onTimeDue = LocalDate.now().minusMonths(2);
        TestDatabase.insertRepayment(loanId, 1, onTimeDue, "PAID", onTimeDue);
        LocalDate missedDue = LocalDate.now().minusMonths(1);
        TestDatabase.insertRepayment(loanId, 2, missedDue, "OVERDUE", null);

        CreditScoreHistory h = creditScoreService.computeScore(customerId, 1);

        // ratio = 1/2 = 0.5 -> repayment points = 100 (half of the 200 a no-loan customer gets).
        // 300 + round(0+0+100+0+20) = 420.
        assertEquals(420, h.getScore());
        assertEquals("POOR", h.getRating());
        assertEquals(100, 520 - h.getScore(), "A 50% on-time ratio must score 100 points below a customer with no loan history");
    }

    @Test
    void computeScore_pendingRepaymentsNotYetDue_areExcludedFromTheRatio() throws Exception {
        int branchId = TestDatabase.insertBranch("Test Branch");
        int customerId = TestDatabase.insertCustomer("PENDING");
        int accountId = TestDatabase.insertAccount(customerId, branchId, "SAVINGS", BigDecimal.ZERO);
        int loanId = TestDatabase.insertLoan(customerId, accountId);

        LocalDate onTimeDue = LocalDate.now().minusMonths(1);
        TestDatabase.insertRepayment(loanId, 1, onTimeDue, "PAID", onTimeDue);
        TestDatabase.insertRepayment(loanId, 2, LocalDate.now().plusMonths(1), "PENDING", null);

        CreditScoreHistory h = creditScoreService.computeScore(customerId, 1);

        // Only the PAID installment counts (ratio = 1/1 = 1.0); the not-yet-due PENDING one must
        // not drag the ratio down to 1/2.
        assertEquals(520, h.getScore());
    }

    @Test
    void computeScore_eachAmlFlag_subtractsExactly40Points() throws Exception {
        int branchId = TestDatabase.insertBranch("Test Branch");
        int customerId = TestDatabase.insertCustomer("PENDING");
        int accountId = TestDatabase.insertAccount(customerId, branchId, "SAVINGS", BigDecimal.ZERO);

        CreditScoreHistory baseline = creditScoreService.computeScore(customerId, 1);
        TestDatabase.insertFlagAt(accountId, new BigDecimal("11000.00"), java.time.LocalDateTime.now());
        CreditScoreHistory afterOneFlag = creditScoreService.computeScore(customerId, 1);
        TestDatabase.insertFlagAt(accountId, new BigDecimal("12000.00"), java.time.LocalDateTime.now());
        CreditScoreHistory afterTwoFlags = creditScoreService.computeScore(customerId, 1);

        assertEquals(520, baseline.getScore());
        assertEquals(480, afterOneFlag.getScore());
        assertEquals(440, afterTwoFlags.getScore());
    }

    @Test
    void computeScore_flooredAt300_neverGoesNegative() throws Exception {
        int branchId = TestDatabase.insertBranch("Test Branch");
        int customerId = TestDatabase.insertCustomer("PENDING");
        int accountId = TestDatabase.insertAccount(customerId, branchId, "SAVINGS", BigDecimal.ZERO);

        // Baseline is 520; 6 flags at -40 each = -240, which would put the raw total at 280.
        for (int i = 0; i < 6; i++) {
            TestDatabase.insertFlagAt(accountId, new BigDecimal("15000.00"), java.time.LocalDateTime.now());
        }

        CreditScoreHistory h = creditScoreService.computeScore(customerId, 1);

        assertEquals(300, h.getScore(), "Score must clamp at the 300 floor, not go to 280");
        assertEquals("POOR", h.getRating());
    }

    @Test
    void computeScore_persistsScoreToBothTheCustomerRecordAndHistory() throws Exception {
        int customerId = TestDatabase.insertCustomer("VERIFIED");

        CreditScoreHistory h = creditScoreService.computeScore(customerId, 7);

        assertEquals(Integer.valueOf(h.getScore()), TestDatabase.creditScoreOf(customerId),
                "Regression: computeScore() must persist the same score onto customers.credit_score, atomically with the history row");

        List<CreditScoreHistory> history = creditScoreService.historyForCustomer(customerId);
        assertEquals(1, history.size());
        assertEquals(h.getScore(), history.get(0).getScore());
        assertEquals(h.getRating(), history.get(0).getRating());
    }

    @Test
    void computeScore_calledTwice_appendsANewHistoryRowRatherThanOverwriting() throws Exception {
        int customerId = TestDatabase.insertCustomer("PENDING");

        creditScoreService.computeScore(customerId, 1);
        creditScoreService.computeScore(customerId, 1);

        assertEquals(2, creditScoreService.historyForCustomer(customerId).size());
    }

    @Test
    void computeScore_writesAnAuditTrailEntry() throws Exception {
        int customerId = TestDatabase.insertCustomer("PENDING");

        int before = TestDatabase.auditCountFor("customer", customerId, "CREDIT_SCORE_COMPUTED");
        creditScoreService.computeScore(customerId, 55);
        int after = TestDatabase.auditCountFor("customer", customerId, "CREDIT_SCORE_COMPUTED");

        assertEquals(before + 1, after);
    }

    @Test
    void recentAll_respectsTheLimitParameter() throws Exception {
        int customerId = TestDatabase.insertCustomer("PENDING");
        creditScoreService.computeScore(customerId, 1);

        List<CreditScoreHistory> mostRecentOne = creditScoreService.recentAll(1);

        assertEquals(1, mostRecentOne.size());
    }
}
