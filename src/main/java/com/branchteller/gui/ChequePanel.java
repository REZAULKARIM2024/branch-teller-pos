package com.branchteller.gui;

import com.branchteller.i18n.Messages;
import com.branchteller.model.Account;
import com.branchteller.model.Cheque;
import com.branchteller.model.User;
import com.branchteller.service.BankingService;
import com.branchteller.service.ChequeService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class ChequePanel extends JPanel {

    private final ChequeService chequeService = new ChequeService();
    private final BankingService bankingService = new BankingService();
    private final User teller;

    private final JTextField accountNumberField = new JTextField(14);
    private final JTextField chequeNoField = new JTextField(12);
    private final JTextField amountField = new JTextField(10);
    private final DefaultTableModel pendingModel =
            new DefaultTableModel(new Object[]{Messages.tr("cheque.col.number"), Messages.tr("cheque.col.account"),
                    Messages.tr("cheque.col.amount"), Messages.tr("cheque.col.deposited"), "Cheque ID"}, 0);
    private final JTable pendingTable = new JTable(pendingModel);

    public ChequePanel(User teller) {
        this.teller = teller;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        add(buildDepositForm(), BorderLayout.NORTH);
        add(buildPendingQueue(), BorderLayout.CENTER);

        // hide the internal cheque-id column, keep it for lookups
        pendingTable.getColumnModel().getColumn(4).setMinWidth(0);
        pendingTable.getColumnModel().getColumn(4).setMaxWidth(0);

        loadPending();
    }

    private JPanel buildDepositForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder(Messages.tr("cheque.depositTitle")));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; form.add(new JLabel(Messages.tr("cheque.account")), gbc);
        gbc.gridx = 1; form.add(accountNumberField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; form.add(new JLabel(Messages.tr("cheque.number")), gbc);
        gbc.gridx = 1; form.add(chequeNoField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; form.add(new JLabel(Messages.tr("cheque.amount")), gbc);
        gbc.gridx = 1; form.add(amountField, gbc);

        JButton depositBtn = new JButton(Messages.tr("cheque.queueDeposit"));
        depositBtn.addActionListener(e -> depositCheque());
        gbc.gridx = 1; gbc.gridy = 3; gbc.anchor = GridBagConstraints.EAST;
        form.add(depositBtn, gbc);

        return form;
    }

    private JPanel buildPendingQueue() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createTitledBorder(Messages.tr("cheque.queueTitle")));
        panel.add(new JScrollPane(pendingTable), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton clearBtn = new JButton(Messages.tr("cheque.markCleared"));
        JButton bounceBtn = new JButton(Messages.tr("cheque.markBounced"));
        clearBtn.addActionListener(e -> resolveSelected(true));
        bounceBtn.addActionListener(e -> resolveSelected(false));
        buttons.add(clearBtn);
        buttons.add(bounceBtn);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private void depositCheque() {
        String accountNumber = accountNumberField.getText().trim();
        String chequeNo = chequeNoField.getText().trim();
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, Messages.tr("common.invalidAmountMsg"), Messages.tr("common.invalidAmountTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (accountNumber.isEmpty() || chequeNo.isEmpty()) {
            JOptionPane.showMessageDialog(this, Messages.tr("cheque.missingInfoMsg"), Messages.tr("common.missingInfoTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            Optional<Account> account = bankingService.lookupAccount(accountNumber);
            if (account.isEmpty()) {
                JOptionPane.showMessageDialog(this, Messages.tr("cheque.accountNotFoundMsg"), Messages.tr("cheque.invalidAccountTitle"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            chequeService.deposit(account.get().getId(), chequeNo, amount, teller.getId(), "Counter deposit");
            accountNumberField.setText("");
            chequeNoField.setText("");
            amountField.setText("");
            loadPending();
            JOptionPane.showMessageDialog(this, Messages.tr("cheque.queuedMsg"), Messages.tr("cheque.queuedTitle"), JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void resolveSelected(boolean clear) {
        int row = pendingTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, Messages.tr("cheque.selectFirstMsg"), Messages.tr("common.noSelectionTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        int chequeId = (int) pendingModel.getValueAt(row, 4);

        try {
            if (clear) {
                chequeService.clear(chequeId, teller.getId());
            } else {
                chequeService.bounce(chequeId, teller.getId());
            }
            loadPending();
        } catch (SQLException ex) {
            showDbError(ex);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), Messages.tr("cheque.resolveErrorTitle"), JOptionPane.WARNING_MESSAGE);
        }
    }

    private void loadPending() {
        pendingModel.setRowCount(0);
        try {
            List<Cheque> pending = chequeService.pendingCheques();
            for (Cheque c : pending) {
                pendingModel.addRow(new Object[]{c.getChequeNo(), c.getAccountNumber(), c.getAmount(), c.getDepositDate(), c.getId()});
            }
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void showDbError(SQLException ex) {
        JOptionPane.showMessageDialog(this, Messages.tr("common.dbErrorPrefix") + ex.getMessage(), Messages.tr("common.error"), JOptionPane.ERROR_MESSAGE);
    }
}
