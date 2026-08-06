package com.branchteller.gui;

import com.branchteller.i18n.Messages;
import com.branchteller.model.SuspiciousActivityFlag;
import com.branchteller.model.User;
import com.branchteller.service.AmlService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.util.List;

public class AmlPanel extends JPanel {

    private final AmlService amlService = new AmlService();
    private final User currentUser;

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{Messages.tr("aml.col.id"), Messages.tr("aml.col.account"), Messages.tr("aml.col.reason"),
                    Messages.tr("aml.col.amount"), Messages.tr("aml.col.flaggedAt"), Messages.tr("aml.col.reviewed")}, 0);
    private final JTable table = new JTable(model);
    private final JCheckBox unreviewedOnly = new JCheckBox(Messages.tr("aml.unreviewedOnly"), true);

    public AmlPanel(User currentUser) {
        this.currentUser = currentUser;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        add(buildControls(), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        UITheme.installStatusRenderer(table, 5);
        loadFlags();
    }

    private JPanel buildControls() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(BorderFactory.createTitledBorder(
                Messages.tr("aml.title", AmlService.REPORTING_THRESHOLD)));
        panel.add(unreviewedOnly);
        unreviewedOnly.addActionListener(e -> loadFlags());
        JButton refreshBtn = new JButton(Messages.tr("common.refresh"));
        refreshBtn.addActionListener(e -> loadFlags());
        panel.add(refreshBtn);
        JButton reviewBtn = new JButton(Messages.tr("aml.markReviewed"));
        reviewBtn.addActionListener(e -> markReviewed());
        panel.add(reviewBtn);
        return panel;
    }

    private void markReviewed() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, Messages.tr("aml.selectFirstMsg"), Messages.tr("common.noSelectionTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        int flagId = (int) model.getValueAt(row, 0);
        try {
            amlService.markReviewed(flagId, currentUser.getId());
            loadFlags();
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void loadFlags() {
        model.setRowCount(0);
        try {
            List<SuspiciousActivityFlag> flags = unreviewedOnly.isSelected()
                    ? amlService.unreviewed()
                    : amlService.all(200);
            for (SuspiciousActivityFlag f : flags) {
                model.addRow(new Object[]{
                        f.getId(), f.getAccountNumber(), f.getReason(), f.getAmount(),
                        f.getFlaggedAt(), f.isReviewed() ? "yes" : "no"
                });
            }
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void showDbError(SQLException ex) {
        JOptionPane.showMessageDialog(this, Messages.tr("common.dbErrorPrefix") + ex.getMessage(), Messages.tr("common.error"), JOptionPane.ERROR_MESSAGE);
    }
}
