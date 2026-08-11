package com.branchteller.service;

import com.branchteller.model.Biller;
import com.branchteller.model.BillPayment;
import com.branchteller.model.ExternalTransfer;
import com.branchteller.support.TestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Senior-QA-style integration coverage for the Payments feature (PaymentsService, backing the
 * Payments tab's Wire/NEFT/RTGS and Bill Pay sub-tabs). No dedicated test class existed for this
 * feature before this review, and the H2 test schema didn't even have {@code
 * external_transfers}/{@code billers}/{@code bill_payments} tables -- meaning Payments could
 * never have been integration-tested against a real database at all.
 *
 * <p>Findings, all fixed:</p>
 * <ol>
 * <li>{@link PaymentsService#initiateExternalTransfer} accepted any transferType string at all
 * (production's MySQL ENUM would eventually reject a bad one, but with a confusing raw error, and
 * this project's H2 test schema didn't enforce it either), and never validated beneficiaryName/
 * beneficiaryBank/beneficiaryAccount/routingSwift beyond what the GUI happened to send -- all four
 * are NOT NULL in the schema, but a blank string still satisfies NOT NULL, so money could leave
 * an account with an empty beneficiary name and no real record of where it went.</li>
 * <li>{@link PaymentsService#payBill} accepted any billerId at all -- an unknown one only failed
 * with a raw foreign-key SQLException instead of a clear message.</li>
 * <li>Neither method blocked a CLOSED account, unlike every other money-moving feature hardened
 * in this review (Teller Counter, Cheques, Loans, Cards, Standing Instructions) -- both methods
 * withdraw funds from the account, so they belong in the same "CLOSED blocks" bucket.</li>
 * </ol>
 */
class PaymentIntegrationTest {

    private final PaymentsService paymentsService = new PaymentsService();

    @BeforeAll
    static void setUpSchema() throws Exception {
        TestDatabase.ensureSchema();
    }

    // ------------------------------------------------------------------
    // initiateExternalTransfer() happy path
    // ------------------------------------------------------------------

    @Test
    void initiateExternalTransfer_debitsAccountAndReturnsAReference() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("500.00"));

        ExternalTransfer t = paymentsService.initiateExternalTransfer(fx.accountId, "WIRE", "Jane Doe",
                "Other Bank", "9988776655", "OTHRUS33", new BigDecimal("100.00"), fx.tellerId);

        assertEquals("COMPLETED", t.getStatus());
        assertNotNull(t.getReferenceNo());
        assertTrue(t.getReferenceNo().startsWith("WIRE-"));
    }

    // ------------------------------------------------------------------
    // initiateExternalTransfer() validation
    // ------------------------------------------------------------------

    @Test
    void nonPositiveAmount_isRejected() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("500.00"));

        assertThrows(IllegalArgumentException.class, () -> paymentsService.initiateExternalTransfer(
                fx.accountId, "WIRE", "Jane Doe", "Other Bank", "9988776655", "OTHRUS33", BigDecimal.ZERO, fx.tellerId));
    }

    @Test
    void invalidTransferType_isRejected_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("500.00"));

        assertThrows(IllegalArgumentException.class, () -> paymentsService.initiateExternalTransfer(
                fx.accountId, "ACH", "Jane Doe", "Other Bank", "9988776655", "OTHRUS33", new BigDecimal("100.00"), fx.tellerId));
        assertThrows(IllegalArgumentException.class, () -> paymentsService.initiateExternalTransfer(
                fx.accountId, null, "Jane Doe", "Other Bank", "9988776655", "OTHRUS33", new BigDecimal("100.00"), fx.tellerId));
    }

    @Test
    void blankBeneficiaryFields_areRejected_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("500.00"));

        assertThrows(IllegalArgumentException.class, () -> paymentsService.initiateExternalTransfer(
                fx.accountId, "WIRE", "   ", "Other Bank", "9988776655", "OTHRUS33", new BigDecimal("100.00"), fx.tellerId));
        assertThrows(IllegalArgumentException.class, () -> paymentsService.initiateExternalTransfer(
                fx.accountId, "WIRE", "Jane Doe", "", "9988776655", "OTHRUS33", new BigDecimal("100.00"), fx.tellerId));
        assertThrows(IllegalArgumentException.class, () -> paymentsService.initiateExternalTransfer(
                fx.accountId, "WIRE", "Jane Doe", "Other Bank", null, "OTHRUS33", new BigDecimal("100.00"), fx.tellerId));
        assertThrows(IllegalArgumentException.class, () -> paymentsService.initiateExternalTransfer(
                fx.accountId, "WIRE", "Jane Doe", "Other Bank", "9988776655", "  ", new BigDecimal("100.00"), fx.tellerId));
    }

    @Test
    void unknownAccount_isRejectedWithAClearMessage_regressionTest() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> paymentsService.initiateExternalTransfer(
                999_999, "WIRE", "Jane Doe", "Other Bank", "9988776655", "OTHRUS33", new BigDecimal("100.00"), 1));
        assertTrue(ex.getMessage().contains("not found"), "Message should explain why: " + ex.getMessage());
    }

    @Test
    void closedAccount_cannotSendAnExternalTransfer_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("500.00"));
        TestDatabase.setAccountStatus(fx.accountId, "CLOSED");

        assertThrows(IllegalArgumentException.class, () -> paymentsService.initiateExternalTransfer(
                fx.accountId, "WIRE", "Jane Doe", "Other Bank", "9988776655", "OTHRUS33", new BigDecimal("100.00"), fx.tellerId));
    }

    @Test
    void insufficientFunds_isRejected() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("50.00"));

        assertThrows(InsufficientFundsException.class, () -> paymentsService.initiateExternalTransfer(
                fx.accountId, "WIRE", "Jane Doe", "Other Bank", "9988776655", "OTHRUS33", new BigDecimal("500.00"), fx.tellerId));
    }

    // ------------------------------------------------------------------
    // payBill() happy path + validation
    // ------------------------------------------------------------------

    @Test
    void payBill_debitsAccountAndReturnsAReference() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("500.00"));
        Biller biller = paymentsService.billers().get(0);

        BillPayment p = paymentsService.payBill(fx.accountId, biller.getId(), new BigDecimal("75.00"), fx.tellerId);

        assertEquals("COMPLETED", p.getStatus());
        assertTrue(p.getReferenceNo().startsWith("BILL-"));
    }

    @Test
    void unknownBiller_isRejectedWithAClearMessage_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("500.00"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> paymentsService.payBill(fx.accountId, 999_999, new BigDecimal("75.00"), fx.tellerId));
        assertTrue(ex.getMessage().contains("not found"), "Message should explain why: " + ex.getMessage());
    }

    @Test
    void closedAccount_cannotPayABill_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("500.00"));
        Biller biller = paymentsService.billers().get(0);
        TestDatabase.setAccountStatus(fx.accountId, "CLOSED");

        assertThrows(IllegalArgumentException.class,
                () -> paymentsService.payBill(fx.accountId, biller.getId(), new BigDecimal("75.00"), fx.tellerId));
    }

    @Test
    void payBill_nonPositiveAmount_isRejected() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("500.00"));
        Biller biller = paymentsService.billers().get(0);

        assertThrows(IllegalArgumentException.class,
                () -> paymentsService.payBill(fx.accountId, biller.getId(), BigDecimal.ZERO, fx.tellerId));
    }

    @Test
    void payBill_insufficientFunds_isRejected() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("10.00"));
        Biller biller = paymentsService.billers().get(0);

        assertThrows(InsufficientFundsException.class,
                () -> paymentsService.payBill(fx.accountId, biller.getId(), new BigDecimal("500.00"), fx.tellerId));
    }
}
