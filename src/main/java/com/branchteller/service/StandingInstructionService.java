package com.branchteller.service;

import com.branchteller.config.DBConnection;
import com.branchteller.dao.StandingInstructionDAO;
import com.branchteller.model.Account;
import com.branchteller.model.StandingInstruction;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Recurring auto-pay transfers. "Run Due Instructions" executes any ACTIVE instruction
 *  whose next_run_date has arrived, via BankingService.transfer, then advances the schedule. */
public class StandingInstructionService {

    private final StandingInstructionDAO siDAO = new StandingInstructionDAO();
    private final BankingService bankingService = new BankingService();

    public StandingInstruction create(int fromAccountId, String toAccountNumber, BigDecimal amount,
                                       String frequency, LocalDate startDate, String note) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            StandingInstruction si = new StandingInstruction();
            si.setFromAccountId(fromAccountId);
            si.setToAccountNumber(toAccountNumber);
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

    public void pause(int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) { siDAO.updateStatus(conn, id, "PAUSED"); }
    }

    public void resume(int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) { siDAO.updateStatus(conn, id, "ACTIVE"); }
    }

    public void cancel(int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) { siDAO.updateStatus(conn, id, "CANCELLED"); }
    }

    /** Executes every ACTIVE instruction due today or earlier. Returns a per-instruction result summary. */
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
            } catch (InsufficientFundsException | SQLException ex) {
                logRun(si.getId(), "FAILED", ex.getMessage());
                results.add("Instruction #" + si.getId() + " FAILED: " + ex.getMessage());
            }
        }
        return results;
    }

    private void logRun(int instructionId, String status, String detail) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            siDAO.logRun(conn, instructionId, LocalDate.now(), status, detail);
        }
    }
}
