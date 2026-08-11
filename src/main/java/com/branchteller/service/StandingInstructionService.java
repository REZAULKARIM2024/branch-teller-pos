package com.branchteller.service;

import com.branchteller.config.DBConnection;
import com.branchteller.dao.AccountDAO;
import com.branchteller.dao.StandingInstructionDAO;
import com.branchteller.model.Account;
import com.branchteller.model.StandingInstruction;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Recurring auto-pay transfers. "Run Due Instructions" executes any ACTIVE instruction
 *  whose next_run_date has arrived, via BankingService.transfer, then advances the schedule. */
public class StandingInstructionService {

    private static final Set<String> VALID_FREQUENCIES = Set.of("WEEKLY", "MONTHLY");

    private final StandingInstructionDAO siDAO = new StandingInstructionDAO();
    private final AccountDAO accountDAO = new AccountDAO();
    private final BankingService bankingService = new BankingService();

    /**
     * QA finding (fixed): this method used to accept any fromAccountId at all (raw FK
     * SQLException on a bad one), never validated {@code toAccountNumber} beyond storing
     * whatever string was given -- so a typo'd destination would sit silently until the
     * instruction actually ran and failed at {@code runDue()} time, potentially days or weeks
     * later -- and never rejected a non-positive amount or an invalid frequency. All are now
     * rejected up front, the same "catch it at creation, not at execution" principle applied
     * everywhere else in this review. Also newly rejects a CLOSED source account (a standing
     * instruction repeatedly pulls money from it, so it belongs with the other money-moving
     * "CLOSED blocks" features) and a self-referencing instruction (from == to).
     */
    public StandingInstruction create(int fromAccountId, String toAccountNumber, BigDecimal amount,
                                       String frequency, LocalDate startDate, String note) throws SQLException {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (frequency == null || !VALID_FREQUENCIES.contains(frequency)) {
            throw new IllegalArgumentException("Frequency must be WEEKLY or MONTHLY, got: " + frequency);
        }
        if (toAccountNumber == null || toAccountNumber.isBlank()) {
            throw new IllegalArgumentException("Destination account is required");
        }

        try (Connection conn = DBConnection.getConnection()) {
            Account from = accountDAO.findByIdForUpdate(conn, fromAccountId)
                    .orElseThrow(() -> new IllegalArgumentException("Account not found: " + fromAccountId));
            if ("CLOSED".equals(from.getStatus())) {
                throw new IllegalArgumentException(
                        "Account " + from.getAccountNumber() + " is closed and cannot have a standing instruction set up against it");
            }
            Account to = accountDAO.findByAccountNumber(conn, toAccountNumber.trim())
                    .orElseThrow(() -> new IllegalArgumentException("Destination account not found: " + toAccountNumber));
            if (from.getId() == to.getId()) {
                throw new IllegalArgumentException("Cannot set up a standing instruction to the same account");
            }

            StandingInstruction si = new StandingInstruction();
            si.setFromAccountId(fromAccountId);
            si.setToAccountNumber(to.getAccountNumber());
            si.setAmount(amount);
            si.setFrequency(frequency);
            si.setNextRunDate(startDate);
            si.setNote(note);
            int id = siDAO.insert(conn, si);
            si.setId(id);
            si.setStatus("ACTIVE");
            return si;
        }
    }

    public List<StandingInstruction> all() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return siDAO.findAll(conn);
        }
    }

    /**
     * QA finding (fixed): pause()/resume()/cancel() used to call updateStatus() completely
     * unconditionally -- no check the instruction even existed, and no state-machine guard at
     * all. Worst of the three: resume() would happily flip a CANCELLED instruction back to
     * ACTIVE, effectively un-cancelling what's supposed to be a permanent, terminal state (the
     * same bug pattern found and fixed in Cards' block/unblock/cancel this same review). Fixed
     * with the same exists-and-in-expected-state guard used throughout this codebase: pause()
     * only from ACTIVE, resume() only from PAUSED, cancel() from either ACTIVE or PAUSED but
     * never from an already-CANCELLED instruction.
     */
    public void pause(int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            StandingInstruction si = requireInstruction(conn, id);
            if (!"ACTIVE".equals(si.getStatus())) {
                throw new IllegalStateException("Instruction " + id + " can't be paused (current status: " + si.getStatus() + ")");
            }
            siDAO.updateStatus(conn, id, "PAUSED");
        }
    }

    public void resume(int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            StandingInstruction si = requireInstruction(conn, id);
            if (!"PAUSED".equals(si.getStatus())) {
                throw new IllegalStateException("Instruction " + id + " can't be resumed (current status: " + si.getStatus() + ")");
            }
            siDAO.updateStatus(conn, id, "ACTIVE");
        }
    }

    public void cancel(int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            StandingInstruction si = requireInstruction(conn, id);
            if ("CANCELLED".equals(si.getStatus())) {
                throw new IllegalStateException("Instruction " + id + " is already cancelled");
            }
            siDAO.updateStatus(conn, id, "CANCELLED");
        }
    }

    /**
     * Executes every ACTIVE instruction due today or earlier. Returns a per-instruction result summary.
     *
     * <p>QA finding (fixed): this loop used to catch only {@code InsufficientFundsException} and
     * {@code SQLException} around each instruction's transfer -- but {@code
     * BankingService.transfer()} can also throw {@code IllegalArgumentException} (e.g. the source
     * account was closed sometime after the instruction was created, or the destination account
     * was too). That exception was uncaught here, so a single bad instruction would abort the
     * entire batch run, silently skipping every other customer's due instruction that hadn't run
     * yet -- a real production-grade bug for a scheduled batch job. Now caught and logged as a
     * FAILED run for that one instruction only, the rest of the batch continues.</p>
     */
    public List<String> runDue(int systemTellerId) throws SQLException {
        List<String> results = new java.util.ArrayList<>();
        List<StandingInstruction> due;
        try (Connection conn = DBConnection.getConnection()) {
            due = siDAO.findDue(conn, LocalDate.now());
        }

        for (StandingInstruction si : due) {
            try {
                Optional<Account> dest = bankingService.lookupAccount(si.getToAccountNumber());
                if (dest.isEmpty()) {
                    logRun(si.getId(), "FAILED", "Destination account " + si.getToAccountNumber() + " not found");
                    results.add("Instruction #" + si.getId() + " FAILED: destination not found");
                    continue;
                }
                Optional<Account> from = bankingService.lookupAccount(si.getFromAccountNumber());
                int fromId = from.isPresent() ? from.get().getId() : si.getFromAccountId();

                bankingService.transfer(fromId, dest.get().getId(), si.getAmount(), systemTellerId,
                        "Standing instruction #" + si.getId() + (si.getNote() == null ? "" : " - " + si.getNote()));

                LocalDate nextDate = "WEEKLY".equals(si.getFrequency()) ? si.getNextRunDate().plusWeeks(1) : si.getNextRunDate().plusMonths(1);
                try (Connection conn = DBConnection.getConnection()) {
                    siDAO.advanceNextRunDate(conn, si.getId(), nextDate);
                }
                logRun(si.getId(), "SUCCESS", "Transferred $" + si.getAmount() + " to " + si.getToAccountNumber());
                results.add("Instruction #" + si.getId() + " SUCCESS: $" + si.getAmount() + " -> " + si.getToAccountNumber());
            } catch (InsufficientFundsException | SQLException | IllegalArgumentException ex) {
                logRun(si.getId(), "FAILED", ex.getMessage());
                results.add("Instruction #" + si.getId() + " FAILED: " + ex.getMessage());
            }
        }
        return results;
    }

    private StandingInstruction requireInstruction(Connection conn, int id) throws SQLException {
        return siDAO.findById(conn, id)
                .orElseThrow(() -> new IllegalArgumentException("Standing instruction not found: " + id));
    }

    private void logRun(int instructionId, String status, String detail) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            siDAO.logRun(conn, instructionId, LocalDate.now(), status, detail);
        }
    }
}
