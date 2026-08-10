package com.branchteller.service;

import com.branchteller.model.CashDrawerLog;
import com.branchteller.model.User;
import com.branchteller.support.TestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Senior-QA-style integration coverage for the Cash Drawer feature (CashDrawerService, backing
 * the Cash Drawer tab). There was zero prior test coverage for this feature -- grepping the
 * whole test suite for "CashDrawer" before this review found nothing, and the shared H2 test
 * schema in {@link TestDatabase} didn't even have a {@code cash_drawer_logs} table, meaning this
 * feature could never have been integration-tested against a real database before now (both
 * gaps fixed as part of this review: the table was added to {@code TestDatabase.ensureSchema()},
 * and this class exercises it).
 *
 * <p>The headline finding: {@link CashDrawerService#record} used to insert whatever the caller
 * passed straight into the database with zero validation -- a negative PAID_IN, a $0.00
 * PAID_OUT, a nonzero NO_SALE, or a completely made-up action string would all have been
 * accepted by the service layer, silently defeating the log's whole stated purpose of being a
 * trustworthy record "for reconciliation at end of shift" (see the Cash Drawer Help topic). Fixed
 * with a {@code validate()} method -- see its javadoc for the exact rules, each pinned by a test
 * below.</p>
 */
class CashDrawerIntegrationTest {

    private final CashDrawerService drawerService = new CashDrawerService();

    @BeforeAll
    static void setUpSchema() throws Exception {
        TestDatabase.ensureSchema();
    }

    private User teller(int branchId) throws Exception {
        int tellerId = TestDatabase.insertUserWithBranch("drawerteller", "TELLER", branchId);
        return new User(tellerId, "drawerteller", "Test Teller", "TELLER", branchId);
    }

    // ------------------------------------------------------------------
    // Happy path
    // ------------------------------------------------------------------

    @Test
    void validPaidIn_isRecordedAndReturnedInRecentActivity() throws Exception {
        int branchId = TestDatabase.insertBranch("Drawer Test Branch");
        User teller = teller(branchId);

        CashDrawerLog log = drawerService.record(teller, "PAID_IN", new BigDecimal("50.00"), "Change fund top-up");

        assertNotNull(log.getId());
        List<CashDrawerLog> recent = drawerService.recentActivity(teller, 25);
        assertTrue(recent.stream().anyMatch(l -> l.getId() == log.getId()
                        && "PAID_IN".equals(l.getAction())
                        && 0 == new BigDecimal("50.00").compareTo(l.getAmount())),
                "Expected the recorded PAID_IN to show up in recent activity");
    }

    @Test
    void tillCountOfZero_isAllowed_regressionTest() throws Exception {
        // TILL_COUNT is a point-in-time count of the whole drawer, not a movement -- zero is a
        // legitimate value (drawer counted out and handed back empty), unlike PAID_IN/PAID_OUT/
        // CASH_PULL where zero isn't a real movement at all.
        User teller = teller(TestDatabase.insertBranch("Drawer Test Branch"));

        CashDrawerLog log = drawerService.record(teller, "TILL_COUNT", BigDecimal.ZERO, "Handed back empty");

        assertNotNull(log.getId());
    }

    @Test
    void noSaleWithZeroAmount_isAllowed() throws Exception {
        User teller = teller(TestDatabase.insertBranch("Drawer Test Branch"));

        CashDrawerLog log = drawerService.record(teller, "NO_SALE", BigDecimal.ZERO, null);

        assertNotNull(log.getId());
    }

    // ------------------------------------------------------------------
    // Validation -- the headline finding
    // ------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"PAID_IN", "PAID_OUT", "CASH_PULL"})
    void negativeAmount_isAlwaysRejected_regressionTest(String action) throws Exception {
        User teller = teller(TestDatabase.insertBranch("Drawer Test Branch"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> drawerService.record(teller, action, new BigDecimal("-10.00"), "test"));
        assertTrue(ex.getMessage().toLowerCase().contains("negative"), "Message should explain why: " + ex.getMessage());
    }

    @Test
    void negativeTillCount_isRejected_regressionTest() throws Exception {
        User teller = teller(TestDatabase.insertBranch("Drawer Test Branch"));

        assertThrows(IllegalArgumentException.class,
                () -> drawerService.record(teller, "TILL_COUNT", new BigDecimal("-1.00"), "test"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PAID_IN", "PAID_OUT", "CASH_PULL"})
    void zeroAmount_isRejectedForRealMovements_regressionTest(String action) throws Exception {
        // A "movement" of $0.00 isn't a movement at all -- it would silently log a bogus entry
        // (e.g. from an accidentally-empty amount field defaulting to BigDecimal.ZERO in
        // DrawerPanel) that pollutes the end-of-shift reconciliation log.
        User teller = teller(TestDatabase.insertBranch("Drawer Test Branch"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> drawerService.record(teller, action, BigDecimal.ZERO, "test"));
        assertTrue(ex.getMessage().contains(action), "Message should name the action: " + ex.getMessage());
    }

    @Test
    void noSaleWithNonzeroAmount_isRejected_regressionTest() throws Exception {
        User teller = teller(TestDatabase.insertBranch("Drawer Test Branch"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> drawerService.record(teller, "NO_SALE", new BigDecimal("5.00"), "test"));
        assertTrue(ex.getMessage().contains("NO_SALE"), "Message should explain why: " + ex.getMessage());
    }

    @Test
    void unknownAction_isRejected_regressionTest() throws Exception {
        User teller = teller(TestDatabase.insertBranch("Drawer Test Branch"));

        assertThrows(IllegalArgumentException.class,
                () -> drawerService.record(teller, "REFUND", new BigDecimal("5.00"), "test"));
    }

    @Test
    void nullAction_isRejected() throws Exception {
        User teller = teller(TestDatabase.insertBranch("Drawer Test Branch"));

        assertThrows(IllegalArgumentException.class,
                () -> drawerService.record(teller, null, new BigDecimal("5.00"), "test"));
    }

    @Test
    void nullAmount_isRejected() throws Exception {
        User teller = teller(TestDatabase.insertBranch("Drawer Test Branch"));

        assertThrows(IllegalArgumentException.class,
                () -> drawerService.record(teller, "PAID_IN", null, "test"));
    }

    // ------------------------------------------------------------------
    // recentActivity scoping
    // ------------------------------------------------------------------

    @Test
    void recentActivity_onlyReturnsTheRequestingTellersOwnEntries() throws Exception {
        int branchId = TestDatabase.insertBranch("Drawer Test Branch");
        User tellerA = teller(branchId);
        User tellerB = teller(branchId);

        drawerService.record(tellerA, "PAID_IN", new BigDecimal("20.00"), "A's entry");
        drawerService.record(tellerB, "PAID_IN", new BigDecimal("30.00"), "B's entry");

        List<CashDrawerLog> activityA = drawerService.recentActivity(tellerA, 25);
        assertTrue(activityA.stream().allMatch(l -> l.getTellerId() == tellerA.getId()),
                "A teller's recent activity must not include another teller's entries");
        assertTrue(activityA.stream().noneMatch(l -> "B's entry".equals(l.getNote())));
    }
}
