package com.branchteller.gui;

import com.branchteller.i18n.Messages;
import com.branchteller.model.Branch;
import com.branchteller.model.User;
import com.branchteller.service.BranchService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.util.List;

/** Multi-branch overview: every branch's address, routing code, staff count, account
 *  count, and total deposits. Admins can open new branches from here. */
public class BranchesPanel extends JPanel {

    private final BranchService branchService = new BranchService();
    private final User currentUser;

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{Messages.tr("branches.col.id"), Messages.tr("branches.col.name"), Messages.tr("branches.col.address"),
                    Messages.tr("branches.col.routingCode"), Messages.tr("branches.col.accounts"), Messages.tr("branches.col.staff"),
                    Messages.tr("branches.col.totalDeposits")}, 0);
    private final JTable table = new JTable(model);

    public BranchesPanel(User currentUser) {
        this.currentUser = currentUser;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.setBorder(BorderFactory.createTitledBorder(Messages.tr("branches.formTitle")));
        JButton refreshBtn = new JButton(Messages.tr("common.refresh"));
        refreshBtn.addActionListener(e -> loadAll());
        controls.add(refreshBtn);
        if ("ADMIN".equals(currentUser.getRole())) {
            JButton openBtn = new JButton(Messages.tr("branches.openNewBtn"));
            openBtn.addActionListener(e -> openBranch());
            controls.add(openBtn);
        }

        add(controls, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        loadAll();
    }

    private void openBranch() {
        JTextField nameField = new JTextField();
        JTextField addressField = new JTextField();
        JTextField routingField = new JTextField();
        JPanel panel = new JPanel(new GridLayout(3, 2, 6, 6));
        panel.add(new JLabel(Messages.tr("branches.name"))); panel.add(nameField);
        panel.add(new JLabel(Messages.tr("branches.address"))); panel.add(addressField);
        panel.add(new JLabel(Messages.tr("branches.routingCode"))); panel.add(routingField);

        int result = JOptionPane.showConfirmDialog(this, panel, Messages.tr("branches.openDialogTitle"), JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) return;
        if (nameField.getText().isBlank() || routingField.getText().isBlank()) {
            JOptionPane.showMessageDialog(this, Messages.tr("branches.missingInfoMsg"), Messages.tr("common.missingInfoTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            branchService.openBranch(nameField.getText().trim(), addressField.getText().trim(), routingField.getText().trim());
            loadAll();
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void loadAll() {
        model.setRowCount(0);
        try {
            List<Branch> branches = branchService.allWithStats();
            for (Branch b : branches) {
                model.addRow(new Object[]{b.getId(), b.getName(), b.getAddress(), b.getRoutingCode(),
                        b.getAccountCount(), b.getEmployeeCount(), b.getTotalDeposits()});
            }
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void showDbError(SQLException ex) {
        JOptionPane.showMessageDialog(this, Messages.tr("common.databaseErrorPrefix") + ex.getMessage(), Messages.tr("common.error"), JOptionPane.ERROR_MESSAGE);
    }
}
