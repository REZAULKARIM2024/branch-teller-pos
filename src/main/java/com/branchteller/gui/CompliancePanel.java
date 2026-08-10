package com.branchteller.gui;

import com.branchteller.i18n.Messages;
import com.branchteller.model.RegulatoryReport;
import com.branchteller.model.ScreeningResult;
import com.branchteller.model.SuspiciousActivityFlag;
import com.branchteller.model.User;
import com.branchteller.service.AmlService;
import com.branchteller.service.ComplianceService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.util.List;

/** Sanctions/PEP screening plus SAR/CTR regulatory report filing -- builds on the basic
 *  threshold-based AmlPanel flags with an escalation path to formal filings. */
public class CompliancePanel extends JPanel {

    private final ComplianceService complianceService = new ComplianceService();
    private final AmlService amlService = new AmlService();
    private final User currentUser;

    private final JTextField customerIdField = new JTextField(8);
    private final DefaultTableModel screeningModel = new DefaultTableModel(
            new Object[]{Messages.tr("compliance.col.id"), Messages.tr("compliance.col.customer"), Messages.tr("compliance.col.matchedAgainst"),
                    Messages.tr("compliance.col.score"), Messages.tr("compliance.col.status"), Messages.tr("compliance.col.screenedAt")}, 0);
    private final JTable screeningTable = new JTable(screeningModel);

    private final DefaultTableModel flagsModel = new DefaultTableModel(
            new Object[]{Messages.tr("compliance.flagsCol.id"), Messages.tr("compliance.flagsCol.account"),
                    Messages.tr("compliance.flagsCol.reason"), Messages.tr("compliance.flagsCol.amount")}, 0);
    private final JTable flagsTable = new JTable(flagsModel);
    private final JComboBox<String> reportTypeCombo = new JComboBox<>(new String[]{"SAR", "CTR"});

    private final DefaultTableModel reportsModel = new DefaultTableModel(
            new Object[]{Messages.tr("compliance.reportsCol.id"), Messages.tr("compliance.reportsCol.type"), Messages.tr("compliance.reportsCol.ref"),
                    Messages.tr("compliance.reportsCol.account"), Messages.tr("compliance.reportsCol.filedBy"), Messages.tr("compliance.reportsCol.filedAt")}, 0);
    private final JTable reportsTable = new JTable(reportsModel);

    public CompliancePanel(User currentUser) {
        this.currentUser = currentUser;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(Messages.tr("compliance.screeningTab"), buildScreeningTab());
        tabs.addTab(Messages.tr("compliance.reportsTab"), buildReportsTab());
        add(tabs, BorderLayout.CENTER);
        UITheme.styleTabs(tabs);

        UITheme.installStatusRenderer(screeningTable, 4);

        loadScreeningResults();
        loadUnreviewedFlags();
        loadReports();
    }

    private JPanel buildScreeningTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(new JLabel(Messages.tr("compliance.customerId")));
        controls.add(customerIdField);
        JButton screenBtn = new JButton(Messages.tr("compliance.screenBtn"));
        screenBtn.addActionListener(e -> screen());
        JButton refreshBtn = new JButton(Messages.tr("common.refresh"));
        refreshBtn.addActionListener(e -> loadScreeningResults());
        controls.add(screenBtn);
        controls.add(refreshBtn);
        panel.add(controls, BorderLayout.NORTH);
        panel.add(new JScrollPane(screeningTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildReportsTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JPanel top = new JPanel(new BorderLayout(6, 6));
        top.setBorder(BorderFactory.createTitledBorder(Messages.tr("compliance.unreviewedFlagsTitle")));
        top.add(new JScrollPane(flagsTable), BorderLayout.CENTER);

        JPanel fileRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        fileRow.add(new JLabel(Messages.tr("compliance.reportType")));
        fileRow.add(reportTypeCombo);
        JButton fileBtn = new JButton(Messages.tr("compliance.fileBtn"));
        fileBtn.addActionListener(e -> fileReport());
        fileRow.add(fileBtn);
        top.add(fileRow, BorderLayout.SOUTH);

        JPanel bottom = new JPanel(new BorderLayout(6, 6));
        bottom.setBorder(BorderFactory.createTitledBorder(Messages.tr("compliance.filedReportsTitle")));
        bottom.add(new JScrollPane(reportsTable), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottom);
        split.setResizeWeight(0.5);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private void screen() {
        try {
            int customerId = Integer.parseInt(customerIdField.getText().trim());
            ScreeningResult r = complianceService.screenCustomer(customerId, currentUser.getId());
            JOptionPane.showMessageDialog(this, Messages.tr("compliance.screeningResultMsg", r.getStatus(), r.getMatchScore()));
            loadScreeningResults();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, Messages.tr("common.enterNumericIdMsg"), Messages.tr("common.invalidInputTitle"), JOptionPane.WARNING_MESSAGE);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), Messages.tr("common.notFoundTitle"), JOptionPane.WARNING_MESSAGE);
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void fileReport() {
        int row = flagsTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, Messages.tr("compliance.selectFlagFirstMsg"), Messages.tr("common.noSelectionTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        int flagId = (int) flagsModel.getValueAt(row, 0);
        String narrative = JOptionPane.showInputDialog(this, Messages.tr("compliance.narrativePrompt"));
        if (narrative == null || narrative.isBlank()) return;

        try {
            List<SuspiciousActivityFlag> flags = amlService.unreviewed();
            SuspiciousActivityFlag flag = flags.stream().filter(f -> f.getId() == flagId).findFirst().orElse(null);
            if (flag == null) {
                JOptionPane.showMessageDialog(this, Messages.tr("compliance.flagNotFoundMsg"), Messages.tr("common.error"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            // fileReport() now marks the flag reviewed itself, atomically, as part of the same
            // filing transaction (see ComplianceService.fileReport() javadoc) -- no separate
            // amlService.markReviewed() call needed here anymore.
            RegulatoryReport r = complianceService.fileReport((String) reportTypeCombo.getSelectedItem(), flag, currentUser.getId(), narrative);
            JOptionPane.showMessageDialog(this, Messages.tr("compliance.filedMsg", r.getReportType(), r.getReferenceNo()));
            loadUnreviewedFlags();
            loadReports();
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void loadScreeningResults() {
        screeningModel.setRowCount(0);
        try {
            List<ScreeningResult> results = complianceService.allScreeningResults();
            for (ScreeningResult r : results) {
                screeningModel.addRow(new Object[]{r.getId(), r.getCustomerName(),
                        r.getMatchedName() == null ? "-" : r.getMatchedName(), r.getMatchScore(), r.getStatus(), r.getScreenedAt()});
            }
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void loadUnreviewedFlags() {
        flagsModel.setRowCount(0);
        try {
            List<SuspiciousActivityFlag> flags = amlService.unreviewed();
            for (SuspiciousActivityFlag f : flags) {
                flagsModel.addRow(new Object[]{f.getId(), f.getAccountNumber(), f.getReason(), f.getAmount()});
            }
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void loadReports() {
        reportsModel.setRowCount(0);
        try {
            List<RegulatoryReport> reports = complianceService.allReports();
            for (RegulatoryReport r : reports) {
                reportsModel.addRow(new Object[]{r.getId(), r.getReportType(), r.getReferenceNo(),
                        r.getRelatedAccountNumber(), r.getFiledByName(), r.getFiledAt()});
            }
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void showDbError(SQLException ex) {
        JOptionPane.showMessageDialog(this, Messages.tr("common.databaseErrorPrefix") + ex.getMessage(), Messages.tr("common.error"), JOptionPane.ERROR_MESSAGE);
    }
}
