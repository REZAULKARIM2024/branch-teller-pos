package com.branchteller.gui;

import com.branchteller.i18n.Messages;
import com.branchteller.model.GlAccount;
import com.branchteller.service.GlService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/** Financial statements built on top of the General Ledger: a Balance Sheet (as of today),
 *  an Income Statement (for a chosen period), and a Statement of Cash Flows. All three are
 *  derived entirely from gl_entries via GlService, so they stay in sync with every transaction
 *  automatically. */
public class FinancialReportsPanel extends JPanel {

    public FinancialReportsPanel() {
        setLayout(new BorderLayout());
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(Messages.tr("finrep.balanceSheetTab"), new BalanceSheetTab());
        tabs.addTab(Messages.tr("finrep.incomeStatementTab"), new IncomeStatementTab());
        tabs.addTab(Messages.tr("finrep.cashFlowTab"), new CashFlowTab());
        UITheme.styleTabs(tabs);
        add(tabs, BorderLayout.CENTER);
    }

    // ---------------------------------------------------------------- Balance Sheet ----

    private static class BalanceSheetTab extends JPanel {
        private final GlService glService = new GlService();
        private final DefaultTableModel model = new DefaultTableModel(new Object[]{Messages.tr("finrep.col.line"), Messages.tr("finrep.col.amount")}, 0);
        private GlService.BalanceSheet lastResult;

        BalanceSheetTab() {
            setLayout(new BorderLayout(10, 10));
            setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

            JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
            controls.setBorder(BorderFactory.createTitledBorder(Messages.tr("finrep.bs.title")));
            JButton refreshBtn = new JButton(Messages.tr("common.refresh"));
            refreshBtn.addActionListener(e -> load());
            controls.add(refreshBtn);
            JButton exportBtn = new JButton(Messages.tr("common.exportCsv"));
            exportBtn.addActionListener(e -> export());
            controls.add(exportBtn);

            JTable table = new JTable(model);
            add(controls, BorderLayout.NORTH);
            add(new JScrollPane(table), BorderLayout.CENTER);

            load();
        }

        private void load() {
            model.setRowCount(0);
            try {
                GlService.BalanceSheet bs = glService.balanceSheet();
                lastResult = bs;

                model.addRow(new Object[]{Messages.tr("finrep.bs.assets"), ""});
                for (GlAccount a : bs.assets) model.addRow(new Object[]{"  " + a.getCode() + " " + a.getName(), a.getNetBalance()});
                model.addRow(new Object[]{Messages.tr("finrep.bs.totalAssets"), bs.totalAssets});
                model.addRow(new Object[]{"", ""});

                model.addRow(new Object[]{Messages.tr("finrep.bs.liabilities"), ""});
                for (GlAccount a : bs.liabilities) model.addRow(new Object[]{"  " + a.getCode() + " " + a.getName(), a.getNetBalance()});
                model.addRow(new Object[]{Messages.tr("finrep.bs.totalLiabilities"), bs.totalLiabilities});
                model.addRow(new Object[]{"", ""});

                model.addRow(new Object[]{Messages.tr("finrep.bs.equity"), ""});
                for (GlAccount a : bs.equity) model.addRow(new Object[]{"  " + a.getCode() + " " + a.getName(), a.getNetBalance()});
                model.addRow(new Object[]{Messages.tr("finrep.bs.netIncomeToDate"), bs.netIncomeToDate});
                model.addRow(new Object[]{Messages.tr("finrep.bs.totalEquity"), bs.totalEquity.add(bs.netIncomeToDate)});
                model.addRow(new Object[]{"", ""});

                model.addRow(new Object[]{Messages.tr("finrep.bs.totalLiabEquity"), bs.totalLiabilitiesAndEquity});
                model.addRow(new Object[]{bs.totalAssets.compareTo(bs.totalLiabilitiesAndEquity) == 0
                        ? Messages.tr("finrep.bs.balanced") : Messages.tr("finrep.bs.outOfBalance"), ""});
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, Messages.tr("common.databaseErrorPrefix") + ex.getMessage(), Messages.tr("common.error"), JOptionPane.ERROR_MESSAGE);
            }
        }

        private void export() {
            if (lastResult == null) return;
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new java.io.File("balance-sheet-" + LocalDate.now() + ".csv"));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            try (FileWriter w = new FileWriter(chooser.getSelectedFile())) {
                w.write("NY Financial Bank - Balance Sheet as of " + LocalDate.now() + "\n\n");
                for (int r = 0; r < model.getRowCount(); r++) {
                    w.write(model.getValueAt(r, 0) + "," + model.getValueAt(r, 1) + "\n");
                }
                JOptionPane.showMessageDialog(this, Messages.tr("common.exportedMsg", chooser.getSelectedFile().getAbsolutePath()),
                        Messages.tr("common.exportedTitle"), JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, Messages.tr("common.writeErrorMsg") + ex.getMessage(), Messages.tr("common.error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ------------------------------------------------------------- Income Statement ----

    private static class IncomeStatementTab extends JPanel {
        private final GlService glService = new GlService();
        private final JTextField fromField = new JTextField(10);
        private final JTextField toField = new JTextField(10);
        private final DefaultTableModel model = new DefaultTableModel(new Object[]{Messages.tr("finrep.col.line"), Messages.tr("finrep.col.amount")}, 0);
        private GlService.IncomeStatement lastResult;
        private LocalDate lastFrom, lastTo;

        IncomeStatementTab() {
            setLayout(new BorderLayout(10, 10));
            setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

            JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
            controls.setBorder(BorderFactory.createTitledBorder(Messages.tr("finrep.is.title")));
            controls.add(new JLabel(Messages.tr("finrep.is.from")));
            controls.add(fromField);
            controls.add(new JLabel(Messages.tr("finrep.is.to")));
            controls.add(toField);
            JButton refreshBtn = new JButton(Messages.tr("common.refresh"));
            refreshBtn.addActionListener(e -> load());
            controls.add(refreshBtn);
            JButton exportBtn = new JButton(Messages.tr("common.exportCsv"));
            exportBtn.addActionListener(e -> export());
            controls.add(exportBtn);

            JTable table = new JTable(model);
            add(controls, BorderLayout.NORTH);
            add(new JScrollPane(table), BorderLayout.CENTER);

            load();
        }

        private LocalDate parseOptionalDate(String text) throws DateTimeParseException {
            if (text == null || text.trim().isEmpty()) return null;
            return LocalDate.parse(text.trim());
        }

        private void load() {
            LocalDate from, to;
            try {
                from = parseOptionalDate(fromField.getText());
                to = parseOptionalDate(toField.getText());
            } catch (DateTimeParseException ex) {
                JOptionPane.showMessageDialog(this, Messages.tr("gl.journal.invalidDateMsg"), Messages.tr("common.invalidDateTitle"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            model.setRowCount(0);
            try {
                GlService.IncomeStatement is = glService.incomeStatement(from, to);
                lastResult = is;
                lastFrom = from;
                lastTo = to;

                model.addRow(new Object[]{Messages.tr("finrep.is.income"), ""});
                for (GlAccount a : is.income) model.addRow(new Object[]{"  " + a.getCode() + " " + a.getName(), a.getNetBalance()});
                model.addRow(new Object[]{Messages.tr("finrep.is.totalIncome"), is.totalIncome});
                model.addRow(new Object[]{"", ""});

                model.addRow(new Object[]{Messages.tr("finrep.is.expense"), ""});
                for (GlAccount a : is.expense) model.addRow(new Object[]{"  " + a.getCode() + " " + a.getName(), a.getNetBalance()});
                model.addRow(new Object[]{Messages.tr("finrep.is.totalExpense"), is.totalExpense});
                model.addRow(new Object[]{"", ""});

                model.addRow(new Object[]{is.netIncome.signum() >= 0 ? Messages.tr("finrep.is.netIncome") : Messages.tr("finrep.is.netLoss"), is.netIncome});
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, Messages.tr("common.databaseErrorPrefix") + ex.getMessage(), Messages.tr("common.error"), JOptionPane.ERROR_MESSAGE);
            }
        }

        private void export() {
            if (lastResult == null) return;
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new java.io.File("income-statement-" + LocalDate.now() + ".csv"));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            try (FileWriter w = new FileWriter(chooser.getSelectedFile())) {
                w.write("NY Financial Bank - Income Statement, "
                        + (lastFrom == null ? "all-time" : lastFrom) + " to " + (lastTo == null ? "present" : lastTo) + "\n\n");
                for (int r = 0; r < model.getRowCount(); r++) {
                    w.write(model.getValueAt(r, 0) + "," + model.getValueAt(r, 1) + "\n");
                }
                JOptionPane.showMessageDialog(this, Messages.tr("common.exportedMsg", chooser.getSelectedFile().getAbsolutePath()),
                        Messages.tr("common.exportedTitle"), JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, Messages.tr("common.writeErrorMsg") + ex.getMessage(), Messages.tr("common.error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // -------------------------------------------------------------------- Cash Flow ----

    private static class CashFlowTab extends JPanel {
        private final GlService glService = new GlService();
        private final JTextField fromField = new JTextField(10);
        private final JTextField toField = new JTextField(10);
        private final DefaultTableModel model = new DefaultTableModel(new Object[]{Messages.tr("finrep.col.line"), Messages.tr("finrep.col.amount")}, 0);
        private GlService.CashFlowStatement lastResult;
        private LocalDate lastFrom, lastTo;

        CashFlowTab() {
            setLayout(new BorderLayout(10, 10));
            setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

            JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
            controls.setBorder(BorderFactory.createTitledBorder(Messages.tr("finrep.cf.title")));
            controls.add(new JLabel(Messages.tr("finrep.cf.from")));
            controls.add(fromField);
            controls.add(new JLabel(Messages.tr("finrep.cf.to")));
            controls.add(toField);
            JButton refreshBtn = new JButton(Messages.tr("common.refresh"));
            refreshBtn.addActionListener(e -> load());
            controls.add(refreshBtn);
            JButton exportBtn = new JButton(Messages.tr("common.exportCsv"));
            exportBtn.addActionListener(e -> export());
            controls.add(exportBtn);

            JTable table = new JTable(model);
            add(controls, BorderLayout.NORTH);
            add(new JScrollPane(table), BorderLayout.CENTER);

            load();
        }

        private LocalDate parseOptionalDate(String text) throws DateTimeParseException {
            if (text == null || text.trim().isEmpty()) return null;
            return LocalDate.parse(text.trim());
        }

        private void load() {
            LocalDate from, to;
            try {
                from = parseOptionalDate(fromField.getText());
                to = parseOptionalDate(toField.getText());
            } catch (DateTimeParseException ex) {
                JOptionPane.showMessageDialog(this, Messages.tr("gl.journal.invalidDateMsg"), Messages.tr("common.invalidDateTitle"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            model.setRowCount(0);
            try {
                GlService.CashFlowStatement cf = glService.cashFlow(from, to);
                lastResult = cf;
                lastFrom = from;
                lastTo = to;

                model.addRow(new Object[]{Messages.tr("finrep.cf.beginningCash"), cf.beginningCash});
                model.addRow(new Object[]{"", ""});

                model.addRow(new Object[]{Messages.tr("finrep.cf.operating"), ""});
                if (cf.operating.isEmpty()) model.addRow(new Object[]{Messages.tr("finrep.cf.noOperating"), ""});
                for (GlService.CashFlowStatement.CategoryLine l : cf.operating) model.addRow(new Object[]{"  " + l.label, l.amount});
                model.addRow(new Object[]{Messages.tr("finrep.cf.netOperating"), cf.netOperating});
                model.addRow(new Object[]{"", ""});

                model.addRow(new Object[]{Messages.tr("finrep.cf.investing"), ""});
                if (cf.investing.isEmpty()) model.addRow(new Object[]{Messages.tr("finrep.cf.noInvesting"), ""});
                for (GlService.CashFlowStatement.CategoryLine l : cf.investing) model.addRow(new Object[]{"  " + l.label, l.amount});
                model.addRow(new Object[]{Messages.tr("finrep.cf.netInvesting"), cf.netInvesting});
                model.addRow(new Object[]{"", ""});

                model.addRow(new Object[]{Messages.tr("finrep.cf.financing"), ""});
                if (cf.financing.isEmpty()) model.addRow(new Object[]{Messages.tr("finrep.cf.noFinancing"), ""});
                for (GlService.CashFlowStatement.CategoryLine l : cf.financing) model.addRow(new Object[]{"  " + l.label, l.amount});
                model.addRow(new Object[]{Messages.tr("finrep.cf.netFinancing"), cf.netFinancing});
                model.addRow(new Object[]{"", ""});

                model.addRow(new Object[]{Messages.tr("finrep.cf.netChange"), cf.netChangeInCash});
                model.addRow(new Object[]{Messages.tr("finrep.cf.endingCash"), cf.endingCash});
                model.addRow(new Object[]{cf.endingCash.compareTo(cf.reconciledLedgerBalance) == 0
                        ? Messages.tr("finrep.cf.reconciled")
                        : Messages.tr("finrep.cf.notReconciled", cf.reconciledLedgerBalance), ""});
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, Messages.tr("common.databaseErrorPrefix") + ex.getMessage(), Messages.tr("common.error"), JOptionPane.ERROR_MESSAGE);
            }
        }

        private void export() {
            if (lastResult == null) return;
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new java.io.File("cash-flow-" + LocalDate.now() + ".csv"));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            try (FileWriter w = new FileWriter(chooser.getSelectedFile())) {
                w.write("NY Financial Bank - Statement of Cash Flows, "
                        + (lastFrom == null ? "all-time" : lastFrom) + " to " + (lastTo == null ? "present" : lastTo) + "\n\n");
                for (int r = 0; r < model.getRowCount(); r++) {
                    w.write(model.getValueAt(r, 0) + "," + model.getValueAt(r, 1) + "\n");
                }
                JOptionPane.showMessageDialog(this, Messages.tr("common.exportedMsg", chooser.getSelectedFile().getAbsolutePath()),
                        Messages.tr("common.exportedTitle"), JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, Messages.tr("common.writeErrorMsg") + ex.getMessage(), Messages.tr("common.error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
