package com.branchteller.gui;

import com.branchteller.i18n.Messages;
import com.branchteller.model.*;
import com.branchteller.service.BankingService;
import com.branchteller.service.InsufficientFundsException;
import com.branchteller.service.PaymentsService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Payment network simulation: outbound NEFT/RTGS/Wire transfers to other banks, and
 *  bill payments to registered billers (utilities, telecom, credit card, government). */
public class PaymentsPanel extends JPanel {

    private final PaymentsService paymentsService = new PaymentsService();
    private final BankingService bankingService = new BankingService();
    private final User currentUser;

    private final JTextField wireAccountField = new JTextField(14);
    private final JComboBox<String> transferTypeCombo = new JComboBox<>(new String[]{"NEFT", "RTGS", "WIRE"});
    private final JTextField beneficiaryNameField = new JTextField(16);
    private final JTextField beneficiaryBankField = new JTextField(16);
    private final JTextField beneficiaryAccountField = new JTextField(14);
    private final JTextField routingSwiftField = new JTextField(12);
    private final JTextField wireAmountField = new JTextField(8);
    private final DefaultTableModel wireModel = new DefaultTableModel(
            new Object[]{Messages.tr("payments.col.ref"), Messages.tr("payments.col.account"), Messages.tr("payments.col.type"),
                    Messages.tr("payments.col.beneficiary"), Messages.tr("payments.col.bank"), Messages.tr("payments.col.amount"),
                    Messages.tr("payments.col.status"), Messages.tr("payments.col.when")}, 0);
    private final JTable wireTable = new JTable(wireModel);

    private final JTextField billAccountField = new JTextField(14);
    private final JComboBox<Biller> billerCombo = new JComboBox<>();
    private final JTextField billAmountField = new JTextField(8);
    private final DefaultTableModel billModel = new DefaultTableModel(
            new Object[]{Messages.tr("payments.billCol.ref"), Messages.tr("payments.col.account"), Messages.tr("payments.billCol.biller"),
                    Messages.tr("payments.col.amount"), Messages.tr("payments.col.status"), Messages.tr("payments.col.when")}, 0);
    private final JTable billTable = new JTable(billModel);

    public PaymentsPanel(User currentUser) {
        this.currentUser = currentUser;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(Messages.tr("payments.wireTab"), buildWireTab());
        tabs.addTab(Messages.tr("payments.billTab"), buildBillTab());
        add(tabs, BorderLayout.CENTER);
        UITheme.styleTabs(tabs);

        UITheme.installStatusRenderer(wireTable, 6);
        UITheme.installStatusRenderer(billTable, 4);

        loadBillers();
        loadWireHistory();
        loadBillHistory();
    }

    private JPanel buildWireTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; form.add(new JLabel(Messages.tr("payments.fromAccount")), gbc);
        gbc.gridx = 1; form.add(wireAccountField, gbc);
        gbc.gridx = 2; form.add(new JLabel(Messages.tr("payments.transferType")), gbc);
        gbc.gridx = 3; form.add(transferTypeCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 1; form.add(new JLabel(Messages.tr("payments.beneficiaryName")), gbc);
        gbc.gridx = 1; form.add(beneficiaryNameField, gbc);
        gbc.gridx = 2; form.add(new JLabel(Messages.tr("payments.beneficiaryBank")), gbc);
        gbc.gridx = 3; form.add(beneficiaryBankField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; form.add(new JLabel(Messages.tr("payments.beneficiaryAccount")), gbc);
        gbc.gridx = 1; form.add(beneficiaryAccountField, gbc);
        gbc.gridx = 2; form.add(new JLabel(Messages.tr("payments.routingSwift")), gbc);
        gbc.gridx = 3; form.add(routingSwiftField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; form.add(new JLabel(Messages.tr("common.amount")), gbc);
        gbc.gridx = 1; form.add(wireAmountField, gbc);
        JButton sendBtn = new JButton(Messages.tr("payments.sendBtn"));
        sendBtn.addActionListener(e -> sendWire());
        gbc.gridx = 3; form.add(sendBtn, gbc);

        panel.add(form, BorderLayout.NORTH);
        panel.add(new JScrollPane(wireTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildBillTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; form.add(new JLabel(Messages.tr("payments.fromAccount")), gbc);
        gbc.gridx = 1; form.add(billAccountField, gbc);
        gbc.gridx = 2; form.add(new JLabel(Messages.tr("payments.biller")), gbc);
        gbc.gridx = 3; form.add(billerCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 1; form.add(new JLabel(Messages.tr("common.amount")), gbc);
        gbc.gridx = 1; form.add(billAmountField, gbc);
        JButton payBtn = new JButton(Messages.tr("payments.payBtn"));
        payBtn.addActionListener(e -> payBill());
        gbc.gridx = 3; form.add(payBtn, gbc);

        panel.add(form, BorderLayout.NORTH);
        panel.add(new JScrollPane(billTable), BorderLayout.CENTER);
        return panel;
    }

    private void loadBillers() {
        try {
            billerCombo.removeAllItems();
            for (Biller b : paymentsService.billers()) billerCombo.addItem(b);
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void sendWire() {
        try {
            Optional<Account> account = bankingService.lookupAccount(wireAccountField.getText().trim());
            if (account.isEmpty()) {
                JOptionPane.showMessageDialog(this, Messages.tr("common.accountNotFoundMsg"), Messages.tr("common.invalidAccountTitle"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            BigDecimal amount = new BigDecimal(wireAmountField.getText().trim());
            ExternalTransfer t = paymentsService.initiateExternalTransfer(account.get().getId(),
                    (String) transferTypeCombo.getSelectedItem(), beneficiaryNameField.getText().trim(),
                    beneficiaryBankField.getText().trim(), beneficiaryAccountField.getText().trim(),
                    routingSwiftField.getText().trim(), amount, currentUser.getId());
            JOptionPane.showMessageDialog(this, Messages.tr("payments.transferCompleteMsg", t.getReferenceNo()));
            wireAmountField.setText("");
            loadWireHistory();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, Messages.tr("common.invalidAmountMsg"), Messages.tr("common.invalidAmountTitle"), JOptionPane.WARNING_MESSAGE);
        } catch (IllegalArgumentException ex) {
            // QA finding (fixed): initiateExternalTransfer() can now reject an invalid transfer
            // type, a blank beneficiary field, or a CLOSED account -- none of that was caught
            // here before, so it would have crashed out of this button handler.
            JOptionPane.showMessageDialog(this, ex.getMessage(), Messages.tr("common.invalidInputTitle"), JOptionPane.WARNING_MESSAGE);
        } catch (InsufficientFundsException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), Messages.tr("payments.insufficientFundsTitle"), JOptionPane.ERROR_MESSAGE);
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void payBill() {
        try {
            Optional<Account> account = bankingService.lookupAccount(billAccountField.getText().trim());
            if (account.isEmpty()) {
                JOptionPane.showMessageDialog(this, Messages.tr("common.accountNotFoundMsg"), Messages.tr("common.invalidAccountTitle"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            Biller biller = (Biller) billerCombo.getSelectedItem();
            if (biller == null) {
                JOptionPane.showMessageDialog(this, Messages.tr("payments.selectBillerMsg"), Messages.tr("payments.missingBillerTitle"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            BigDecimal amount = new BigDecimal(billAmountField.getText().trim());
            BillPayment p = paymentsService.payBill(account.get().getId(), biller.getId(), amount, currentUser.getId());
            JOptionPane.showMessageDialog(this, Messages.tr("payments.billPaidMsg", p.getReferenceNo()));
            billAmountField.setText("");
            loadBillHistory();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, Messages.tr("common.invalidAmountMsg"), Messages.tr("common.invalidAmountTitle"), JOptionPane.WARNING_MESSAGE);
        } catch (IllegalArgumentException ex) {
            // QA finding (fixed): payBill() can now reject an unknown biller or a CLOSED account
            // -- neither was caught here before, so it would have crashed out of this button
            // handler.
            JOptionPane.showMessageDialog(this, ex.getMessage(), Messages.tr("common.invalidInputTitle"), JOptionPane.WARNING_MESSAGE);
        } catch (InsufficientFundsException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), Messages.tr("payments.insufficientFundsTitle"), JOptionPane.ERROR_MESSAGE);
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void loadWireHistory() {
        wireModel.setRowCount(0);
        try {
            List<ExternalTransfer> transfers = paymentsService.recentExternalTransfers(200);
            for (ExternalTransfer t : transfers) {
                wireModel.addRow(new Object[]{t.getReferenceNo(), t.getAccountNumber(), t.getTransferType(),
                        t.getBeneficiaryName(), t.getBeneficiaryBank(), t.getAmount(), t.getStatus(), t.getCreatedAt()});
            }
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void loadBillHistory() {
        billModel.setRowCount(0);
        try {
            List<BillPayment> payments = paymentsService.recentBillPayments(200);
            for (BillPayment p : payments) {
                billModel.addRow(new Object[]{p.getReferenceNo(), p.getAccountNumber(), p.getBillerName(),
                        p.getAmount(), p.getStatus(), p.getCreatedAt()});
            }
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void showDbError(SQLException ex) {
        JOptionPane.showMessageDialog(this, Messages.tr("common.databaseErrorPrefix") + ex.getMessage(), Messages.tr("common.error"), JOptionPane.ERROR_MESSAGE);
    }
}
