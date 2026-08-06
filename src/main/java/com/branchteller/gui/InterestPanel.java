package com.branchteller.gui;

import com.branchteller.i18n.Messages;
import com.branchteller.model.InterestAccrual;
import com.branchteller.model.User;
import com.branchteller.service.InterestService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class InterestPanel extends JPanel {

    private final InterestService interestService = new InterestService();
    private final User currentUser;

    private final JTextField periodField = new JTextField(
            LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM")), 8);
    private final DefaultTableModel resultsModel =
            new DefaultTableModel(new Object[]{Messages.tr("interest.col.account"), Messages.tr("interest.col.amount"),
                    Messages.tr("interest.col.skipped")}, 0);
    private final JLabel summaryLabel = new JLabel(" ");

    public InterestPanel(User currentUser) {
        this.currentUser = currentUser;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        add(buildControls(), BorderLayout.NORTH);
        add(buildResultsTable(), BorderLayout.CENTER);
    }

    private JPanel buildControls() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(BorderFactory.createTitledBorder(Messages.tr("interest.title")));
        panel.add(new JLabel(Messages.tr("interest.period")));
        panel.add(periodField);
        JButton runBtn = new JButton(Messages.tr("interest.runAccrual"));
        runBtn.addActionListener(e -> runAccrual());
        panel.add(runBtn);
        JButton historyBtn = new JButton(Messages.tr("interest.showHistory"));
        historyBtn.addActionListener(e -> showHistory());
        panel.add(historyBtn);
        panel.add(summaryLabel);
        return panel;
    }

    private JScrollPane buildResultsTable() {
        JTable table = new JTable(resultsModel);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createTitledBorder(Messages.tr("interest.resultsTitle")));
        return scrollPane;
    }

    private void runAccrual() {
        String period = periodField.getText().trim();
        if (!period.matches("\\d{4}-\\d{2}")) {
            JOptionPane.showMessageDialog(this, Messages.tr("interest.invalidPeriodMsg"), Messages.tr("common.invalidInputTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            List<InterestService.AccrualResult> results = interestService.runMonthlyAccrual(period, currentUser.getId());
            resultsModel.setRowCount(0);
            int posted = 0, skipped = 0;
            for (InterestService.AccrualResult r : results) {
                resultsModel.addRow(new Object[]{r.accountNumber, r.amount, r.skipped ? "yes" : "no"});
                if (r.skipped) skipped++; else posted++;
            }
            summaryLabel.setText(Messages.tr("interest.summary", posted, skipped, results.size()));
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void showHistory() {
        String period = periodField.getText().trim();
        try {
            List<InterestAccrual> history = interestService.history(period);
            resultsModel.setRowCount(0);
            for (InterestAccrual a : history) {
                resultsModel.addRow(new Object[]{a.getAccountNumber(), a.getAmount(), "-"});
            }
            summaryLabel.setText(Messages.tr("interest.historySummary", history.size(), period));
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void showDbError(SQLException ex) {
        JOptionPane.showMessageDialog(this, Messages.tr("common.dbErrorPrefix") + ex.getMessage(), Messages.tr("common.error"), JOptionPane.ERROR_MESSAGE);
    }
}
