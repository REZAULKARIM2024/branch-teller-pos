package com.branchteller.service;

import com.branchteller.config.DBConnection;
import com.branchteller.dao.AccountDAO;
import com.branchteller.dao.TransactionDAO;
import com.branchteller.model.Account;
import com.branchteller.model.Transaction;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Builds a plain-text account statement, ready to hand to PrintableText or show in a dialog. */
public class StatementService {

    private final AccountDAO accountDAO = new AccountDAO();
    private final TransactionDAO transactionDAO = new TransactionDAO();
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public List<String> buildStatement(String accountNumber, LocalDate from, LocalDate to) throws SQLException {
        List<String> lines = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection()) {
            Account account = accountDAO.findByAccountNumber(conn, accountNumber)
                    .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountNumber));

            lines.add("NY Financial Bank - Account Statement");
            lines.add("Account: " + account.getAccountNumber() + "  (" + account.getCustomerName() + ")");
            lines.add("Type: " + account.getAccountType() + "   Period: " + from + " to " + to);
            lines.add("Current balance: $" + account.getBalance());
            lines.add("-".repeat(70));
            lines.add(String.format("%-17s %-14s %12s %14s", "Date", "Type", "Amount", "Balance After"));
            lines.add("-".repeat(70));

            List<Transaction> txns = transactionDAO.findByAccountIdAndDateRange(conn, account.getId(), from, to);
            if (txns.isEmpty()) {
                lines.add("(no transactions in this period)");
            }
            for (Transaction t : txns) {
                lines.add(String.format("%-17s %-14s %12s %14s",
                        t.getCreatedAt() == null ? "" : t.getCreatedAt().format(TS_FMT),
                        t.getTxnType(),
                        t.getAmount(),
                        t.getBalanceAfter()));
            }
        }
        return lines;
    }
}
