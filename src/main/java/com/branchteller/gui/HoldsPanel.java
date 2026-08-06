package com.branchteller.gui;

import com.branchteller.i18n.Messages;
import com.branchteller.model.Account;
import com.branchteller.model.AccountHold;
import com.branchteller.model.User;
import com.branchteller.service.BankingService;
import com.branchteller.service.HoldService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Places and releases holds/liens on accounts (fraud investigation, court order,
 *  uncleared cheque). Available balance for withdrawals = balance - active holds. */
public class HoldsPanel extends JPanel {

    private final HoldService holdService = new HoldService();
    private final BankingService bankingService = new BankingService();
    private final User currentUser;

    private final JTextField accountField = new JTextField(14);
    private final JTextField amountField = new JTextField(10);
    private final JTextField reasonField = new JTextField(24);
    private final JLabel accountInfoLabel = new JLabel(Messages.tr("holds.noAccountLoaded"));
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{Messages.tr("holds.col.id"), Messages.tr("holds.col.account"), Messages.tr("holds.col.amount"),
                    Messages.tr("holds.col.reason"), Messages.tr("holds.col.status"), Messages.tr("holds.col.placedAt")}, 0);
    private final JTable table = new JTable(model);

    private Account currentAccount;

    public HoldsPanel(User currentUser) {
        this.currentUser = currentUser;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        add(buildForm(), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        UITheme.installStatusRenderer(table, 4);
        loadActive();
    }

    private JPanel buildForm() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(Messages.tr("holds.formTitle")));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; panel.add(new JLabel(Messages.tr("common.account")), gbc);
        gbc.gridx = 1; panel.add(accountField, gbc);
        JButton lookupBtn = new JButton(Messages.tr("holds.lookupBtn"));
        lookupBtn.addActionListener(e -> lookup());
        gbc.gridx = 2; panel.add(lookupBtn, gbc);
        gbc.gridx = 3; panel.add(accountInfoLabel, gbc);

        gbc.gridx = 0; gbc.gridy = 1; panel.add(new JLabel(Messages.tr("holds.holdAmount")), gbc);
        gbc.gridx = 1; panel.add(amountField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; panel.add(new JLabel(Messages.tr("holds.reason")), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2; panel.add(reasonField, gbc);
        gbc.gridwidth = 1;

        JButton placeBtn = new JButton(Messages.tr("holds.placeBtn"));
        placeBtn.addActionListener(e -> placeHold());
        gbc.gridx = 3; gbc.gridy = 2; panel.add(placeBtn, gbc);

        JButton releaseBtn = new JButton(Messages.tr("holds.releaseBtn"));
        releaseBtn.addActionListener(e -> releaseHold());
        JButton refreshBtn = new JButton(Messages.tr("common.refresh"));
        refreshBtn.addActionListener(e -> loadActive());
        gbc.gridx = 1; gbc.gridy = 3; panel.add(releaseBtn, gbc);
        gbc.gridx = 2; panel.add(refreshBtn, gbc);

        return panel;
    }

    private void lookup() {
        try {
            Optional<Account> found = bankingService.lookupAccount(accountField.getText().trim());
            if (found.isPresent()) {
                currentAccount = found.get();
                BigDecimal available = bankingService.availableBalance(currentAccount.getId(), currentAccount.getBalance());
                accountInfoLabel.setText(currentAccount.getCustomerName() + " | Balance: $" + currentAccount.getBalance() +
                        " | Available: $" + available);
            } else {
                currentAccount = null;
                accountInfoLabel.setText(Messages.tr("common.accountNotFoundMsg"));
            }
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void placeHold() {
        if (currentAccount == null) {
            JOptionPane.showMessageDialog(this, Messages.tr("holds.lookupFirstMsg"), Messages.tr("holds.noAccountTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, Messages.tr("common.invalidAmountMsg"), Messages.tr("common.invalidAmountTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        String reason = reasonField.getText().trim();
        if (reason.isEmpty()) {
            JOptionPane.showMessageDialog(this, Messages.tr("holds.enterReasonMsg"), Messages.tr("holds.missingReasonTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            holdService.placeHold(currentAccount.getId(), amount, reason, currentUser.getId());
            amountField.setText("");
            reasonField.setText("");
            lookup();
            loadActive();
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void releaseHold() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, Messages.tr("holds.selectFirstMsg"), Messages.tr("common.noSelectionTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        int holdId = (int) model.getValueAt(row, 0);
        try {
            holdService.releaseHold(holdId, currentUser.getId());
            loadActive();
            if (currentAccount != null) lookup();
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void loadActive() {
        model.setRowCount(0);
        try {
            List<AccountHold> holds = holdService.activeHolds();
            for (AccountHold h : holds) {
                model.addRow(new Object[]{h.getId(), h.getAccountNumber(), h.getAmount(), h.getReason(), h.getStatus(), h.getPlacedAt()});
            }
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void showDbError(SQLException ex) {
        JOptionPane.showMessageDialog(this, Messages.tr("common.databaseErrorPrefix") + ex.getMessage(), Messages.tr("common.error"), JOptionPane.ERROR_MESSAGE);
    }
}
