package com.branchteller.service;

import com.branchteller.config.DBConnection;
import com.branchteller.dao.InterestAccrualDAO;
import com.branchteller.model.InterestAccrual;
import com.branchteller.model.Loan;
import com.branchteller.support.TestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Senior-QA-style integration coverage for the Correspondence feature (CorrespondenceService,
 * backing the Correspondence tab). There was zero prior test coverage -- grepping the whole test
 * suite for "Correspondence" before this review found nothing.
 *
 * <p>The headline finding: {@link CorrespondenceService#generate} with {@code LOAN_SANCTION} for
 * a REJECTED loan used to fall into the same branch as APPROVED/DISBURSED/CLOSED, producing the
 * letter "We are pleased to inform you that the following credit facility has been rejected on
 * the terms summarized below" -- genuinely wrong, tone-deaf customer-facing copy. Fixed with a
 * dedicated REJECTED branch. Also newly rejects a malformed year on the interest certificate
 * (e.g. "26" instead of "2026"), which previously silently produced a misleading empty-looking
 * certificate instead of a clear validation error.</p>
 *
 * <p>Unlike the money-moving features reviewed earlier this pass (Teller Counter, Cheques,
 * Loans), Correspondence is purely read-only reporting -- generating a letter never moves money
 * or changes any record's state, so the "CLOSED blocks" guard established for those features
 * doesn't apply here; every letter type intentionally works regardless of account status, and
 * always shows the real status honestly in the letter body.</p>
 */
class CorrespondenceIntegrationTest {

    private final CorrespondenceService correspondenceService = new CorrespondenceService();
    private final LoanService loanService = new LoanService();
    private final InterestAccrualDAO interestAccrualDAO = new InterestAccrualDAO();

    @BeforeAll
    static void setUpSchema() throws Exception {
        TestDatabase.ensureSchema();
    }

    // ------------------------------------------------------------------
    // Account-based letters
    // ------------------------------------------------------------------

    @Test
    void accountOpeningLetter_includesRealAccountDetails() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("500.00"));
        String accountNumber = TestDatabase.accountNumberFor(fx.accountId);

        List<String> letter = correspondenceService.generate(
                CorrespondenceService.LetterType.ACCOUNT_OPENING, accountNumber, null, null);

        String text = String.join("\n", letter);
        assertTrue(text.contains(accountNumber));
        assertTrue(text.contains("SAVINGS"));
    }

    @Test
    void unknownAccountNumber_isRejectedForEveryAccountBasedLetter() {
        assertThrows(IllegalArgumentException.class, () -> correspondenceService.generate(
                CorrespondenceService.LetterType.BALANCE_CERTIFICATE, "NO-SUCH-ACCOUNT", null, null));
        assertThrows(IllegalArgumentException.class, () -> correspondenceService.generate(
                CorrespondenceService.LetterType.ACCOUNT_OPENING, "", null, null));
    }

    @Test
    void balanceCertificate_showsCurrentBalance() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("1234.56"));
        String accountNumber = TestDatabase.accountNumberFor(fx.accountId);

        List<String> letter = correspondenceService.generate(
                CorrespondenceService.LetterType.BALANCE_CERTIFICATE, accountNumber, null, null);

        assertTrue(String.join("\n", letter).contains("1234.56"));
    }

    @Test
    void noc_defaultsPurposeWhenBlank_andUsesGivenPurposeOtherwise() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        String accountNumber = TestDatabase.accountNumberFor(fx.accountId);

        List<String> defaulted = correspondenceService.generate(
                CorrespondenceService.LetterType.NO_OBJECTION_CERTIFICATE, accountNumber, null, "  ");
        assertTrue(String.join("\n", defaulted).contains("the purpose stated by the account holder"));

        List<String> withPurpose = correspondenceService.generate(
                CorrespondenceService.LetterType.NO_OBJECTION_CERTIFICATE, accountNumber, null, "visa application");
        assertTrue(String.join("\n", withPurpose).contains("visa application"));
    }

    @Test
    void referenceLetter_defaultsAddresseeWhenBlank() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        String accountNumber = TestDatabase.accountNumberFor(fx.accountId);

        List<String> letter = correspondenceService.generate(
                CorrespondenceService.LetterType.REFERENCE_LETTER, accountNumber, null, null);

        assertTrue(String.join("\n", letter).contains("To Whom It May Concern"));
    }

    @Test
    void accountClosureLetter_wordingReflectsActualStatus() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("0.00"));
        String accountNumber = TestDatabase.accountNumberFor(fx.accountId);

        List<String> whileActive = correspondenceService.generate(
                CorrespondenceService.LetterType.ACCOUNT_CLOSURE, accountNumber, null, null);
        assertTrue(String.join("\n", whileActive).contains("Account Closure Status"));

        TestDatabase.setAccountStatus(fx.accountId, "CLOSED");
        List<String> afterClose = correspondenceService.generate(
                CorrespondenceService.LetterType.ACCOUNT_CLOSURE, accountNumber, null, null);
        assertTrue(String.join("\n", afterClose).contains("Account Closure Confirmation"));
    }

    // ------------------------------------------------------------------
    // Loan sanction letter -- the headline finding
    // ------------------------------------------------------------------

    @Test
    void loanSanctionLetter_appliedLoan_showsPendingMessage() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        Loan loan = loanService.apply(fx.customerId, fx.accountId, "PERSONAL", new BigDecimal("1000.00"), new BigDecimal("5.00"), 12);

        List<String> letter = correspondenceService.generate(
                CorrespondenceService.LetterType.LOAN_SANCTION, null, String.valueOf(loan.getId()), null);

        String text = String.join("\n", letter);
        assertTrue(text.contains("Loan Application Received"));
        assertTrue(text.contains("currently under review"));
    }

    @Test
    void loanSanctionLetter_rejectedLoan_showsRegretfulMessageNotPleased_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        Loan loan = loanService.apply(fx.customerId, fx.accountId, "PERSONAL", new BigDecimal("1000.00"), new BigDecimal("5.00"), 12);
        loanService.reject(loan.getId(), fx.tellerId);

        List<String> letter = correspondenceService.generate(
                CorrespondenceService.LetterType.LOAN_SANCTION, null, String.valueOf(loan.getId()), null);

        String text = String.join("\n", letter);
        assertTrue(text.contains("We regret to inform you"), "Expected regretful wording: " + text);
        assertFalse(text.contains("We are pleased to inform you"),
                "A rejected loan must never say 'pleased' -- that was the original bug");
        assertFalse(text.toLowerCase().contains("rejected on the terms"),
                "Must not say the facility was 'rejected on the terms summarized below'");
    }

    @Test
    void loanSanctionLetter_approvedLoan_showsPleasedMessage() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        Loan loan = loanService.apply(fx.customerId, fx.accountId, "PERSONAL", new BigDecimal("1000.00"), new BigDecimal("5.00"), 12);
        loanService.approve(loan.getId(), fx.tellerId);

        List<String> letter = correspondenceService.generate(
                CorrespondenceService.LetterType.LOAN_SANCTION, null, String.valueOf(loan.getId()), null);

        String text = String.join("\n", letter);
        assertTrue(text.contains("We are pleased to inform you"));
        assertTrue(text.contains("approved on the terms"));
    }

    @Test
    void loanSanctionLetter_nonNumericOrUnknownId_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> correspondenceService.generate(
                CorrespondenceService.LetterType.LOAN_SANCTION, null, "not-a-number", null));
        assertThrows(IllegalArgumentException.class, () -> correspondenceService.generate(
                CorrespondenceService.LetterType.LOAN_SANCTION, null, "999999", null));
    }

    // ------------------------------------------------------------------
    // Interest certificate -- year validation finding
    // ------------------------------------------------------------------

    @Test
    void interestCertificate_malformedYear_isRejected_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        String accountNumber = TestDatabase.accountNumberFor(fx.accountId);

        assertThrows(IllegalArgumentException.class, () -> correspondenceService.generate(
                CorrespondenceService.LetterType.INTEREST_CERTIFICATE, accountNumber, null, "26"));
        assertThrows(IllegalArgumentException.class, () -> correspondenceService.generate(
                CorrespondenceService.LetterType.INTEREST_CERTIFICATE, accountNumber, null, "not-a-year"));
    }

    @Test
    void interestCertificate_defaultsToCurrentYear_andSumsMatchingAccruals() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        String accountNumber = TestDatabase.accountNumberFor(fx.accountId);
        int year = java.time.LocalDate.now().getYear();

        try (Connection conn = DBConnection.getConnection()) {
            InterestAccrual a1 = new InterestAccrual();
            a1.setAccountId(fx.accountId);
            a1.setPeriod(year + "-01");
            a1.setRateApplied(new BigDecimal("6.00"));
            a1.setAmount(new BigDecimal("5.00"));
            a1.setPostedDate(java.time.LocalDate.of(year, 1, 31));
            interestAccrualDAO.insert(conn, a1);

            InterestAccrual a2 = new InterestAccrual();
            a2.setAccountId(fx.accountId);
            a2.setPeriod(year + "-02");
            a2.setRateApplied(new BigDecimal("6.00"));
            a2.setAmount(new BigDecimal("5.25"));
            a2.setPostedDate(java.time.LocalDate.of(year, 2, 28));
            interestAccrualDAO.insert(conn, a2);
        }

        List<String> letter = correspondenceService.generate(
                CorrespondenceService.LetterType.INTEREST_CERTIFICATE, accountNumber, null, null);

        String text = String.join("\n", letter);
        assertTrue(text.contains("Total Interest Earned in " + year + ": $10.25"), "Full text: " + text);
    }

    @Test
    void interestCertificate_yearWithNoAccruals_saysSoRatherThanShowingAnEmptyTable() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        String accountNumber = TestDatabase.accountNumberFor(fx.accountId);

        List<String> letter = correspondenceService.generate(
                CorrespondenceService.LetterType.INTEREST_CERTIFICATE, accountNumber, null, "1999");

        assertTrue(String.join("\n", letter).contains("no interest accrual records found for 1999"));
    }
}
