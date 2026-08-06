package com.branchteller.gui;

import com.branchteller.i18n.Messages;
import com.branchteller.model.Account;
import com.branchteller.model.StandingInstruction;
import com.branchteller.model.User;
import com.branchteller.service.BankingService;
import com.branchteller.service.StandingInstructionService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Recurring auto-pay transfers (standing instructions). Tellers/managers can set up a
 *  scheduled transfer and manually trigger the batch job that executes anything due. */
public class StandingInstructionsPanel extends JPanel {

    private final StandingInstructionService siService = new StandingInstructionService();
    private final BankingService bankingService = new BankingService();
    private final User currentUser;

    private final JTextField fromAccountField = new JTextField(14);
    private final JTextField toAccountField = new JTextField(14);
    private final JTextField amountField = new JTextField(8);
    private final JComboBox<String> frequencyCombo = new JComboBox<>(new String[]{"MONTHLY", "WEEKLY"});
    private final JTextField noteField = new JTextField(18);
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{Messages.tr("si.col.id"), Messages.tr("si.col.from"), Messages.tr("si.col.to"),
                    Messages.tr("si.col.amount"), Messages.tr("si.col.frequency"), Messages.tr("si.col.nextRun"),
                    Messages.tr("si.col.status"), Messages.tr("si.col.note")}, 0);
    private final JTable table = new JTable(model);

    public StandingInstructionsPanel(User currentUser) {
        this.currentUser = currentUser;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        add(buildForm(), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        UITheme.installStatusRenderer(table, 6);
        loadAll();
    }

    private JPanel buildForm() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(Messages.tr("si.formTitle")));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; panel.add(new JLabel(Messages.tr("si.fromAccount")), gbc);
        gbc.gridx = 1; panel.add(fromAccountField, gbc);
        gbc.gridx = 2; panel.add(new JLabel(Messages.tr("si.toAccount")), gbc);
        gbc.gridx = 3; panel.add(toAccountField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; panel.add(new JLabel(Messages.tr("common.amount")), gbc);
        gbc.gridx = 1; panel.add(amountField, gbc);
        gbc.gridx = 2; panel.add(new JLabel(Messages.tr("si.frequency")), gbc);
        gbc.gridx = 3; panel.add(frequencyCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 2; panel.add(new JLabel(Messages.tr("si.note")), gbc);
        gbc.gridx = 1; gbc.gridwidth = 3; panel.add(noteField, gbc);
        gbc.gridwidth = 1;

        JButton createBtn = new JButton(Messages.tr("si.createBtn"));
        createBtn.addActionListener(e -> create());
        JButton pauseBtn = new JButton(Messages.tr("si.pauseBtn"));
        pauseBtn.addActionListener(e -> withSelected(id -> silently(() -> siService.pause(id))));
        JButton resumeBtn = new JButton(Messages.tr("si.resumeBtn"));
        resumeBtn.addActionListener(e -> withSelected(id -> silently(() -> siService.resume(id))));
        JButton cancelBtn = new JButton(Messages.tr("si.cancelBtn"));
        cancelBtn.addActionListener(e -> withSelected(id -> silently(() -> siService.cancel(id))));
        JButton runDueBtn = new JButton(Messages.tr("si.runDueBtn"));
        runDueBtn.addActionListener(e -> runDue());

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonRow.add(createBtn);
        buttonRow.add(pauseBtn);
        buttonRow.add(resumeBtn);
        buttonRow.add(cancelBtn);
        buttonRow.add(runDueBtn);
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 4;
        panel.add(buttonRow, gbc);

        return panel;
    }

    private void create() {
        try {
            Optional<Account> from = bankingService.lookupAccount(fromAccountField.getText().trim());
            if (from.isEmpty()) {
                JOptionPane.showMessageDialog(this, Messages.tr("si.fromNotFoundMsg"), Messages.tr("common.invalidAccountTitle"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            BigDecimal amount = new BigDecimal(amountField.getText().trim());
            siService.create(from.get().getId(), toAccountField.getText().trim(), amount,
                    (String) frequencyCombo.getSelectedItem(), LocalDate.now(), noteField.getText().trim());
            amountField.setText("");
            noteField.setText("");
            loadAll();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, Messages.tr("common.invalidAmountMsg"), Messages.tr("common.invalidAmountTitle"), JOptionPane.WARNING_MESSAGE);
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void runDue() {
        try {
            List<String> results = siService.runDue(currentUser.getId());
            if (results.isEmpty()) {
                JOptionPane.showMessageDialog(this, Messages.tr("si.noneDueMsg"));
            } else {
                JOptionPane.showMessageDialog(this, String.join("\n", results), Messages.tr("si.batchResultsTitle"), JOptionPane.INFORMATION_MESSAGE);
            }
            loadAll();
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void withSelected(java.util.function.IntConsumer consumer) {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, Messages.tr("si.selectFirstMsg"), Messages.tr("common.noSelectionTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        int id = (int) model.getValueAt(row, 0);
        consumer.accept(id);
        loadAll();
    }

    private void silently(SqlRunnable r) {
        try {
            r.run();
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    @FunctionalInterface
    private interface SqlRunnable { void run() throws SQLException; }

    private void loadAll() {
        model.setRowCount(0);
        try {
            List<StandingInstruction> list = siService.all();
            for (StandingInstruction si : list) {
                model.addRow(new Object[]{si.getId(), si.getFromAccountNumber(), si.getToAccountNumber(),
                        si.getAmount(), si.getFrequency(), si.getNextRunDate(), si.getStatus(), si.getNote()});
            }
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void showDbError(SQLException ex) {
        JOptionPane.showMessageDialog(this, Messages.tr("common.databaseErrorPrefix") + ex.getMessage(), Messages.tr("common.error"), JOptionPane.ERROR_MESSAGE);
    }
}
