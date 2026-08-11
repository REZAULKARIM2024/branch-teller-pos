package com.branchteller.gui;

import com.branchteller.i18n.Messages;
import com.branchteller.model.Account;
import com.branchteller.model.Card;
import com.branchteller.model.User;
import com.branchteller.service.BankingService;
import com.branchteller.service.CardService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Debit/credit card issuance and lifecycle management: issue, block/unblock, cancel,
 *  reset PIN (simulated), adjust spending limits. */
public class CardsPanel extends JPanel {

    private final CardService cardService = new CardService();
    private final BankingService bankingService = new BankingService();
    private final User currentUser;

    private final JTextField accountField = new JTextField(14);
    private final JComboBox<String> typeCombo = new JComboBox<>(new String[]{"DEBIT", "CREDIT"});
    private final JTextField creditLimitField = new JTextField(8);
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{Messages.tr("cards.col.id"), Messages.tr("cards.col.account"), Messages.tr("cards.col.number"),
                    Messages.tr("cards.col.type"), Messages.tr("cards.col.holder"), Messages.tr("cards.col.expiry"),
                    Messages.tr("cards.col.creditLimit"), Messages.tr("cards.col.dailyLimit"), Messages.tr("cards.col.status")}, 0);
    private final JTable table = new JTable(model);

    public CardsPanel(User currentUser) {
        this.currentUser = currentUser;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        add(buildForm(), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        UITheme.installStatusRenderer(table, 8);
        loadAll();
    }

    private JPanel buildForm() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(Messages.tr("cards.formTitle")));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; panel.add(new JLabel(Messages.tr("common.account")), gbc);
        gbc.gridx = 1; panel.add(accountField, gbc);
        gbc.gridx = 2; panel.add(new JLabel(Messages.tr("cards.cardType")), gbc);
        gbc.gridx = 3; panel.add(typeCombo, gbc);
        gbc.gridx = 4; panel.add(new JLabel(Messages.tr("cards.creditLimit")), gbc);
        gbc.gridx = 5; panel.add(creditLimitField, gbc);

        JButton issueBtn = new JButton(Messages.tr("cards.issueBtn"));
        issueBtn.addActionListener(e -> issue());
        JButton blockBtn = new JButton(Messages.tr("cards.blockBtn"));
        blockBtn.addActionListener(e -> withSelected(id -> silently(() -> cardService.block(id, currentUser.getId()))));
        JButton unblockBtn = new JButton(Messages.tr("cards.unblockBtn"));
        unblockBtn.addActionListener(e -> withSelected(id -> silently(() -> cardService.unblock(id, currentUser.getId()))));
        JButton cancelBtn = new JButton(Messages.tr("cards.cancelBtn"));
        cancelBtn.addActionListener(e -> withSelected(id -> silently(() -> cardService.cancel(id, currentUser.getId()))));
        JButton resetPinBtn = new JButton(Messages.tr("cards.resetPinBtn"));
        resetPinBtn.addActionListener(e -> resetPin());
        JButton refreshBtn = new JButton(Messages.tr("common.refresh"));
        refreshBtn.addActionListener(e -> loadAll());

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonRow.add(issueBtn);
        buttonRow.add(blockBtn);
        buttonRow.add(unblockBtn);
        buttonRow.add(cancelBtn);
        buttonRow.add(resetPinBtn);
        buttonRow.add(refreshBtn);
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 6;
        panel.add(buttonRow, gbc);

        return panel;
    }

    private void issue() {
        try {
            Optional<Account> account = bankingService.lookupAccount(accountField.getText().trim());
            if (account.isEmpty()) {
                JOptionPane.showMessageDialog(this, Messages.tr("common.accountNotFoundMsg"), Messages.tr("common.invalidAccountTitle"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            BigDecimal limit = null;
            if (!creditLimitField.getText().trim().isEmpty()) {
                limit = new BigDecimal(creditLimitField.getText().trim());
            }
            Card c = cardService.issue(account.get().getId(), (String) typeCombo.getSelectedItem(),
                    account.get().getCustomerName(), limit, currentUser.getId());
            JOptionPane.showMessageDialog(this, Messages.tr("cards.issuedMsg", maskCard(c.getCardNumber()), c.getExpiryDate()));
            loadAll();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, Messages.tr("common.invalidAmountMsg"), Messages.tr("common.invalidAmountTitle"), JOptionPane.WARNING_MESSAGE);
        } catch (IllegalArgumentException ex) {
            // QA finding (fixed): issue() can now reject an invalid card type, a blank
            // cardholder name, a negative credit limit, an unknown account, or a CLOSED account
            // -- none of that was caught here before, so it would have crashed out of this
            // button handler instead of showing a clear message.
            JOptionPane.showMessageDialog(this, ex.getMessage(), Messages.tr("common.invalidInputTitle"), JOptionPane.WARNING_MESSAGE);
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void resetPin() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, Messages.tr("cards.selectCardFirstMsg"), Messages.tr("common.noSelectionTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        int cardId = (int) model.getValueAt(row, 0);
        try {
            String newPin = cardService.resetPin(cardId, currentUser.getId());
            JOptionPane.showMessageDialog(this, Messages.tr("cards.newPinMsg", newPin));
        } catch (IllegalStateException | IllegalArgumentException ex) {
            // QA finding (fixed): resetPin() now rejects an unknown or CANCELLED card instead of
            // silently issuing a PIN for it -- shown here rather than left uncaught.
            JOptionPane.showMessageDialog(this, ex.getMessage(), Messages.tr("common.cannotCompleteTitle"), JOptionPane.WARNING_MESSAGE);
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void withSelected(java.util.function.IntConsumer consumer) {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, Messages.tr("cards.selectCardFirstMsg"), Messages.tr("common.noSelectionTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        consumer.accept((int) model.getValueAt(row, 0));
        loadAll();
    }

    private void silently(SqlRunnable r) {
        try {
            r.run();
        } catch (IllegalStateException | IllegalArgumentException ex) {
            // QA finding (fixed): block()/unblock()/cancel() now enforce a state-machine guard
            // (e.g. can't unblock a card that isn't BLOCKED) instead of silently no-op'ing --
            // shown here rather than left uncaught, which would have crashed this button handler.
            JOptionPane.showMessageDialog(this, ex.getMessage(), Messages.tr("common.cannotCompleteTitle"), JOptionPane.WARNING_MESSAGE);
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    @FunctionalInterface
    private interface SqlRunnable { void run() throws SQLException; }

    private void loadAll() {
        model.setRowCount(0);
        try {
            List<Card> cards = cardService.all();
            for (Card c : cards) {
                model.addRow(new Object[]{c.getId(), c.getAccountNumber(), maskCard(c.getCardNumber()), c.getCardType(),
                        c.getCardholderName(), c.getExpiryDate(), c.getCreditLimit(), c.getDailyLimit(), c.getStatus()});
            }
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private String maskCard(String number) {
        if (number == null || number.length() < 4) return number;
        return "**** **** **** " + number.substring(number.length() - 4);
    }

    private void showDbError(SQLException ex) {
        JOptionPane.showMessageDialog(this, Messages.tr("common.databaseErrorPrefix") + ex.getMessage(), Messages.tr("common.error"), JOptionPane.ERROR_MESSAGE);
    }
}
