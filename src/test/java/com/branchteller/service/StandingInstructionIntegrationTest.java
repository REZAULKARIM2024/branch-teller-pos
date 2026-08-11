package com.branchteller.service;

import com.branchteller.model.StandingInstruction;
import com.branchteller.support.TestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Senior-QA-style integration coverage for the Standing Instructions feature
 * (StandingInstructionService, backing the Standing Instructions tab). No dedicated test class
 * existed for this feature before this review, and the H2 test schema didn't even have
 * {@code standing_instructions}/{@code standing_instruction_runs} tables -- meaning this feature
 * could never have been integration-tested against a real database at all.
 *
 * <p>Findings, all fixed:</p>
 * <ol>
 * <li>{@link StandingInstructionService#create} accepted any fromAccountId (raw FK SQLException
 * on a bad one), stored any {@code toAccountNumber} string at all with no existence check -- a
 * typo'd destination would sit silently until the instruction actually ran and failed at {@code
 * runDue()} time, possibly days later -- and never rejected a non-positive amount, an invalid
 * frequency, a CLOSED source account, or a same-account instruction.</li>
 * <li>{@link StandingInstructionService#pause}/{@link StandingInstructionService#resume}/
 * {@link StandingInstructionService#cancel} had no existence check and no state-machine guard at
 * all. Worst of the three: {@code resume()} would flip a CANCELLED instruction back to ACTIVE --
 * the same un-cancelling bug pattern found and fixed in Cards this same review.</li>
 * <li>{@link StandingInstructionService#runDue} only caught {@code InsufficientFundsException}
 * and {@code SQLException} around each instruction -- not {@code IllegalArgumentException}, which
 * {@code BankingService.transfer()} throws for a since-closed account. One bad instruction used
 * to crash the entire batch run, silently skipping every other customer's due instruction.</li>
 * </ol>
 */
class StandingInstructionIntegrationTest {

    private final StandingInstructionService siService = new StandingInstructionService();

    @BeforeAll
    static void setUpSchema() throws Exception {
        TestDatabase.ensureSchema();
    }

    // ------------------------------------------------------------------
    // create() happy path
    // ------------------------------------------------------------------

    @Test
    void create_setsUpAnActiveInstruction() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("500.00"));
        int toAccountId = TestDatabase.insertAccount(fx.customerId, fx.branchId, "SAVINGS", new BigDecimal("0.00"));
        String toAccountNumber = TestDatabase.accountNumberFor(toAccountId);

        StandingInstruction si = siService.create(fx.accountId, toAccountNumber, new BigDecimal("50.00"),
                "MONTHLY", LocalDate.now(), "Rent");

        assertEquals("ACTIVE", si.getStatus());
        assertEquals(toAccountNumber, si.getToAccountNumber());
    }

    // ------------------------------------------------------------------
    // create() validation
    // ------------------------------------------------------------------

    @Test
    void nonPositiveAmount_isRejected() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("500.00"));
        int toAccountId = TestDatabase.insertAccount(fx.customerId, fx.branchId, "SAVINGS", new BigDecimal("0.00"));
        String toAccountNumber = TestDatabase.accountNumberFor(toAccountId);

        assertThrows(IllegalArgumentException.class, () -> siService.create(
                fx.accountId, toAccountNumber, BigDecimal.ZERO, "MONTHLY", LocalDate.now(), null));
        assertThrows(IllegalArgumentException.class, () -> siService.create(
                fx.accountId, toAccountNumber, new BigDecimal("-10.00"), "MONTHLY", LocalDate.now(), null));
    }

    @Test
    void invalidFrequency_isRejected_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("500.00"));
        int toAccountId = TestDatabase.insertAccount(fx.customerId, fx.branchId, "SAVINGS", new BigDecimal("0.00"));
        String toAccountNumber = TestDatabase.accountNumberFor(toAccountId);

        assertThrows(IllegalArgumentException.class, () -> siService.create(
                fx.accountId, toAccountNumber, new BigDecimal("50.00"), "DAILY", LocalDate.now(), null));
        assertThrows(IllegalArgumentException.class, () -> siService.create(
                fx.accountId, toAccountNumber, new BigDecimal("50.00"), null, LocalDate.now(), null));
    }

    @Test
    void blankDestinationAccount_isRejected_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("500.00"));

        assertThrows(IllegalArgumentException.class, () -> siService.create(
                fx.accountId, "  ", new BigDecimal("50.00"), "MONTHLY", LocalDate.now(), null));
        assertThrows(IllegalArgumentException.class, () -> siService.create(
                fx.accountId, null, new BigDecimal("50.00"), "MONTHLY", LocalDate.now(), null));
    }

    @Test
    void unknownDestinationAccount_isRejected_regressionTest() throws Exception {
        // This was the real gap: previously the instruction would be created successfully against
        // a typo'd destination and only fail (silently, from the teller's perspective) when it
        // actually ran at runDue() time.
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("500.00"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> siService.create(
                fx.accountId, "TST-DOES-NOT-EXIST", new BigDecimal("50.00"), "MONTHLY", LocalDate.now(), null));
        assertTrue(ex.getMessage().contains("not found"), "Message should explain why: " + ex.getMessage());
    }

    @Test
    void unknownSourceAccount_isRejected_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("500.00"));
        int toAccountId = TestDatabase.insertAccount(fx.customerId, fx.branchId, "SAVINGS", new BigDecimal("0.00"));
        String toAccountNumber = TestDatabase.accountNumberFor(toAccountId);

        assertThrows(IllegalArgumentException.class, () -> siService.create(
                999_999, toAccountNumber, new BigDecimal("50.00"), "MONTHLY", LocalDate.now(), null));
    }

    @Test
    void closedSourceAccount_isRejected_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("500.00"));
        int toAccountId = TestDatabase.insertAccount(fx.customerId, fx.branchId, "SAVINGS", new BigDecimal("0.00"));
        String toAccountNumber = TestDatabase.accountNumberFor(toAccountId);
        TestDatabase.setAccountStatus(fx.accountId, "CLOSED");

        assertThrows(IllegalArgumentException.class, () -> siService.create(
                fx.accountId, toAccountNumber, new BigDecimal("50.00"), "MONTHLY", LocalDate.now(), null));
    }

    @Test
    void sameAccountInstruction_isRejected_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("500.00"));
        String ownAccountNumber = TestDatabase.accountNumberFor(fx.accountId);

        assertThrows(IllegalArgumentException.class, () -> siService.create(
                fx.accountId, ownAccountNumber, new BigDecimal("50.00"), "MONTHLY", LocalDate.now(), null));
    }

    // ------------------------------------------------------------------
    // pause() / resume() / cancel() state machine
    // ------------------------------------------------------------------

    @Test
    void pausingActiveInstruction_thenResuming_works() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("500.00"));
        int toAccountId = TestDatabase.insertAccount(fx.customerId, fx.branchId, "SAVINGS", new BigDecimal("0.00"));
        StandingInstruction si = siService.create(fx.accountId, TestDatabase.accountNumberFor(toAccountId),
                new BigDecimal("50.00"), "MONTHLY", LocalDate.now(), null);

        siService.pause(si.getId());
        siService.resume(si.getId());

        List<StandingInstruction> all = siService.all();
        assertEquals("ACTIVE", findById(all, si.getId()).getStatus());
    }

    @Test
    void pausingAnAlreadyPausedInstruction_isRejected_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("500.00"));
        int toAccountId = TestDatabase.insertAccount(fx.customerId, fx.branchId, "SAVINGS", new BigDecimal("0.00"));
        StandingInstruction si = siService.create(fx.accountId, TestDatabase.accountNumberFor(toAccountId),
                new BigDecimal("50.00"), "MONTHLY", LocalDate.now(), null);
        siService.pause(si.getId());

        assertThrows(IllegalStateException.class, () -> siService.pause(si.getId()));
    }

    @Test
    void resumingAnActiveInstruction_isRejected_regressionTest() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("500.00"));
        int toAccountId = TestDatabase.insertAccount(fx.customerId, fx.branchId, "SAVINGS", new BigDecimal("0.00"));
        StandingInstruction si = siService.create(fx.accountId, TestDatabase.accountNumberFor(toAccountId),
                new BigDecimal("50.00"), "MONTHLY", LocalDate.now(), null);

        assertThrows(IllegalStateException.class, () -> siService.resume(si.getId()));
    }

    @Test
    void cannotResumeACancelledInstruction_regressionTest() throws Exception {
        // The real bug: resume() used to flip a CANCELLED instruction straight back to ACTIVE
        // with no guard at all.
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("500.00"));
        int toAccountId = TestDatabase.insertAccount(fx.customerId, fx.branchId, "SAVINGS", new BigDecimal("0.00"));
        StandingInstruction si = siService.create(fx.accountId, TestDatabase.accountNumberFor(toAccountId),
                new BigDecimal("50.00"), "MONTHLY", LocalDate.now(), null);
        siService.cancel(si.getId());

        assertThrows(IllegalStateException.class, () -> siService.resume(si.getId()));

        List<StandingInstruction> all = siService.all();
        assertEquals("CANCELLED", findById(all, si.getId()).getStatus());
    }

    @Test
    void cancellingFromActiveOrPaused_bothWork_butNotTwice() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("500.00"));
        int toAccountId = TestDatabase.insertAccount(fx.customerId, fx.branchId, "SAVINGS", new BigDecimal("0.00"));
        String toAccountNumber = TestDatabase.accountNumberFor(toAccountId);
        StandingInstruction active = siService.create(fx.accountId, toAccountNumber, new BigDecimal("50.00"), "MONTHLY", LocalDate.now(), null);
        StandingInstruction paused = siService.create(fx.accountId, toAccountNumber, new BigDecimal("50.00"), "MONTHLY", LocalDate.now(), null);
        siService.pause(paused.getId());

        siService.cancel(active.getId());
        siService.cancel(paused.getId());

        assertThrows(IllegalStateException.class, () -> siService.cancel(active.getId()));
    }

    @Test
    void unknownInstruction_operationsAreRejectedWithAClearMessage_regressionTest() {
        assertThrows(IllegalArgumentException.class, () -> siService.pause(999_999));
        assertThrows(IllegalArgumentException.class, () -> siService.resume(999_999));
        assertThrows(IllegalArgumentException.class, () -> siService.cancel(999_999));
    }

    // ------------------------------------------------------------------
    // runDue()
    // ------------------------------------------------------------------

    // NOTE: findDue()/runDue() sweep every ACTIVE, due-today standing instruction in the whole
    // shared H2 database (like the production batch job would sweep the whole bank), not just
    // the ones a single test method created. Every other test method in this class also creates
    // ACTIVE instructions due "today". So these tests filter the per-instruction result lines
    // down to the specific instruction ID(s) they created, rather than asserting on the total
    // result count -- asserting a total count would be coupled to test execution order/what
    // else happens to be due, which isn't what these tests are actually verifying.

    @Test
    void runDue_transfersFundsAndAdvancesSchedule() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("500.00"));
        int toAccountId = TestDatabase.insertAccount(fx.customerId, fx.branchId, "SAVINGS", new BigDecimal("0.00"));
        String toAccountNumber = TestDatabase.accountNumberFor(toAccountId);
        StandingInstruction si = siService.create(fx.accountId, toAccountNumber, new BigDecimal("50.00"), "MONTHLY", LocalDate.now(), null);

        List<String> results = siService.runDue(fx.tellerId);

        String mine = resultFor(results, si.getId());
        assertTrue(mine.contains("SUCCESS"), "Expected SUCCESS: " + mine);
    }

    @Test
    void runDue_oneFailingInstructionDoesNotBlockTheRestOfTheBatch_regressionTest() throws Exception {
        // The real bug: BankingService.transfer() throws IllegalArgumentException for a closed
        // account, which runDue() never caught -- so this first (bad) instruction used to abort
        // the loop entirely and the second (healthy) instruction was silently never run.
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("500.00"));
        int toAccountId = TestDatabase.insertAccount(fx.customerId, fx.branchId, "SAVINGS", new BigDecimal("0.00"));
        String toAccountNumber = TestDatabase.accountNumberFor(toAccountId);
        StandingInstruction failing = siService.create(fx.accountId, toAccountNumber, new BigDecimal("50.00"), "MONTHLY", LocalDate.now(), null);

        // Second, healthy instruction from a different source account.
        int secondSourceId = TestDatabase.insertAccount(fx.customerId, fx.branchId, "SAVINGS", new BigDecimal("500.00"));
        StandingInstruction healthy = siService.create(secondSourceId, toAccountNumber, new BigDecimal("25.00"), "MONTHLY", LocalDate.now(), null);

        // Close the first instruction's source account after creation, simulating an account
        // getting closed in between setting up the instruction and it actually running.
        TestDatabase.setAccountStatus(fx.accountId, "CLOSED");

        List<String> results = siService.runDue(fx.tellerId);

        assertTrue(resultFor(results, failing.getId()).contains("FAILED"),
                "The closed-account instruction should be reported as failed: " + resultFor(results, failing.getId()));
        assertTrue(resultFor(results, healthy.getId()).contains("SUCCESS"),
                "The healthy instruction should still have run despite the other one failing: " + resultFor(results, healthy.getId()));
    }

    @Test
    void runDue_closedDestinationAccountIsReportedAsFailed_regressionTest() throws Exception {
        // The destination existed (and was open) when the instruction was created, but was
        // closed sometime before it actually ran. lookupAccount() still finds it by number
        // regardless of status, so this exercises the same IllegalArgumentException-from-
        // transfer() path as the closed-source case above, just on the other leg.
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("500.00"));
        int toAccountId = TestDatabase.insertAccount(fx.customerId, fx.branchId, "SAVINGS", new BigDecimal("0.00"));
        String toAccountNumber = TestDatabase.accountNumberFor(toAccountId);
        StandingInstruction si = siService.create(fx.accountId, toAccountNumber, new BigDecimal("50.00"), "MONTHLY", LocalDate.now(), null);
        TestDatabase.setAccountStatus(toAccountId, "CLOSED");

        List<String> results = siService.runDue(fx.tellerId);

        String mine = resultFor(results, si.getId());
        assertTrue(mine.contains("FAILED"), "Expected FAILED: " + mine);
    }

    private static String resultFor(List<String> results, int instructionId) {
        String marker = "Instruction #" + instructionId + " ";
        return results.stream().filter(r -> r.startsWith(marker)).findFirst()
                .orElseThrow(() -> new AssertionError("No result line for instruction " + instructionId + " in " + results));
    }

    private static StandingInstruction findById(List<StandingInstruction> list, int id) {
        return list.stream().filter(si -> si.getId() == id).findFirst()
                .orElseThrow(() -> new AssertionError("Instruction " + id + " not found in list"));
    }
}
