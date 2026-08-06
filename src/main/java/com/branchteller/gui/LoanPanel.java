package com.branchteller.gui;

import com.branchteller.i18n.Messages;
import com.branchteller.model.Account;
import com.branchteller.model.Loan;
import com.branchteller.model.LoanRepayment;
import com.branchteller.model.User;
import com.branchteller.service.BankingService;
import com.branchteller.service.InsufficientFundsException;
import com.branchteller.service.LoanService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class LoanPanel extends JPanel {

    private final LoanService loanService = new LoanService();
    private final BankingService bankingService = new BankingService();
    private final User currentUser;

    private final JTextField accountNumberField = new JTextField(14);
    private final JComboBox<String> loanTypeCombo = new JComboBox<>(new String[]{"PERSONAL", "AUTO", "HOME", "BUSINESS"});
    private final JTextField principalField = new JTextField(10);
    private final JTextField rateField = new JTextField(6);
    private final JTextField tenureField = new JTextField(6);

    private final DefaultTableModel loansModel = new DefaultTableModel(
            new Object[]{Messages.tr("loan.col.id"), Messages.tr("loan.col.customer"), Messages.tr("loan.col.account"),
                    Messages.tr("loan.col.type"), Messages.tr("loan.col.principal"), Messages.tr("loan.col.rate"),
                    Messages.tr("loan.col.months"), Messages.tr("loan.col.status")}, 0);
    private final JTable loansTable = new JTable(loansModel);

    private final DefaultTableModel scheduleModel = new DefaultTableModel(
            new Object[]{Messages.tr("loan.sched.col.no"), Messages.tr("loan.sched.col.due"),
                    Messages.tr("loan.sched.col.amountDue"), Messages.tr("loan.sched.col.amountPaid"),
                    Messages.tr("loan.sched.col.status")}, 0);
    private final JTable scheduleTable = new JTable(scheduleModel);

    public LoanPanel(User currentUser) {
        this.currentUser = currentUser;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        add(buildApplyForm(), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, buildLoansTable(), buildScheduleTable());
        split.setResizeWeight(0.6);
        add(split, BorderLayout.CENTER);

        UITheme.installStatusRenderer(loansTable, 7);
        UITheme.installStatusRenderer(scheduleTable, 4);
        loadLoans();
    }

    private JPanel buildApplyForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder(Messages.tr("loan.applyTitle")));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;

        int col = 0;
        gbc.gridy = 0;
        gbc.gridx = col++; form.add(new JLabel(Messages.tr("loan.account")), gbc);
        gbc.gridx = col++; form.add(accountNumberField, gbc);
        gbc.gridx = col++; form.add(new JLabel(Messages.tr("loan.type")), gbc);
        gbc.gridx = col++; form.add(loanTypeCombo, gbc);
        gbc.gridx = col++; form.add(new JLabel(Messages.tr("loan.principal")), gbc);
        gbc.gridx = col++; form.add(principalField, gbc);
        gbc.gridx = col++; form.add(new JLabel(Messages.tr("loan.rate")), gbc);
        gbc.gridx = col++; form.add(rateField, gbc);
        gbc.gridx = col++; form.add(new JLabel(Messages.tr("loan.tenure")), gbc);
        gbc.gridx = col++; form.add(tenureField, gbc);

        JButton applyBtn = new JButton(Messages.tr("loan.apply"));
        applyBtn.addActionListener(e -> applyLoan());
        gbc.gridx = col; form.add(applyBtn, gbc);

        return form;
    }

    private JPanel buildLoansTable() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createTitledBorder(Messages.tr("loan.tableTitle")));
        panel.add(new JScrollPane(loansTable), BorderLayout.CENTER);

        loansTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadSelectedSchedule();
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton approveBtn = new JButton(Messages.tr("loan.approve"));
        JButton rejectBtn = new JButton(Messages.tr("loan.reject"));
        JButton disburseBtn = new JButton(Messages.tr("loan.disburse"));
        JButton payBtn = new JButton(Messages.tr("loan.payNext"));

        approveBtn.setVisible(currentUser.isManagerOrAbove());
        rejectBtn.setVisible(currentUser.isManagerOrAbove());

        approveBtn.addActionListener(e -> withSelectedLoan(id -> {
            loanService.approve(id, currentUser.getId());
            loadLoans();
        }));
        rejectBtn.addActionListener(e -> withSelectedLoan(id -> {
            loanService.reject(id, currentUser.getId());
            loadLoans();
        }));
        disburseBtn.addActionListener(e -> withSelectedLoan(id -> {
            loanService.disburse(id, currentUser);
            loadLoans();
            loadSelectedSchedule();
        }));
        payBtn.addActionListener(e -> payInstallment());

        buttons.add(approveBtn);
        buttons.add(rejectBtn);
        buttons.add(disburseBtn);
        buttons.add(payBtn);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildScheduleTable() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(Messages.tr("loan.scheduleTitle")));
        panel.add(new JScrollPane(scheduleTable), BorderLayout.CENTER);
        return panel;
    }

    private void applyLoan() {
        String accountNumber = accountNumberField.getText().trim();
        BigDecimal principal, rate;
        int tenure;
        try {
            principal = new BigDecimal(principalField.getText().trim());
            rate = new BigDecimal(rateField.getText().trim());
            tenure = Integer.parseInt(tenureField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, Messages.tr("loan.invalidInputMsg"), Messages.tr("common.invalidInputTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            Optional<Account> account = bankingService.lookupAccount(accountNumber);
            if (account.isEmpty()) {
                JOptionPane.showMessageDialog(this, Messages.tr("loan.accountNotFoundMsg"), Messages.tr("cheque.invalidAccountTitle"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            loanService.apply(account.get().getCustomerId(), account.get().getId(),
                    (String) loanTypeCombo.getSelectedItem(), principal, rate, tenure);
            accountNumberField.setText("");
            principalField.setText("");
            rateField.setText("");
            tenureField.setText("");
            loadLoans();
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    @FunctionalInterface
    private interface LoanAction {
        void run(int loanId) throws SQLException;
    }

    private void withSelectedLoan(LoanAction action) {
        int row = loansTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, Messages.tr("loan.selectFirstMsg"), Messages.tr("common.noSelectionTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        int loanId = (int) loansModel.getValueAt(row, 0);
        try {
            action.run(loanId);
        } catch (SQLException ex) {
            showDbError(ex);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), Messages.tr("common.cannotCompleteTitle"), JOptionPane.WARNING_MESSAGE);
        }
    }

    private void payInstallment() {
        int row = loansTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, Messages.tr("loan.selectFirstMsg"), Messages.tr("common.noSelectionTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        int loanId = (int) loansModel.getValueAt(row, 0);
        try {
            loanService.payNextInstallment(loanId, currentUser.getId());
            loadSelectedSchedule();
        } catch (InsufficientFundsException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), Messages.tr("teller.insufficientFundsTitle"), JOptionPane.ERROR_MESSAGE);
        } catch (SQLException ex) {
            showDbError(ex);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), Messages.tr("loan.cannotPayTitle"), JOptionPane.WARNING_MESSAGE);
        }
    }

    private void loadLoans() {
        loansModel.setRowCount(0);
        try {
            List<Loan> loans = loanService.findAll();
            for (Loan l : loans) {
                loansModel.addRow(new Object[]{
                        l.getId(), l.getCustomerName(), l.getAccountNumber(), l.getLoanType(),
                        l.getPrincipal(), l.getInterestRate(), l.getTenureMonths(), l.getStatus()
                });
            }
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void loadSelectedSchedule() {
        scheduleModel.setRowCount(0);
        int row = loansTable.getSelectedRow();
        if (row < 0) return;
        int loanId = (int) loansModel.getValueAt(row, 0);
        try {
            List<LoanRepayment> schedule = loanService.schedule(loanId);
            for (LoanRepayment r : schedule) {
                scheduleModel.addRow(new Object[]{
                        r.getInstallmentNo(), r.getDueDate(), r.getAmountDue(), r.getAmountPaid(), r.getStatus()
                });
            }
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void showDbError(SQLException ex) {
        JOptionPane.showMessageDialog(this, Messages.tr("common.dbErrorPrefix") + ex.getMessage(), Messages.tr("common.error"), JOptionPane.ERROR_MESSAGE);
    }
}
