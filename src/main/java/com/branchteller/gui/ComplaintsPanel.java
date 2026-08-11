package com.branchteller.gui;

import com.branchteller.dao.UserDAO;
import com.branchteller.i18n.Messages;
import com.branchteller.model.Complaint;
import com.branchteller.model.User;
import com.branchteller.service.ComplaintService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.util.List;

/** Customer complaint management / lightweight CRM: log a complaint, assign it to staff,
 *  track status through resolution and closure. */
public class ComplaintsPanel extends JPanel {

    private final ComplaintService complaintService = new ComplaintService();
    private final UserDAO userDAO = new UserDAO();
    private final User currentUser;

    private final JTextField customerIdField = new JTextField(8);
    private final JComboBox<String> categoryCombo = new JComboBox<>(new String[]{
            Messages.tr("complaints.category.serviceQuality"), Messages.tr("complaints.category.fees"),
            Messages.tr("complaints.category.accountAccess"), Messages.tr("complaints.category.cardIssue"),
            Messages.tr("complaints.category.loanIssue"), Messages.tr("complaints.category.fraud"),
            Messages.tr("complaints.category.other")});
    private final JComboBox<String> priorityCombo = new JComboBox<>(new String[]{"LOW", "MEDIUM", "HIGH"});
    private final JTextArea descriptionArea = new JTextArea(3, 30);

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{Messages.tr("complaints.col.id"), Messages.tr("complaints.col.customer"), Messages.tr("complaints.col.category"),
                    Messages.tr("complaints.col.priority"), Messages.tr("complaints.col.status"), Messages.tr("complaints.col.assignedTo"),
                    Messages.tr("complaints.col.createdAt"), Messages.tr("complaints.col.description")}, 0);
    private final JTable table = new JTable(model);

    public ComplaintsPanel(User currentUser) {
        this.currentUser = currentUser;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        add(buildForm(), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        UITheme.installStatusRenderer(table, 4);
        loadAll();
    }

    private JPanel buildForm() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(Messages.tr("complaints.formTitle")));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; panel.add(new JLabel(Messages.tr("complaints.customerId")), gbc);
        gbc.gridx = 1; panel.add(customerIdField, gbc);
        gbc.gridx = 2; panel.add(new JLabel(Messages.tr("complaints.category")), gbc);
        gbc.gridx = 3; panel.add(categoryCombo, gbc);
        gbc.gridx = 4; panel.add(new JLabel(Messages.tr("complaints.priority")), gbc);
        gbc.gridx = 5; panel.add(priorityCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 1; panel.add(new JLabel(Messages.tr("complaints.description")), gbc);
        gbc.gridx = 1; gbc.gridwidth = 5; panel.add(new JScrollPane(descriptionArea), gbc);
        gbc.gridwidth = 1;

        JButton logBtn = new JButton(Messages.tr("complaints.logBtn"));
        logBtn.addActionListener(e -> logComplaint());
        JButton assignBtn = new JButton(Messages.tr("complaints.assignBtn"));
        assignBtn.addActionListener(e -> assignToMe());
        JButton resolveBtn = new JButton(Messages.tr("complaints.resolveBtn"));
        resolveBtn.addActionListener(e -> resolveSelected());
        JButton closeBtn = new JButton(Messages.tr("complaints.closeBtn"));
        closeBtn.addActionListener(e -> closeSelected());
        JButton refreshBtn = new JButton(Messages.tr("common.refresh"));
        refreshBtn.addActionListener(e -> loadAll());

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonRow.add(logBtn);
        buttonRow.add(assignBtn);
        buttonRow.add(resolveBtn);
        buttonRow.add(closeBtn);
        buttonRow.add(refreshBtn);
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 6;
        panel.add(buttonRow, gbc);

        return panel;
    }

    private void logComplaint() {
        try {
            int customerId = Integer.parseInt(customerIdField.getText().trim());
            String description = descriptionArea.getText().trim();
            if (description.isEmpty()) {
                JOptionPane.showMessageDialog(this, Messages.tr("complaints.enterDescMsg"), Messages.tr("complaints.missingDescTitle"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            complaintService.log(customerId, (String) categoryCombo.getSelectedItem(), description,
                    (String) priorityCombo.getSelectedItem(), currentUser.getId());
            descriptionArea.setText("");
            loadAll();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, Messages.tr("common.enterNumericIdMsg"), Messages.tr("common.invalidInputTitle"), JOptionPane.WARNING_MESSAGE);
        } catch (IllegalArgumentException ex) {
            // QA finding (fixed): log() can now reject an unknown customer ID or a description
            // over 500 characters -- neither was caught here before, so it would have crashed
            // out of this button handler instead of showing a message.
            JOptionPane.showMessageDialog(this, ex.getMessage(), Messages.tr("common.invalidInputTitle"), JOptionPane.WARNING_MESSAGE);
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void assignToMe() {
        withSelected(id -> silently(() -> complaintService.assign(id, currentUser.getId(), currentUser.getId())));
    }

    private void resolveSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, Messages.tr("complaints.selectFirstMsg"), Messages.tr("common.noSelectionTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        String note = JOptionPane.showInputDialog(this, Messages.tr("complaints.resolutionNotePrompt"));
        if (note == null || note.isBlank()) return;
        int id = (int) model.getValueAt(row, 0);
        silently(() -> complaintService.resolve(id, note, currentUser.getId()));
        loadAll();
    }

    private void closeSelected() {
        withSelected(id -> silently(() -> complaintService.close(id, currentUser.getId())));
    }

    private void withSelected(java.util.function.IntConsumer consumer) {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, Messages.tr("complaints.selectFirstMsg"), Messages.tr("common.noSelectionTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        consumer.accept((int) model.getValueAt(row, 0));
        loadAll();
    }

    private void silently(SqlRunnable r) {
        try {
            r.run();
        } catch (IllegalStateException | IllegalArgumentException ex) {
            // QA finding (fixed): assign()/resolve()/close() can now reject a CLOSED complaint
            // (terminal state), an unknown complaint ID, an unknown assignee, or a blank/too-long
            // resolution note -- none of that was caught here before, so it would have crashed
            // out of the button handler.
            JOptionPane.showMessageDialog(this, ex.getMessage(), Messages.tr("common.invalidInputTitle"), JOptionPane.WARNING_MESSAGE);
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    @FunctionalInterface
    private interface SqlRunnable { void run() throws SQLException; }

    private void loadAll() {
        model.setRowCount(0);
        try {
            List<Complaint> complaints = complaintService.all();
            for (Complaint c : complaints) {
                model.addRow(new Object[]{c.getId(), c.getCustomerName(), c.getCategory(), c.getPriority(),
                        c.getStatus(), c.getAssignedToName() == null ? "-" : c.getAssignedToName(), c.getCreatedAt(), c.getDescription()});
            }
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void showDbError(SQLException ex) {
        JOptionPane.showMessageDialog(this, Messages.tr("common.databaseErrorPrefix") + ex.getMessage(), Messages.tr("common.error"), JOptionPane.ERROR_MESSAGE);
    }
}
