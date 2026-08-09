package com.branchteller.service;

import com.branchteller.config.DBConnection;
import com.branchteller.dao.GlDAO;
import com.branchteller.model.GlAccount;
import com.branchteller.model.GlEntryLine;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Double-entry general ledger. Every money-movement service posts a balanced pair of
 * legs (one debit, one credit) here inside its own JDBC transaction, so the GL commits
 * or rolls back atomically with the underlying customer-facing transaction.
 *
 * This class also exposes the three standard accounting views built on top of that
 * ledger: the Journal (every leg, chronological), the Ledger (one account's legs with
 * a running balance), and Financial Reports (Balance Sheet + Income Statement).
 */
public class GlService {

    private final GlDAO glDAO = new GlDAO();

    /** Posts a balanced journal entry: debits debitCode, credits creditCode, both for `amount`. */
    public void post(Connection conn, String debitCode, String creditCode, BigDecimal amount, Integer txnId, String description) throws SQLException {
        if (amount == null || amount.signum() == 0) return;
        glDAO.postEntry(conn, debitCode, txnId, amount, BigDecimal.ZERO, description);
        glDAO.postEntry(conn, creditCode, txnId, BigDecimal.ZERO, amount, description);
    }

    public List<GlAccount> trialBalance() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return glDAO.trialBalance(conn);
        }
    }

    public List<GlAccount> listAccounts() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return glDAO.listAccounts(conn);
        }
    }

    /** General Journal: every posted leg, across every account, in the order it was posted.
     *  `from`/`to` may both be null to show all history. */
    public List<GlEntryLine> journal(LocalDate from, LocalDate to) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return glDAO.journal(conn, from, to);
        }
    }

    /** General Ledger for one account: its posted legs in order, with a running balance
     *  computed in the account's own normal-balance direction (so it reads like a real T-account).
     *
     * <p>When `from` narrows the window, the running balance (and therefore the "Ending balance"
     * shown under the table) is seeded with the account's real balance carried forward from
     * everything posted before `from` -- not reset to zero. Without this, filtering the Ledger
     * tab to, say, "this month" would show an "Ending balance" that is really just that month's
     * net change, silently misrepresenting the account's actual balance to whoever is reading it
     * (a serious defect for a banking ledger). This mirrors the same carry-forward technique
     * {@link #cashFlow} already uses for beginningCash. */
    public List<GlEntryLine> ledger(String glCode, LocalDate from, LocalDate to) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            GlAccount account = glDAO.listAccounts(conn).stream()
                    .filter(a -> a.getCode().equals(glCode)).findFirst()
                    .orElseThrow(() -> new SQLException("Unknown GL account code: " + glCode));
            boolean debitNormal = "DEBIT".equals(account.getNormalBalance());

            BigDecimal running = BigDecimal.ZERO;
            if (from != null) {
                // IMPORTANT: GlDAO's `to` bound is inclusive of the WHOLE day passed in (it
                // filters created_at < to.plusDays(1)), so passing `from` itself here would
                // wrongly include `from`'s own day in the "prior" sum -- double-counting it
                // against the main windowed loop below, which also includes that same day.
                // Passing from.minusDays(1) correctly stops at the day BEFORE the window starts.
                for (GlEntryLine priorLine : glDAO.ledgerForAccount(conn, glCode, null, from.minusDays(1))) {
                    BigDecimal priorDelta = debitNormal
                            ? priorLine.getDebit().subtract(priorLine.getCredit())
                            : priorLine.getCredit().subtract(priorLine.getDebit());
                    running = running.add(priorDelta);
                }
            }

            List<GlEntryLine> lines = glDAO.ledgerForAccount(conn, glCode, from, to);
            for (GlEntryLine line : lines) {
                BigDecimal delta = debitNormal
                        ? line.getDebit().subtract(line.getCredit())
                        : line.getCredit().subtract(line.getDebit());
                running = running.add(delta);
                line.setRunningBalance(running);
            }
            return lines;
        }
    }

    /** Balance Sheet as of today: Assets vs. Liabilities + Equity (+ Net Income To Date, since this
     *  simplified ledger doesn't run period-end closing entries that would fold income/expense into
     *  retained earnings -- so it's added back explicitly to keep the sheet balanced). */
    public BalanceSheet balanceSheet() throws SQLException {
        List<GlAccount> all = trialBalance();
        BalanceSheet bs = new BalanceSheet();
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        for (GlAccount a : all) {
            switch (a.getAccountClass()) {
                case "ASSET":
                    bs.assets.add(a);
                    bs.totalAssets = bs.totalAssets.add(a.getNetBalance());
                    break;
                case "LIABILITY":
                    bs.liabilities.add(a);
                    bs.totalLiabilities = bs.totalLiabilities.add(a.getNetBalance());
                    break;
                case "EQUITY":
                    bs.equity.add(a);
                    bs.totalEquity = bs.totalEquity.add(a.getNetBalance());
                    break;
                case "INCOME":
                    totalIncome = totalIncome.add(a.getNetBalance());
                    break;
                case "EXPENSE":
                    totalExpense = totalExpense.add(a.getNetBalance());
                    break;
                default:
                    break;
            }
        }
        bs.netIncomeToDate = totalIncome.subtract(totalExpense);
        bs.totalLiabilitiesAndEquity = bs.totalLiabilities.add(bs.totalEquity).add(bs.netIncomeToDate);
        return bs;
    }

    /** Income Statement for a period (either bound may be null for "all time"): Income minus
     *  Expense equals Net Income. */
    public IncomeStatement incomeStatement(LocalDate from, LocalDate to) throws SQLException {
        List<GlAccount> all;
        try (Connection conn = DBConnection.getConnection()) {
            all = glDAO.trialBalance(conn, from, to);
        }
        IncomeStatement is = new IncomeStatement();
        for (GlAccount a : all) {
            if ("INCOME".equals(a.getAccountClass())) {
                is.income.add(a);
                is.totalIncome = is.totalIncome.add(a.getNetBalance());
            } else if ("EXPENSE".equals(a.getAccountClass())) {
                is.expense.add(a);
                is.totalExpense = is.totalExpense.add(a.getNetBalance());
            }
        }
        is.netIncome = is.totalIncome.subtract(is.totalExpense);
        return is;
    }

    /** GL code for the cash-and-cash-equivalents account -- the only account a real cash movement
     *  ever touches (loan disbursement/repayment, for example, moves Loans Receivable against Customer
     *  Deposits Control and never touches cash, so it correctly does not appear on this statement). */
    private static final String CASH_CODE = "1000";

    /** Statement of Cash Flows for a period (either bound may be null; null `from` means "from the
     *  beginning", so Beginning Cash Balance is $0). Built entirely from postings to the Cash account,
     *  each one classified Operating/Investing/Financing by looking at its contra account's class:
     *  INCOME/EXPENSE/LIABILITY &rarr; Operating, other ASSET (e.g. Loans Receivable) &rarr; Investing,
     *  EQUITY &rarr; Financing. */
    public CashFlowStatement cashFlow(LocalDate from, LocalDate to) throws SQLException {
        CashFlowStatement cf = new CashFlowStatement();
        try (Connection conn = DBConnection.getConnection()) {
            if (from != null) {
                BigDecimal balance = BigDecimal.ZERO;
                for (GlEntryLine line : glDAO.ledgerForAccount(conn, CASH_CODE, null, from)) {
                    balance = balance.add(line.getDebit()).subtract(line.getCredit());
                }
                cf.beginningCash = balance;
            }

            // Group net cash movement by (category, contra account) so each line reads like
            // "cash paid/received in connection with GL account X", the standard direct-method layout.
            Map<String, BigDecimal> grouped = new LinkedHashMap<>();
            Map<String, String> categoryByKey = new LinkedHashMap<>();
            for (GlEntryLine line : glDAO.cashTouchingEntries(conn, CASH_CODE, from, to)) {
                BigDecimal net = line.getDebit().subtract(line.getCredit()); // + inflow, - outflow
                String category = classify(line.getContraClass());
                String label = line.getContraCode() == null
                        ? "Other / Uncategorized Cash Activity"
                        : line.getContraCode() + " " + line.getContraName();
                String key = category + "|" + label;
                grouped.merge(key, net, BigDecimal::add);
                categoryByKey.put(key, category);
            }
            for (Map.Entry<String, BigDecimal> e : grouped.entrySet()) {
                String category = categoryByKey.get(e.getKey());
                String label = e.getKey().substring(category.length() + 1);
                CashFlowStatement.CategoryLine line = new CashFlowStatement.CategoryLine(label, e.getValue());
                switch (category) {
                    case "INVESTING": cf.investing.add(line); cf.netInvesting = cf.netInvesting.add(e.getValue()); break;
                    case "FINANCING": cf.financing.add(line); cf.netFinancing = cf.netFinancing.add(e.getValue()); break;
                    default: cf.operating.add(line); cf.netOperating = cf.netOperating.add(e.getValue()); break;
                }
            }
            cf.netChangeInCash = cf.netOperating.add(cf.netInvesting).add(cf.netFinancing);
            cf.endingCash = cf.beginningCash.add(cf.netChangeInCash);

            // Reconciliation: the ending balance computed above should always equal the Cash account's
            // actual ledger balance as of `to` -- a self-check that the classification above didn't
            // silently drop or double-count any posting.
            BigDecimal reconciled = BigDecimal.ZERO;
            for (GlEntryLine line : glDAO.ledgerForAccount(conn, CASH_CODE, null, to)) {
                reconciled = reconciled.add(line.getDebit()).subtract(line.getCredit());
            }
            cf.reconciledLedgerBalance = reconciled;
        }
        return cf;
    }

    private String classify(String contraClass) {
        if (contraClass == null) return "OPERATING";
        switch (contraClass) {
            case "ASSET": return "INVESTING";
            case "EQUITY": return "FINANCING";
            case "INCOME":
            case "EXPENSE":
            case "LIABILITY":
            default: return "OPERATING";
        }
    }

    public static class CashFlowStatement {
        public BigDecimal beginningCash = BigDecimal.ZERO;
        public final List<CategoryLine> operating = new ArrayList<>();
        public final List<CategoryLine> investing = new ArrayList<>();
        public final List<CategoryLine> financing = new ArrayList<>();
        public BigDecimal netOperating = BigDecimal.ZERO;
        public BigDecimal netInvesting = BigDecimal.ZERO;
        public BigDecimal netFinancing = BigDecimal.ZERO;
        public BigDecimal netChangeInCash = BigDecimal.ZERO;
        public BigDecimal endingCash = BigDecimal.ZERO;
        public BigDecimal reconciledLedgerBalance = BigDecimal.ZERO;

        public static class CategoryLine {
            public final String label;
            public final BigDecimal amount;
            public CategoryLine(String label, BigDecimal amount) { this.label = label; this.amount = amount; }
        }
    }

    public static class BalanceSheet {
        public final List<GlAccount> assets = new ArrayList<>();
        public final List<GlAccount> liabilities = new ArrayList<>();
        public final List<GlAccount> equity = new ArrayList<>();
        public BigDecimal totalAssets = BigDecimal.ZERO;
        public BigDecimal totalLiabilities = BigDecimal.ZERO;
        public BigDecimal totalEquity = BigDecimal.ZERO;
        public BigDecimal netIncomeToDate = BigDecimal.ZERO;
        public BigDecimal totalLiabilitiesAndEquity = BigDecimal.ZERO;
    }

    public static class IncomeStatement {
        public final List<GlAccount> income = new ArrayList<>();
        public final List<GlAccount> expense = new ArrayList<>();
        public BigDecimal totalIncome = BigDecimal.ZERO;
        public BigDecimal totalExpense = BigDecimal.ZERO;
        public BigDecimal netIncome = BigDecimal.ZERO;
    }
}
