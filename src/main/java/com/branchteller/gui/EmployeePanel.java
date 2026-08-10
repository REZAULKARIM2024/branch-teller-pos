package com.branchteller.gui;

import com.branchteller.i18n.Messages;
import com.branchteller.model.Employee;
import com.branchteller.model.PayrollRun;
import com.branchteller.model.User;
import com.branchteller.service.PayrollService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class EmployeePanel extends JPanel {

    private final PayrollService payrollService = new PayrollService();
    private final User currentUser;

    private final JTextField nameField = new JTextField(14);
    private final JTextField positionField = new JTextField(12);
    private final JTextField rateField = new JTextField(6);

    private final DefaultTableModel employeeModel =
            new DefaultTableModel(new Object[]{Messages.tr("emp.col.id"), Messages.tr("emp.col.name"),
                    Messages.tr("emp.col.position"), Messages.tr("emp.col.rate")}, 0);
    private final JTable employeeTable = new JTable(employeeModel);

    private final DefaultTableModel payrollModel = new DefaultTableModel(
            new Object[]{Messages.tr("emp.payroll.col.periodStart"), Messages.tr("emp.payroll.col.periodEnd"),
                    Messages.tr("emp.payroll.col.hours"), Messages.tr("emp.payroll.col.gross"),
                    Messages.tr("emp.payroll.col.tax"), Messages.tr("emp.payroll.col.net")}, 0);

    public EmployeePanel(User currentUser) {
        this.currentUser = currentUser;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        add(buildHireForm(), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, buildRoster(), buildPayrollHistory());
        split.setResizeWeight(0.5);
        add(split, BorderLayout.CENTER);

        loadRoster();
    }

    private JPanel buildHireForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder(Messages.tr("emp.hireTitle")));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;

        int col = 0;
        gbc.gridy = 0;
        gbc.gridx = col++; form.add(new JLabel(Messages.tr("emp.name")), gbc);
        gbc.gridx = col++; form.add(nameField, gbc);
        gbc.gridx = col++; form.add(new JLabel(Messages.tr("emp.position")), gbc);
        gbc.gridx = col++; form.add(positionField, gbc);
        gbc.gridx = col++; form.add(new JLabel(Messages.tr("emp.hourlyRate")), gbc);
        gbc.gridx = col++; form.add(rateField, gbc);

        JButton hireBtn = new JButton(Messages.tr("emp.hire"));
        hireBtn.addActionListener(e -> hireEmployee());
        gbc.gridx = col; form.add(hireBtn, gbc);

        return form;
    }

    private JPanel buildRoster() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createTitledBorder(Messages.tr("emp.rosterTitle")));
        panel.add(new JScrollPane(employeeTable), BorderLayout.CENTER);

        employeeTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadPayrollHistory();
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton clockInBtn = new JButton(Messages.tr("emp.clockIn"));
        JButton clockOutBtn = new JButton(Messages.tr("emp.clockOut"));
        JButton runPayrollBtn = new JButton(Messages.tr("emp.runPayroll"));

        clockInBtn.addActionListener(e -> withSelectedEmployee(id -> payrollService.clockIn(id)));
        clockOutBtn.addActionListener(e -> withSelectedEmployee(id -> payrollService.clockOut(id)));
        runPayrollBtn.addActionListener(e -> runPayroll());

        buttons.add(clockInBtn);
        buttons.add(clockOutBtn);
        buttons.add(runPayrollBtn);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildPayrollHistory() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(Messages.tr("emp.payrollHistoryTitle")));
        panel.add(new JScrollPane(new JTable(payrollModel)), BorderLayout.CENTER);
        return panel;
    }

    private void hireEmployee() {
        String name = nameField.getText().trim();
        String position = positionField.getText().trim();
        BigDecimal rate;
        try {
            rate = new BigDecimal(rateField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, Messages.tr("emp.invalidRateMsg"), Messages.tr("common.invalidInputTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (name.isEmpty() || position.isEmpty()) {
            JOptionPane.showMessageDialog(this, Messages.tr("emp.missingInfoMsg"), Messages.tr("common.missingInfoTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            payrollService.hire(name, position, rate);
            nameField.setText("");
            positionField.setText("");
            rateField.setText("");
            loadRoster();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), Messages.tr("common.invalidInputTitle"), JOptionPane.WARNING_MESSAGE);
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    @FunctionalInterface
    private interface EmployeeAction {
        void run(int employeeId) throws SQLException;
    }

    private void withSelectedEmployee(EmployeeAction action) {
        int row = employeeTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, Messages.tr("emp.selectFirstMsg"), Messages.tr("common.noSelectionTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        int employeeId = (int) employeeModel.getValueAt(row, 0);
        try {
            action.run(employeeId);
        } catch (SQLException ex) {
            showDbError(ex);
        } catch (IllegalStateException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), Messages.tr("common.cannotCompleteTitle"), JOptionPane.WARNING_MESSAGE);
        }
    }

    private void runPayroll() {
        int row = employeeTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, Messages.tr("emp.selectFirstMsg"), Messages.tr("common.noSelectionTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        int employeeId = (int) employeeModel.getValueAt(row, 0);

        JTextField startField = new JTextField(LocalDate.now().minusDays(14).toString());
        JTextField endField = new JTextField(LocalDate.now().toString());
        JPanel panel = new JPanel(new GridLayout(2, 2, 6, 6));
        panel.add(new JLabel(Messages.tr("emp.periodStart")));
        panel.add(startField);
        panel.add(new JLabel(Messages.tr("emp.periodEnd")));
        panel.add(endField);

        int result = JOptionPane.showConfirmDialog(this, panel, Messages.tr("emp.runPayrollDialogTitle"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        try {
            LocalDate start = LocalDate.parse(startField.getText().trim());
            LocalDate end = LocalDate.parse(endField.getText().trim());
            PayrollRun run = payrollService.runPayroll(employeeId, start, end, currentUser.getId());
            JOptionPane.showMessageDialog(this,
                    Messages.tr("emp.payrollCompleteMsg", run.getGrossPay(), run.getTaxWithheld(), run.getNetPay()),
                    Messages.tr("emp.payrollCompleteTitle"), JOptionPane.INFORMATION_MESSAGE);
            loadPayrollHistory();
        } catch (java.time.format.DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this, Messages.tr("emp.invalidDateMsg"), Messages.tr("common.invalidDateTitle"), JOptionPane.WARNING_MESSAGE);
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void loadRoster() {
        employeeModel.setRowCount(0);
        try {
            List<Employee> employees = payrollService.activeEmployees();
            for (Employee e : employees) {
                employeeModel.addRow(new Object[]{e.getId(), e.getFullName(), e.getPosition(), e.getHourlyRate()});
            }
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void loadPayrollHistory() {
        payrollModel.setRowCount(0);
        int row = employeeTable.getSelectedRow();
        if (row < 0) return;
        int employeeId = (int) employeeModel.getValueAt(row, 0);
        try {
            List<PayrollRun> history = payrollService.payrollHistory(employeeId);
            for (PayrollRun r : history) {
                payrollModel.addRow(new Object[]{
                        r.getPeriodStart(), r.getPeriodEnd(), r.getHoursWorked(), r.getGrossPay(), r.getTaxWithheld(), r.getNetPay()
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
