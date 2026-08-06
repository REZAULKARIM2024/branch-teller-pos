package com.branchteller.gui;

import com.branchteller.i18n.Messages;
import com.branchteller.model.Account;
import com.branchteller.model.Transaction;
import com.branchteller.model.User;
import com.branchteller.service.ApprovalService;
import com.branchteller.service.BankingService;
import com.branchteller.service.InsufficientFundsException;
import com.branchteller.service.NotificationService;
import com.branchteller.service.StatementService;
import com.branchteller.util.PrintableText;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TellerPanel extends JPanel {

    private final BankingService bankingService = new BankingService();
    private final StatementService statementService = new StatementService();
    private final ApprovalService approvalService = new ApprovalService();
    private final NotificationService notificationService = new NotificationService();
    private final User teller;

    private final JTextField accountLookupField = new JTextField(16);
    private final JLabel accountInfoLabel = new JLabel(Messages.tr("teller.noAccountLoaded"));
    private final JTextField amountField = new JTextField(10);
    private final JTextField destAccountField = new JTextField(16);
    private final JTextField noteField = new JTextField(20);
    private final JComboBox<String> txnTypeCombo = new JComboBox<>(new String[]{"DEPOSIT", "WITHDRAW", "TRANSFER"});
    private final DefaultTableModel historyModel =
            new DefaultTableModel(new Object[]{Messages.tr("teller.col.type"), Messages.tr("teller.col.amount"),
                    Messages.tr("teller.col.balanceAfter"), Messages.tr("teller.col.note")}, 0);
    private final JButton printReceiptBtn = new JButton(Messages.tr("teller.printLastReceipt"));
    private final JButton printStatementBtn = new JButton(Messages.tr("teller.printStatement"));

    private Account currentAccount;
    private List<String> lastReceiptLines;

    public TellerPanel(User teller) {
        this.teller = teller;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        add(buildLookupPanel(), BorderLayout.NORTH);
        add(buildTransactionPanel(), BorderLayout.CENTER);

        printReceiptBtn.setEnabled(false);
        printReceiptBtn.addActionListener(e -> printReceipt());
        printStatementBtn.addActionListener(e -> printStatement());
    }

    private JPanel buildLookupPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(BorderFactory.createTitledBorder(Messages.tr("teller.lookupTitle")));
        panel.add(new JLabel(Messages.tr("teller.account")));
        panel.add(accountLookupField);
        JButton lookupBtn = new JButton(Messages.tr("teller.lookupBtn"));
        lookupBtn.addActionListener(e -> lookupAccount());
        panel.add(lookupBtn);
        panel.add(accountInfoLabel);
        panel.add(printStatementBtn);
        return panel;
    }

    private JPanel buildTransactionPanel() {
        JPanel outer = new JPanel(new BorderLayout(10, 10));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder(Messages.tr("teller.newTxnTitle")));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; form.add(new JLabel(Messages.tr("teller.type")), gbc);
        gbc.gridx = 1; form.add(txnTypeCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 1; form.add(new JLabel(Messages.tr("teller.amount")), gbc);
        gbc.gridx = 1; form.add(amountField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; form.add(new JLabel(Messages.tr("teller.toAccount")), gbc);
        gbc.gridx = 1; form.add(destAccountField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; form.add(new JLabel(Messages.tr("teller.note")), gbc);
        gbc.gridx = 1; form.add(noteField, gbc);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton submitBtn = new JButton(Messages.tr("teller.submitTxn"));
        submitBtn.addActionListener(e -> submitTransaction());
        buttonRow.add(printReceiptBtn);
        buttonRow.add(submitBtn);
        gbc.gridx = 1; gbc.gridy = 4; gbc.anchor = GridBagConstraints.EAST;
        form.add(buttonRow, gbc);

        JTable historyTable = new JTable(historyModel);
        JScrollPane scrollPane = new JScrollPane(historyTable);
        scrollPane.setBorder(BorderFactory.createTitledBorder(Messages.tr("teller.historyTitle")));

        outer.add(form, BorderLayout.NORTH);
        outer.add(scrollPane, BorderLayout.CENTER);
        return outer;
    }

    private void lookupAccount() {
        String accountNumber = accountLookupField.getText().trim();
        if (accountNumber.isEmpty()) return;

        try {
            Optional<Account> found = bankingService.lookupAccount(accountNumber);
            if (found.isPresent()) {
                currentAccount = found.get();
                accountInfoLabel.setText(String.format(
                        "%s | %s | %s | Balance: $%s",
                        currentAccount.getCustomerName(),
                        currentAccount.getAccountType(),
                        currentAccount.getStatus(),
                        currentAccount.getBalance()));
            } else {
                currentAccount = null;
                accountInfoLabel.setText(Messages.tr("teller.accountNotFound"));
            }
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void submitTransaction() {
        if (currentAccount == null) {
            JOptionPane.showMessageDialog(this, Messages.tr("teller.noAccountMsg"), Messages.tr("teller.noAccountTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(amountField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, Messages.tr("common.invalidAmountMsg"), Messages.tr("common.invalidAmountTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        String type = (String) txnTypeCombo.getSelectedItem();
        String note = noteField.getText().trim();

        try {
            if (("WITHDRAW".equals(type) || "TRANSFER".equals(type)) && approvalService.requiresApproval(teller, amount)) {
                if ("WITHDRAW".equals(type)) {
                    approvalService.submitWithdrawal(currentAccount.getId(), amount, teller.getId(), note);
                } else {
                    String destAccNum = destAccountField.getText().trim();
                    Optional<Account> dest = bankingService.lookupAccount(destAccNum);
                    if (dest.isEmpty()) {
                        JOptionPane.showMessageDialog(this, Messages.tr("teller.invalidDestMsg"), Messages.tr("teller.invalidDestTitle"), JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    approvalService.submitTransfer(currentAccount.getId(), dest.get().getId(), amount, teller.getId(), note);
                }
                JOptionPane.showMessageDialog(this,
                        type + " of $" + amount + " exceeds your approval limit of $" + teller.getApprovalLimit() +
                                " and has been queued for manager approval.",
                        "Queued for Approval", JOptionPane.INFORMATION_MESSAGE);
                amountField.setText("");
                noteField.setText("");
                return;
            }

            if ("DEPOSIT".equals(type)) {
                Transaction txn = bankingService.deposit(currentAccount.getId(), amount, teller.getId(), note);
                appendHistory(txn);
                buildReceipt("DEPOSIT", amount, txn.getBalanceAfter(), note, null);
                notificationService.notifyTransaction(currentAccount.getCustomerId(), "DEPOSIT", amount, txn.getBalanceAfter());
                refreshBalance();
            } else if ("WITHDRAW".equals(type)) {
                Transaction txn = bankingService.withdraw(currentAccount.getId(), amount, teller.getId(), note);
                appendHistory(txn);
                buildReceipt("WITHDRAW", amount, txn.getBalanceAfter(), note, null);
                notificationService.notifyTransaction(currentAccount.getCustomerId(), "WITHDRAW", amount, txn.getBalanceAfter());
                refreshBalance();
            } else { // TRANSFER
                String destAccNum = destAccountField.getText().trim();
                Optional<Account> dest = bankingService.lookupAccount(destAccNum);
                if (dest.isEmpty()) {
                    JOptionPane.showMessageDialog(this, Messages.tr("teller.invalidDestMsg"), Messages.tr("teller.invalidDestTitle"), JOptionPane.WARNING_MESSAGE);
                    return;
                }
                bankingService.transfer(currentAccount.getId(), dest.get().getId(), amount, teller.getId(), note);
                historyModel.addRow(new Object[]{"TRANSFER_OUT", amount, "-", "to " + destAccNum + (note.isEmpty() ? "" : " - " + note)});
                buildReceipt("TRANSFER", amount, null, note, destAccNum);
                notificationService.notifyTransaction(currentAccount.getCustomerId(), "TRANSFER_OUT", amount, null);
                refreshBalance();
            }
            amountField.setText("");
            noteField.setText("");
            printReceiptBtn.setEnabled(true);
        } catch (InsufficientFundsException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), Messages.tr("teller.insufficientFundsTitle"), JOptionPane.ERROR_MESSAGE);
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void appendHistory(Transaction txn) {
        historyModel.addRow(new Object[]{txn.getTxnType(), txn.getAmount(), txn.getBalanceAfter(), txn.getNote()});
    }

    private void refreshBalance() throws SQLException {
        bankingService.lookupAccount(currentAccount.getAccountNumber()).ifPresent(a -> {
            currentAccount = a;
            accountInfoLabel.setText(String.format(
                    "%s | %s | %s | Balance: $%s",
                    a.getCustomerName(), a.getAccountType(), a.getStatus(), a.getBalance()));
        });
    }

    private void buildReceipt(String type, BigDecimal amount, BigDecimal balanceAfter, String note, String destAccount) {
        List<String> lines = new ArrayList<>();
        lines.add(Messages.tr("app.bankName") + " - Transaction Receipt");
        lines.add("-".repeat(40));
        lines.add("Account: " + currentAccount.getAccountNumber());
        lines.add("Customer: " + currentAccount.getCustomerName());
        lines.add("Type: " + type);
        lines.add("Amount: $" + amount);
        if (destAccount != null) lines.add("To account: " + destAccount);
        if (balanceAfter != null) lines.add("Balance after: $" + balanceAfter);
        if (!note.isEmpty()) lines.add("Note: " + note);
        lines.add("Teller: " + teller.getFullName());
        lines.add("Time: " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(java.time.LocalDateTime.now()));
        lines.add("-".repeat(40));
        lines.add("Thank you for banking with us.");
        lastReceiptLines = lines;
    }

    private void printReceipt() {
        if (lastReceiptLines == null) {
            JOptionPane.showMessageDialog(this, Messages.tr("teller.nothingToPrintMsg"), Messages.tr("teller.nothingToPrintTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        PrintableText.printLines(lastReceiptLines);
    }

    private void printStatement() {
        if (currentAccount == null) {
            JOptionPane.showMessageDialog(this, Messages.tr("teller.noAccountMsg"), Messages.tr("teller.noAccountTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        JTextField fromField = new JTextField(LocalDate.now().minusMonths(1).toString());
        JTextField toField = new JTextField(LocalDate.now().toString());
        JPanel panel = new JPanel(new GridLayout(2, 2, 6, 6));
        panel.add(new JLabel(Messages.tr("teller.from")));
        panel.add(fromField);
        panel.add(new JLabel(Messages.tr("teller.to")));
        panel.add(toField);

        int result = JOptionPane.showConfirmDialog(this, panel, Messages.tr("teller.statementPeriodTitle"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        try {
            LocalDate from = LocalDate.parse(fromField.getText().trim());
            LocalDate to = LocalDate.parse(toField.getText().trim());
            List<String> lines = statementService.buildStatement(currentAccount.getAccountNumber(), from, to);
            PrintableText.printLines(lines);
        } catch (java.time.format.DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this, Messages.tr("common.invalidDateMsg"), Messages.tr("common.invalidDateTitle"), JOptionPane.WARNING_MESSAGE);
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void showDbError(SQLException ex) {
        JOptionPane.showMessageDialog(this,
                Messages.tr("common.dbErrorPrefix") + ex.getMessage(),
                Messages.tr("common.error"), JOptionPane.ERROR_MESSAGE);
    }
}
