package com.branchteller.gui;

import com.branchteller.i18n.Messages;
import com.branchteller.model.GlAccount;
import com.branchteller.model.GlEntryLine;
import com.branchteller.service.GlService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/** General Ledger workspace: Trial Balance (account totals), Journal (every posted leg in
 *  chronological order), and Ledger (one account's activity with a running balance) -- the
 *  three standard views onto the same double-entry gl_entries table. Every deposit, withdrawal,
 *  loan disbursement/repayment, and payroll run posts a balanced debit/credit pair here. */
public class GlPanel extends JPanel {

    public GlPanel() {
        setLayout(new BorderLayout());
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(Messages.tr("gl.trialBalanceTab"), new TrialBalanceTab());
        tabs.addTab(Messages.tr("gl.journalTab"), new JournalTab());
        tabs.addTab(Messages.tr("gl.ledgerTab"), new LedgerTab());
        UITheme.styleTabs(tabs);
        add(tabs, BorderLayout.CENTER);
    }

    // ---------------------------------------------------------------- Trial Balance ----

    private static class TrialBalanceTab extends JPanel {
        private final GlService glService = new GlService();
        private final DefaultTableModel model = new DefaultTableModel(
                new Object[]{Messages.tr("gl.tb.col.code"), Messages.tr("gl.tb.col.account"), Messages.tr("gl.tb.col.class"),
                        Messages.tr("gl.tb.col.normalBalance"), Messages.tr("gl.tb.col.debits"), Messages.tr("gl.tb.col.credits"),
                        Messages.tr("gl.tb.col.netBalance")}, 0);
        private final JTable table = new JTable(model);
        private final JLabel totalsLabel = new JLabel(" ");

        TrialBalanceTab() {
            setLayout(new BorderLayout(10, 10));
            setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

            JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
            controls.setBorder(BorderFactory.createTitledBorder(Messages.tr("gl.tb.title")));
            JButton refreshBtn = new JButton(Messages.tr("common.refresh"));
            refreshBtn.addActionListener(e -> load());
            controls.add(refreshBtn);
            controls.add(totalsLabel);

            add(controls, BorderLayout.NORTH);
            add(new JScrollPane(table), BorderLayout.CENTER);

            load();
        }

        private void load() {
            model.setRowCount(0);
            try {
                List<GlAccount> accounts = glService.trialBalance();
                BigDecimal totalDebits = BigDecimal.ZERO;
                BigDecimal totalCredits = BigDecimal.ZERO;
                for (GlAccount a : accounts) {
                    model.addRow(new Object[]{
                            a.getCode(), a.getName(), a.getAccountClass(), a.getNormalBalance(),
                            a.getDebitTotal(), a.getCreditTotal(), a.getNetBalance()
                    });
                    totalDebits = totalDebits.add(a.getDebitTotal());
                    totalCredits = totalCredits.add(a.getCreditTotal());
                }
                totalsLabel.setText(Messages.tr("gl.tb.col.debits") + ": $" + totalDebits + "   " + Messages.tr("gl.tb.col.credits") + ": $" + totalCredits +
                        (totalDebits.compareTo(totalCredits) == 0 ? Messages.tr("gl.tb.balanced") : Messages.tr("gl.tb.outOfBalance")));
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, Messages.tr("common.databaseErrorPrefix") + ex.getMessage(), Messages.tr("common.error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // --------------------------------------------------------------------- Journal ----

    private static class JournalTab extends JPanel {
        private final GlService glService = new GlService();
        private final JTextField fromField = new JTextField(10);
        private final JTextField toField = new JTextField(10);
        private final DefaultTableModel model = new DefaultTableModel(
                new Object[]{Messages.tr("gl.journal.col.postedAt"), Messages.tr("gl.tb.col.code"), Messages.tr("gl.journal.col.account"),
                        Messages.tr("gl.journal.col.debit"), Messages.tr("gl.journal.col.credit"), Messages.tr("gl.journal.col.description"),
                        Messages.tr("gl.journal.col.txnRef")}, 0);
        private final JTable table = new JTable(model);
        private final JLabel countLabel = new JLabel(" ");

        JournalTab() {
            setLayout(new BorderLayout(10, 10));
            setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

            JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
            controls.setBorder(BorderFactory.createTitledBorder(Messages.tr("gl.journal.title")));
            controls.add(new JLabel(Messages.tr("gl.journal.from")));
            controls.add(fromField);
            controls.add(new JLabel(Messages.tr("gl.journal.to")));
            controls.add(toField);
            JButton refreshBtn = new JButton(Messages.tr("common.refresh"));
            refreshBtn.addActionListener(e -> load());
            controls.add(refreshBtn);
            controls.add(countLabel);

            add(controls, BorderLayout.NORTH);
            add(new JScrollPane(table), BorderLayout.CENTER);

            load();
        }

        private void load() {
            LocalDate from = parseOptionalDate(fromField.getText());
            LocalDate to = parseOptionalDate(toField.getText());
            if (from == PARSE_ERROR || to == PARSE_ERROR) {
                JOptionPane.showMessageDialog(this, Messages.tr("gl.journal.invalidDateMsg"),
                        Messages.tr("common.invalidDateTitle"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            model.setRowCount(0);
            try {
                List<GlEntryLine> lines = glService.journal(from, to);
                for (GlEntryLine line : lines) {
                    model.addRow(new Object[]{
                            line.getPostedAt(), line.getCode(), line.getAccountName(),
                            line.getDebit(), line.getCredit(), line.getDescription(),
                            line.getTxnId() == null ? "" : line.getTxnId()
                    });
                }
                countLabel.setText(Messages.tr("gl.journal.entriesCount", lines.size()));
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, Messages.tr("common.databaseErrorPrefix") + ex.getMessage(), Messages.tr("common.error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ---------------------------------------------------------------------- Ledger ----

    private static class LedgerTab extends JPanel {
        private final GlService glService = new GlService();
        private final JComboBox<String> accountCombo = new JComboBox<>();
        private final JTextField fromField = new JTextField(10);
        private final JTextField toField = new JTextField(10);
        private final DefaultTableModel model = new DefaultTableModel(
                new Object[]{Messages.tr("gl.ledger.col.postedAt"), Messages.tr("gl.ledger.col.description"), Messages.tr("gl.ledger.col.txnRef"),
                        Messages.tr("gl.ledger.col.debit"), Messages.tr("gl.ledger.col.credit"), Messages.tr("gl.ledger.col.runningBalance")}, 0);
        private final JTable table = new JTable(model);
        private final JLabel endingBalanceLabel = new JLabel(" ");

        LedgerTab() {
            setLayout(new BorderLayout(10, 10));
            setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

            JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
            controls.setBorder(BorderFactory.createTitledBorder(Messages.tr("gl.ledger.title")));
            controls.add(new JLabel(Messages.tr("gl.ledger.account")));
            controls.add(accountCombo);
            controls.add(new JLabel(Messages.tr("gl.journal.from")));
            controls.add(fromField);
            controls.add(new JLabel(Messages.tr("gl.journal.to")));
            controls.add(toField);
            JButton refreshBtn = new JButton(Messages.tr("common.refresh"));
            refreshBtn.addActionListener(e -> load());
            controls.add(refreshBtn);

            JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
            south.add(endingBalanceLabel);

            add(controls, BorderLayout.NORTH);
            add(new JScrollPane(table), BorderLayout.CENTER);
            add(south, BorderLayout.SOUTH);

            loadAccounts();
        }

        private void loadAccounts() {
            try {
                for (GlAccount a : glService.listAccounts()) {
                    accountCombo.addItem(a.getCode() + " - " + a.getName());
                }
                if (accountCombo.getItemCount() > 0) {
                    accountCombo.setSelectedIndex(0);
                    load();
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, Messages.tr("common.databaseErrorPrefix") + ex.getMessage(), Messages.tr("common.error"), JOptionPane.ERROR_MESSAGE);
            }
        }

        private void load() {
            Object selected = accountCombo.getSelectedItem();
            if (selected == null) return;
            String code = selected.toString().split(" - ", 2)[0];

            LocalDate from = parseOptionalDate(fromField.getText());
            LocalDate to = parseOptionalDate(toField.getText());
            if (from == PARSE_ERROR || to == PARSE_ERROR) {
                JOptionPane.showMessageDialog(this, Messages.tr("gl.journal.invalidDateMsg"),
                        Messages.tr("common.invalidDateTitle"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            model.setRowCount(0);
            try {
                List<GlEntryLine> lines = glService.ledger(code, from, to);
                for (GlEntryLine line : lines) {
                    model.addRow(new Object[]{
                            line.getPostedAt(), line.getDescription(),
                            line.getTxnId() == null ? "" : line.getTxnId(),
                            line.getDebit(), line.getCredit(), line.getRunningBalance()
                    });
                }
                if (lines.isEmpty()) {
                    endingBalanceLabel.setText(Messages.tr("gl.ledger.noActivity"));
                } else {
                    endingBalanceLabel.setText(Messages.tr("gl.ledger.endingBalance", lines.get(lines.size() - 1).getRunningBalance()));
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, Messages.tr("common.databaseErrorPrefix") + ex.getMessage(), Messages.tr("common.error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ------------------------------------------------------------------- shared util ----

    /** Sentinel returned when the text couldn't be parsed as a date (distinct from "blank -> no bound"). */
    private static final LocalDate PARSE_ERROR = LocalDate.MIN;

    private static LocalDate parseOptionalDate(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException ex) {
            return PARSE_ERROR;
        }
    }
}
