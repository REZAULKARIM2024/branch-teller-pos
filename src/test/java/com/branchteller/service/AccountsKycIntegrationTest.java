package com.branchteller.service;

import com.branchteller.model.Account;
import com.branchteller.model.Customer;
import com.branchteller.support.TestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Senior-QA-style integration coverage for the Accounts & KYC feature (CustomerService,
 * backed by the real CustomerDAO/AccountDAO/AuditService against a shared H2 database --
 * see support/TestDatabase). {@code EndToEndFlowTest} already covers the three headline
 * happy/blocked paths (PENDING blocks account opening, VERIFIED allows it, REJECTED blocks
 * it); this class exists to close the gaps a senior QA review of that coverage would flag:
 *
 * <ul>
 *   <li>Duplicate phone numbers -- the DB has a UNIQUE constraint on {@code phone}, but
 *       {@code CustomerService.register()} never pre-checks it, so the failure mode is a
 *       raw {@link SQLException} bubbling out of a second registration attempt.</li>
 *   <li>Audit trail accuracy for KYC transitions -- {@code verifyKyc}/{@code rejectKyc} used
 *       to hardcode the audit log's "before" value as the literal string "PENDING" no matter
 *       what the customer's actual prior status was. That's fixed in this same change (both
 *       methods now look up the real prior status first); the tests here are the regression
 *       coverage that proves it, using a REJECTED-then-VERIFIED transition where the old
 *       hardcoded value would have been provably wrong.</li>
 *   <li>Fail-fast on a nonexistent customer id -- {@code verifyKyc}/{@code rejectKyc} used to
 *       silently no-op (zero DB rows updated, no exception, plus a fabricated audit row) when
 *       given an id that doesn't exist. That's also fixed here to throw
 *       {@link IllegalArgumentException}, consistent with {@code openAccount}'s existing
 *       behavior for the same class of error.</li>
 *   <li>Permissive (non-guarded) re-verify/re-reject transitions, and that rejecting KYC after
 *       an account is already open does not retroactively touch that account.</li>
 *   <li>Account opening for non-SAVINGS types, multiple accounts per customer, unique account
 *       number generation, and audit trail coverage for ACCOUNT_OPENED.</li>
 * </ul>
 */
class AccountsKycIntegrationTest {

    private final CustomerService customerService = new CustomerService();

    @BeforeAll
    static void setUpSchema() throws Exception {
        TestDatabase.ensureSchema();
    }

    private static String uniquePhone() {
        return "555-" + TestDatabase.nextSeq();
    }

    // ------------------------------------------------------------------
    // Registration
    // ------------------------------------------------------------------

    @Test
    void register_persistsDataAndWritesAccurateAuditTrail() throws Exception {
        String phone = uniquePhone();
        Customer c = customerService.register("Audit Trail Test", phone, "audit@example.test", "1 Test St");

        assertEquals("PENDING", c.getKycStatus());
        assertEquals("Audit Trail Test", TestDatabase.fullNameOf(c.getId()));
        assertEquals("PENDING", TestDatabase.kycStatusOf(c.getId()));

        assertEquals(1, TestDatabase.auditCountFor("customer", c.getId(), "CUSTOMER_REGISTERED"));
        assertEquals("PENDING", TestDatabase.auditAfterValue("customer", c.getId(), "CUSTOMER_REGISTERED"));
    }

    @Test
    void register_duplicatePhoneNumber_violatesUniqueConstraint() throws Exception {
        String phone = uniquePhone();
        customerService.register("First Owner Of Phone", phone, "first@example.test", "1 Test St");

        // CustomerService.register() has no app-level pre-check for an existing phone (the
        // DAO's findByPhone() lookup exists but is unused by register()) -- so a duplicate
        // is only ever caught by the database's own UNIQUE constraint, surfacing as a raw
        // SQLException rather than a friendly validation error. This test locks in that a
        // duplicate is still rejected end-to-end, even though the failure mode is a low-level
        // one that the GUI would currently show to a teller as a raw DB error message.
        assertThrows(SQLException.class,
                () -> customerService.register("Second Owner Of Same Phone", phone, "second@example.test", "2 Test St"));
    }

    // ------------------------------------------------------------------
    // KYC verify / reject -- audit trail accuracy
    // ------------------------------------------------------------------

    @Test
    void verifyKyc_pendingToVerified_recordsAccuratePriorStatus() throws Exception {
        Customer c = customerService.register("Verify Audit Test", uniquePhone(), "v@example.test", "addr");
        int actorId = TestDatabase.insertUser("manager", "MANAGER");

        customerService.verifyKyc(c.getId(), actorId);

        assertEquals("VERIFIED", TestDatabase.kycStatusOf(c.getId()));
        assertEquals("PENDING", TestDatabase.auditBeforeValue("customer", c.getId(), "KYC_VERIFIED"),
                "Before-value should reflect the customer's actual prior status");
        assertEquals("VERIFIED", TestDatabase.auditAfterValue("customer", c.getId(), "KYC_VERIFIED"));
    }

    @Test
    void rejectKyc_thenVerifyKyc_auditRecordsActualPriorStatus_notHardcodedPending() throws Exception {
        Customer c = customerService.register("Reject Then Verify Test", uniquePhone(), "rtv@example.test", "addr");
        int actorId = TestDatabase.insertUser("manager", "MANAGER");

        // PENDING -> REJECTED
        customerService.rejectKyc(c.getId(), actorId);
        assertEquals("REJECTED", TestDatabase.kycStatusOf(c.getId()));
        assertEquals("PENDING", TestDatabase.auditBeforeValue("customer", c.getId(), "KYC_REJECTED"));

        // REJECTED -> VERIFIED: the regression check. Before the fix, verifyKyc() always
        // logged the literal string "PENDING" as the before-value no matter what -- which
        // would have been provably wrong here, since the customer's real prior status at
        // this point is REJECTED, not PENDING.
        customerService.verifyKyc(c.getId(), actorId);
        assertEquals("VERIFIED", TestDatabase.kycStatusOf(c.getId()));
        assertEquals("REJECTED", TestDatabase.auditBeforeValue("customer", c.getId(), "KYC_VERIFIED"),
                "Before-value must reflect the real prior status (REJECTED), not a hardcoded PENDING");
        assertEquals("VERIFIED", TestDatabase.auditAfterValue("customer", c.getId(), "KYC_VERIFIED"));
    }

    @Test
    void verifyKyc_reVerifyingAlreadyVerifiedCustomer_isPermittedAndAuditedEachTime() throws Exception {
        Customer c = customerService.register("Re-Verify Test", uniquePhone(), "rv@example.test", "addr");
        int actorId = TestDatabase.insertUser("manager", "MANAGER");

        customerService.verifyKyc(c.getId(), actorId);
        customerService.verifyKyc(c.getId(), actorId); // no state-machine guard today -- documents current behavior

        assertEquals("VERIFIED", TestDatabase.kycStatusOf(c.getId()));
        assertEquals(2, TestDatabase.auditCountFor("customer", c.getId(), "KYC_VERIFIED"),
                "Each verify call should write its own audit row, even if the status doesn't change");
        assertEquals("VERIFIED", TestDatabase.auditBeforeValue("customer", c.getId(), "KYC_VERIFIED"),
                "Second call's before-value should be VERIFIED (the real prior status), not PENDING");
    }

    @Test
    void rejectKyc_onVerifiedCustomer_revokesStatus_butDoesNotAffectAlreadyOpenedAccounts() throws Exception {
        Customer c = customerService.register("Revoke After Open Test", uniquePhone(), "rao@example.test", "addr");
        int actorId = TestDatabase.insertUser("manager", "MANAGER");
        customerService.verifyKyc(c.getId(), actorId);

        int branchId = TestDatabase.insertBranch("Branch " + TestDatabase.nextSeq());
        Account account = customerService.openAccount(c.getId(), branchId, "SAVINGS", new BigDecimal("3.00"), actorId);

        customerService.rejectKyc(c.getId(), actorId);

        assertEquals("REJECTED", TestDatabase.kycStatusOf(c.getId()));
        assertEquals("VERIFIED", TestDatabase.auditBeforeValue("customer", c.getId(), "KYC_REJECTED"),
                "Before-value should show the customer was VERIFIED, not PENDING, at the moment of rejection");
        // The account opened while still VERIFIED is untouched -- rejection is not retroactive.
        assertEquals(0, BigDecimal.ZERO.compareTo(TestDatabase.balanceOf(account.getId())));
    }

    @Test
    void verifyKyc_onNonexistentCustomer_throwsIllegalArgumentException() throws Exception {
        int actorId = TestDatabase.insertUser("manager", "MANAGER");
        assertThrows(IllegalArgumentException.class, () -> customerService.verifyKyc(9_999_999, actorId));
    }

    @Test
    void rejectKyc_onNonexistentCustomer_throwsIllegalArgumentException() throws Exception {
        int actorId = TestDatabase.insertUser("manager", "MANAGER");
        assertThrows(IllegalArgumentException.class, () -> customerService.rejectKyc(9_999_999, actorId));
    }

    // ------------------------------------------------------------------
    // Account opening
    // ------------------------------------------------------------------

    @Test
    void openAccount_onNonexistentCustomer_throwsIllegalArgumentException() throws Exception {
        int actorId = TestDatabase.insertUser("manager", "MANAGER");
        int branchId = TestDatabase.insertBranch("Branch " + TestDatabase.nextSeq());
        assertThrows(IllegalArgumentException.class,
                () -> customerService.openAccount(9_999_999, branchId, "SAVINGS", BigDecimal.ZERO, actorId));
    }

    @Test
    void openAccount_writesAccurateAuditTrailEntry_forNonSavingsType() throws Exception {
        Customer c = customerService.register("Current Account Test", uniquePhone(), "cur@example.test", "addr");
        int actorId = TestDatabase.insertUser("manager", "MANAGER");
        customerService.verifyKyc(c.getId(), actorId);
        int branchId = TestDatabase.insertBranch("Branch " + TestDatabase.nextSeq());

        Account account = customerService.openAccount(c.getId(), branchId, "CURRENT", new BigDecimal("0.00"), actorId);

        assertEquals("CURRENT", account.getAccountType());
        assertEquals(1, TestDatabase.auditCountFor("account", account.getId(), "ACCOUNT_OPENED"));
        assertEquals("CURRENT", TestDatabase.auditAfterValue("account", account.getId(), "ACCOUNT_OPENED"));
    }

    @Test
    void openAccount_multipleAccountTypesForSameCustomer_allSucceedIndependently() throws Exception {
        Customer c = customerService.register("Multi Account Test", uniquePhone(), "multi@example.test", "addr");
        int actorId = TestDatabase.insertUser("manager", "MANAGER");
        customerService.verifyKyc(c.getId(), actorId);
        int branchId = TestDatabase.insertBranch("Branch " + TestDatabase.nextSeq());

        Account savings = customerService.openAccount(c.getId(), branchId, "SAVINGS", new BigDecimal("2.50"), actorId);
        Account current = customerService.openAccount(c.getId(), branchId, "CURRENT", BigDecimal.ZERO, actorId);
        Account fd = customerService.openAccount(c.getId(), branchId, "FD", new BigDecimal("5.00"), actorId);

        assertEquals("SAVINGS", savings.getAccountType());
        assertEquals("CURRENT", current.getAccountType());
        assertEquals("FD", fd.getAccountType());
        assertNotEquals(savings.getAccountNumber(), current.getAccountNumber());
        assertNotEquals(current.getAccountNumber(), fd.getAccountNumber());
        assertNotEquals(savings.getAccountNumber(), fd.getAccountNumber());
    }

    @Test
    void openAccount_generatesUniqueAccountNumbers_acrossManyAccounts() throws Exception {
        Customer c = customerService.register("Unique Number Test", uniquePhone(), "uniq@example.test", "addr");
        int actorId = TestDatabase.insertUser("manager", "MANAGER");
        customerService.verifyKyc(c.getId(), actorId);
        int branchId = TestDatabase.insertBranch("Branch " + TestDatabase.nextSeq());

        List<String> numbers = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Account a = customerService.openAccount(c.getId(), branchId, "SAVINGS", BigDecimal.ZERO, actorId);
            numbers.add(a.getAccountNumber());
        }

        assertEquals(20, numbers.size());
        assertEquals(20, numbers.stream().distinct().count(), "All generated account numbers must be unique");
    }

    // ------------------------------------------------------------------
    // Listing
    // ------------------------------------------------------------------

    @Test
    void findAll_returnsAllRegisteredCustomers_regardlessOfKycStatus() throws Exception {
        Customer pending = customerService.register("List Test Pending", uniquePhone(), "p@example.test", "addr");
        Customer verified = customerService.register("List Test Verified", uniquePhone(), "vv@example.test", "addr");
        int actorId = TestDatabase.insertUser("manager", "MANAGER");
        customerService.verifyKyc(verified.getId(), actorId);
        Customer rejected = customerService.register("List Test Rejected", uniquePhone(), "rr@example.test", "addr");
        customerService.rejectKyc(rejected.getId(), actorId);

        List<Customer> all = customerService.findAll();
        List<Integer> ids = all.stream().map(Customer::getId).toList();

        assertTrue(ids.contains(pending.getId()));
        assertTrue(ids.contains(verified.getId()));
        assertTrue(ids.contains(rejected.getId()));
    }
}
