package com.branchteller.gui;

import com.branchteller.i18n.Messages;
import com.branchteller.model.CreditScoreHistory;
import com.branchteller.model.User;
import com.branchteller.service.CreditScoreService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.util.List;

/** Simplified credit scoring/underwriting: computes a 300-850 score from relationship
 *  tenure, balances held, on-time loan repayment history, KYC status, and AML flags.
 *  Intended as a supporting signal for loan approval, not an automatic decision. */
public class CreditScorePanel extends JPanel {

    private final CreditScoreService creditScoreService = new CreditScoreService();
    private final User currentUser;

    private final JTextField customerIdField = new JTextField(8);
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{Messages.tr("credit.col.id"), Messages.tr("credit.col.customer"), Messages.tr("credit.col.score"),
                    Messages.tr("credit.col.rating"), Messages.tr("credit.col.computedAt")}, 0);
    private final JTable table = new JTable(model);

    public CreditScorePanel(User currentUser) {
        this.currentUser = currentUser;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.setBorder(BorderFactory.createTitledBorder(Messages.tr("credit.formTitle")));
        controls.add(new JLabel(Messages.tr("credit.customerId")));
        controls.add(customerIdField);
        JButton computeBtn = new JButton(Messages.tr("credit.computeBtn"));
        computeBtn.addActionListener(e -> compute());
        JButton refreshBtn = new JButton(Messages.tr("common.refresh"));
        refreshBtn.addActionListener(e -> loadRecent());
        controls.add(computeBtn);
        controls.add(refreshBtn);

        add(controls, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        UITheme.installStatusRenderer(table, 3);
        loadRecent();
    }

    private void compute() {
        try {
            int customerId = Integer.parseInt(customerIdField.getText().trim());
            CreditScoreHistory h = creditScoreService.computeScore(customerId, currentUser.getId());
            JOptionPane.showMessageDialog(this, Messages.tr("credit.resultMsg", h.getCustomerName(), h.getScore(), h.getRating()));
            loadRecent();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, Messages.tr("common.enterNumericIdMsg"), Messages.tr("common.invalidInputTitle"), JOptionPane.WARNING_MESSAGE);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), Messages.tr("common.notFoundTitle"), JOptionPane.WARNING_MESSAGE);
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void loadRecent() {
        model.setRowCount(0);
        try {
            List<CreditScoreHistory> history = creditScoreService.recentAll(300);
            for (CreditScoreHistory h : history) {
                model.addRow(new Object[]{h.getId(), h.getCustomerName(), h.getScore(), h.getRating(), h.getComputedAt()});
            }
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void showDbError(SQLException ex) {
        JOptionPane.showMessageDialog(this, Messages.tr("common.databaseErrorPrefix") + ex.getMessage(), Messages.tr("common.error"), JOptionPane.ERROR_MESSAGE);
    }
}
