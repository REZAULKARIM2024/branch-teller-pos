package com.branchteller.gui;

import com.branchteller.i18n.Messages;
import com.branchteller.model.AuditLog;
import com.branchteller.service.AuditService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.util.List;

public class AuditLogPanel extends JPanel {

    private final AuditService auditService = new AuditService();
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{Messages.tr("audit.col.when"), Messages.tr("audit.col.actor"), Messages.tr("audit.col.action"),
                    Messages.tr("audit.col.entityType"), Messages.tr("audit.col.entityId"),
                    Messages.tr("audit.col.before"), Messages.tr("audit.col.after")}, 0);
    private final JComboBox<String> entityFilter =
            new JComboBox<>(new String[]{"ALL", "account", "cheque", "loan"});

    public AuditLogPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        add(buildControls(), BorderLayout.NORTH);
        JTable table = new JTable(model);
        add(new JScrollPane(table), BorderLayout.CENTER);

        loadLogs();
    }

    private JPanel buildControls() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(BorderFactory.createTitledBorder(Messages.tr("audit.title")));
        panel.add(new JLabel(Messages.tr("audit.entityType")));
        panel.add(entityFilter);
        JButton refreshBtn = new JButton(Messages.tr("common.refresh"));
        refreshBtn.addActionListener(e -> loadLogs());
        panel.add(refreshBtn);
        return panel;
    }

    private void loadLogs() {
        model.setRowCount(0);
        try {
            String filter = (String) entityFilter.getSelectedItem();
            List<AuditLog> logs = "ALL".equals(filter)
                    ? auditService.recent(200)
                    : auditService.byEntityType(filter, 200);
            for (AuditLog log : logs) {
                model.addRow(new Object[]{
                        log.getCreatedAt(),
                        log.getActorName() == null ? Messages.tr("audit.systemActor") : log.getActorName(),
                        log.getAction(),
                        log.getEntityType(),
                        log.getEntityId(),
                        log.getBeforeValue(),
                        log.getAfterValue()
                });
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, Messages.tr("common.dbErrorPrefix") + ex.getMessage(), Messages.tr("common.error"), JOptionPane.ERROR_MESSAGE);
        }
    }
}
