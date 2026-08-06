package com.branchteller.gui;

import com.branchteller.i18n.Messages;
import com.branchteller.model.Account;
import com.branchteller.model.Customer;
import com.branchteller.model.User;
import com.branchteller.service.CustomerService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

public class CustomerPanel extends JPanel {

    private final CustomerService customerService = new CustomerService();
    private final User currentUser;

    private final JTextField nameField = new JTextField(16);
    private final JTextField phoneField = new JTextField(14);
    private final JTextField emailField = new JTextField(16);
    private final JTextField addressField = new JTextField(20);

    private final DefaultTableModel customerModel = new DefaultTableModel(
            new Object[]{Messages.tr("cust.col.id"), Messages.tr("cust.col.name"), Messages.tr("cust.col.phone"),
                    Messages.tr("cust.col.email"), Messages.tr("cust.col.kyc")}, 0);
    private final JTable customerTable = new JTable(customerModel);

    public CustomerPanel(User currentUser) {
        this.currentUser = currentUser;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        add(buildRegisterForm(), BorderLayout.NORTH);
        add(buildCustomerTable(), BorderLayout.CENTER);

        UITheme.installStatusRenderer(customerTable, 4);
        loadCustomers();
    }

    private JPanel buildRegisterForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder(Messages.tr("cust.registerTitle")));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;

        int col = 0;
        gbc.gridy = 0;
        gbc.gridx = col++; form.add(new JLabel(Messages.tr("cust.name")), gbc);
        gbc.gridx = col++; form.add(nameField, gbc);
        gbc.gridx = col++; form.add(new JLabel(Messages.tr("cust.phone")), gbc);
        gbc.gridx = col++; form.add(phoneField, gbc);
        gbc.gridx = col++; form.add(new JLabel(Messages.tr("cust.email")), gbc);
        gbc.gridx = col++; form.add(emailField, gbc);
        gbc.gridx = col++; form.add(new JLabel(Messages.tr("cust.address")), gbc);
        gbc.gridx = col++; form.add(addressField, gbc);

        JButton registerBtn = new JButton(Messages.tr("cust.register"));
        registerBtn.addActionListener(e -> registerCustomer());
        gbc.gridx = col; form.add(registerBtn, gbc);

        return form;
    }

    private JPanel buildCustomerTable() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createTitledBorder(Messages.tr("cust.tableTitle")));
        panel.add(new JScrollPane(customerTable), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton verifyBtn = new JButton(Messages.tr("cust.verifyKyc"));
        JButton rejectBtn = new JButton(Messages.tr("cust.rejectKyc"));
        JButton openAccountBtn = new JButton(Messages.tr("cust.openAccount"));

        verifyBtn.addActionListener(e -> withSelectedCustomer(id -> {
            customerService.verifyKyc(id, currentUser.getId());
            loadCustomers();
        }));
        rejectBtn.addActionListener(e -> withSelectedCustomer(id -> {
            customerService.rejectKyc(id, currentUser.getId());
            loadCustomers();
        }));
        openAccountBtn.addActionListener(e -> openAccountForSelected());

        buttons.add(verifyBtn);
        buttons.add(rejectBtn);
        buttons.add(openAccountBtn);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private void registerCustomer() {
        String name = nameField.getText().trim();
        String phone = phoneField.getText().trim();
        if (name.isEmpty() || phone.isEmpty()) {
            JOptionPane.showMessageDialog(this, Messages.tr("cust.missingInfoMsg"), Messages.tr("common.missingInfoTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            customerService.register(name, phone, emailField.getText().trim(), addressField.getText().trim());
            nameField.setText("");
            phoneField.setText("");
            emailField.setText("");
            addressField.setText("");
            loadCustomers();
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    @FunctionalInterface
    private interface CustomerAction {
        void run(int customerId) throws SQLException;
    }

    private void withSelectedCustomer(CustomerAction action) {
        int row = customerTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, Messages.tr("cust.selectFirstMsg"), Messages.tr("common.noSelectionTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        int customerId = (int) customerModel.getValueAt(row, 0);
        try {
            action.run(customerId);
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void openAccountForSelected() {
        int row = customerTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, Messages.tr("cust.selectFirstMsg"), Messages.tr("common.noSelectionTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        int customerId = (int) customerModel.getValueAt(row, 0);

        JComboBox<String> typeCombo = new JComboBox<>(new String[]{"SAVINGS", "CURRENT", "FD", "RD"});
        JTextField rateField = new JTextField("2.50");
        JPanel panel = new JPanel(new GridLayout(2, 2, 6, 6));
        panel.add(new JLabel(Messages.tr("cust.accountType")));
        panel.add(typeCombo);
        panel.add(new JLabel(Messages.tr("cust.interestRate")));
        panel.add(rateField);

        int result = JOptionPane.showConfirmDialog(this, panel, Messages.tr("cust.openAccountDialogTitle"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        BigDecimal rate;
        try {
            rate = new BigDecimal(rateField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, Messages.tr("cust.invalidRateMsg"), Messages.tr("common.invalidInputTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            Account account = customerService.openAccount(customerId, currentUser.getBranchId(),
                    (String) typeCombo.getSelectedItem(), rate, currentUser.getId());
            JOptionPane.showMessageDialog(this, Messages.tr("cust.accountOpenedMsg", account.getAccountNumber()),
                    Messages.tr("cust.accountOpenedTitle"), JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException ex) {
            showDbError(ex);
        } catch (IllegalStateException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), Messages.tr("cust.cannotOpenTitle"), JOptionPane.WARNING_MESSAGE);
        }
    }

    private void loadCustomers() {
        customerModel.setRowCount(0);
        try {
            List<Customer> customers = customerService.findAll();
            for (Customer c : customers) {
                customerModel.addRow(new Object[]{c.getId(), c.getFullName(), c.getPhone(), c.getEmail(), c.getKycStatus()});
            }
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void showDbError(SQLException ex) {
        JOptionPane.showMessageDialog(this, Messages.tr("common.dbErrorPrefix") + ex.getMessage(), Messages.tr("common.error"), JOptionPane.ERROR_MESSAGE);
    }
}
