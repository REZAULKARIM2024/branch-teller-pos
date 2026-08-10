package com.branchteller.gui;

import com.branchteller.i18n.Messages;
import com.branchteller.model.CashDrawerLog;
import com.branchteller.model.User;
import com.branchteller.service.CashDrawerService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;

public class DrawerPanel extends JPanel {

    private final CashDrawerService drawerService = new CashDrawerService();
    private final User teller;

    private final JComboBox<String> actionCombo =
            new JComboBox<>(new String[]{"PAID_IN", "PAID_OUT", "CASH_PULL", "NO_SALE", "TILL_COUNT"});
    private final JTextField amountField = new JTextField(10);
    private final JTextField noteField = new JTextField(24);
    private final DefaultTableModel logModel =
            new DefaultTableModel(new Object[]{Messages.tr("drawer.col.action"), Messages.tr("drawer.col.amount"),
                    Messages.tr("drawer.col.note"), Messages.tr("drawer.col.when")}, 0);

    public DrawerPanel(User teller) {
        this.teller = teller;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        add(buildForm(), BorderLayout.NORTH);
        add(buildLogTable(), BorderLayout.CENTER);

        loadRecent();
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder(Messages.tr("drawer.title")));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; form.add(new JLabel(Messages.tr("drawer.action")), gbc);
        gbc.gridx = 1; form.add(actionCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 1; form.add(new JLabel(Messages.tr("drawer.amount")), gbc);
        gbc.gridx = 1; form.add(amountField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; form.add(new JLabel(Messages.tr("drawer.note")), gbc);
        gbc.gridx = 1; form.add(noteField, gbc);

        JButton submitBtn = new JButton(Messages.tr("drawer.record"));
        submitBtn.addActionListener(e -> record());
        gbc.gridx = 1; gbc.gridy = 3; gbc.anchor = GridBagConstraints.EAST;
        form.add(submitBtn, gbc);

        return form;
    }

    private JScrollPane buildLogTable() {
        JTable table = new JTable(logModel);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createTitledBorder(Messages.tr("drawer.logTitle")));
        return scrollPane;
    }

    private void record() {
        String action = (String) actionCombo.getSelectedItem();
        BigDecimal amount;
        try {
            String text = amountField.getText().trim();
            amount = text.isEmpty() ? BigDecimal.ZERO : new BigDecimal(text);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, Messages.tr("common.invalidAmountMsg"), Messages.tr("common.invalidAmountTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            drawerService.record(teller, action, amount, noteField.getText().trim());
            amountField.setText("");
            noteField.setText("");
            loadRecent();
        } catch (IllegalArgumentException ex) {
            // QA finding (fixed): CashDrawerService.record() now validates the action/amount
            // (see its javadoc) and throws IllegalArgumentException for things like a $0.00
            // PAID_IN or a nonzero NO_SALE -- previously uncaught here, that would have
            // propagated out of this button handler as an unhandled exception instead of
            // showing the teller a clear message explaining what to fix.
            JOptionPane.showMessageDialog(this, ex.getMessage(), Messages.tr("common.invalidInputTitle"), JOptionPane.WARNING_MESSAGE);
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void loadRecent() {
        logModel.setRowCount(0);
        try {
            for (CashDrawerLog log : drawerService.recentActivity(teller, 25)) {
                logModel.addRow(new Object[]{log.getAction(), log.getAmount(), log.getNote(), log.getCreatedAt()});
            }
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void showDbError(SQLException ex) {
        JOptionPane.showMessageDialog(this, Messages.tr("common.dbErrorPrefix") + ex.getMessage(), Messages.tr("common.error"), JOptionPane.ERROR_MESSAGE);
    }
}
