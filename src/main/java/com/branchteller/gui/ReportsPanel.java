package com.branchteller.gui;

import com.branchteller.i18n.Messages;
import com.branchteller.service.ReportService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

public class ReportsPanel extends JPanel {

    private final ReportService reportService = new ReportService();

    private final JTextField dateField = new JTextField(LocalDate.now().toString(), 10);
    private final DefaultTableModel summaryModel =
            new DefaultTableModel(new Object[]{Messages.tr("reports.col.type"), Messages.tr("reports.col.count"),
                    Messages.tr("reports.col.total")}, 0);

    public ReportsPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        add(buildControls(), BorderLayout.NORTH);
        add(new JScrollPane(new JTable(summaryModel)), BorderLayout.CENTER);
    }

    private JPanel buildControls() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(BorderFactory.createTitledBorder(Messages.tr("reports.title")));
        panel.add(new JLabel(Messages.tr("reports.date")));
        panel.add(dateField);

        JButton previewBtn = new JButton(Messages.tr("reports.preview"));
        previewBtn.addActionListener(e -> preview());
        panel.add(previewBtn);

        JButton exportBtn = new JButton(Messages.tr("reports.exportCsv"));
        exportBtn.addActionListener(e -> export());
        panel.add(exportBtn);

        return panel;
    }

    private LocalDate parseDate() {
        try {
            return LocalDate.parse(dateField.getText().trim());
        } catch (DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this, Messages.tr("common.invalidDateMsg"), Messages.tr("common.invalidDateTitle"), JOptionPane.WARNING_MESSAGE);
            return null;
        }
    }

    private void preview() {
        LocalDate date = parseDate();
        if (date == null) return;
        try {
            List<ReportService.DailySummaryLine> summary = reportService.dailySummary(date);
            summaryModel.setRowCount(0);
            for (ReportService.DailySummaryLine line : summary) {
                summaryModel.addRow(new Object[]{line.txnType, line.count, line.totalAmount});
            }
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void export() {
        LocalDate date = parseDate();
        if (date == null) return;

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("daily-report-" + date + ".csv"));
        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;

        try {
            String path = reportService.exportDailyReportCsv(date, chooser.getSelectedFile().getAbsolutePath());
            JOptionPane.showMessageDialog(this, Messages.tr("reports.exportedMsg") + path, Messages.tr("reports.exportedTitle"), JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException ex) {
            showDbError(ex);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, Messages.tr("reports.writeErrorMsg") + ex.getMessage(), Messages.tr("reports.exportErrorTitle"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showDbError(SQLException ex) {
        JOptionPane.showMessageDialog(this, Messages.tr("common.dbErrorPrefix") + ex.getMessage(), Messages.tr("common.error"), JOptionPane.ERROR_MESSAGE);
    }
}
