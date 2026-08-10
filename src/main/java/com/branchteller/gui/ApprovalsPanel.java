package com.branchteller.gui;

import com.branchteller.i18n.Messages;
import com.branchteller.model.PendingApproval;
import com.branchteller.model.User;
import com.branchteller.service.ApprovalService;
import com.branchteller.service.InsufficientFundsException;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.util.List;

/** Manager/admin queue for maker-checker: approve or reject transactions a teller queued
 *  because the amount exceeded their personal approval limit. */
public class ApprovalsPanel extends JPanel {

    private final ApprovalService approvalService = new ApprovalService();
    private final User currentUser;

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{Messages.tr("approvals.col.id"), Messages.tr("approvals.col.type"), Messages.tr("approvals.col.fromAccount"),
                    Messages.tr("approvals.col.toAccount"), Messages.tr("approvals.col.amount"), Messages.tr("approvals.col.requestedBy"),
                    Messages.tr("approvals.col.note"), Messages.tr("approvals.col.requestedAt")}, 0);
    private final JTable table = new JTable(model);

    public ApprovalsPanel(User currentUser) {
        this.currentUser = currentUser;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.setBorder(BorderFactory.createTitledBorder(Messages.tr("approvals.formTitle")));
        JButton refreshBtn = new JButton(Messages.tr("common.refresh"));
        refreshBtn.addActionListener(e -> loadPending());
        JButton approveBtn = new JButton(Messages.tr("approvals.approveBtn"));
        approveBtn.addActionListener(e -> decide(true));
        JButton rejectBtn = new JButton(Messages.tr("approvals.rejectBtn"));
        rejectBtn.addActionListener(e -> decide(false));
        controls.add(refreshBtn);
        controls.add(approveBtn);
        controls.add(rejectBtn);

        add(controls, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        loadPending();
    }

    private void loadPending() {
        model.setRowCount(0);
        try {
            List<PendingApproval> list = approvalService.pending();
            for (PendingApproval a : list) {
                model.addRow(new Object[]{
                        a.getId(), a.getRequestType(), a.getAccountNumber(),
                        a.getToAccountNumber() == null ? "-" : a.getToAccountNumber(),
                        a.getAmount(), a.getRequestedByName(), a.getRequestNote(), a.getCreatedAt()
                });
            }
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void decide(boolean approve) {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, Messages.tr("approvals.selectFirstMsg"), Messages.tr("common.noSelectionTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        int approvalId = (int) model.getValueAt(row, 0);
        String note = JOptionPane.showInputDialog(this, approve ? Messages.tr("approvals.approvalNotePrompt") : Messages.tr("approvals.rejectionNotePrompt"));
        if (note == null) return;

        try {
            if (approve) {
                approvalService.approve(approvalId, currentUser, note);
                JOptionPane.showMessageDialog(this, Messages.tr("approvals.approvedMsg"));
            } else {
                approvalService.reject(approvalId, currentUser, note);
                JOptionPane.showMessageDialog(this, Messages.tr("approvals.rejectedMsg"));
            }
            loadPending();
        } catch (InsufficientFundsException ex) {
            JOptionPane.showMessageDialog(this, Messages.tr("approvals.cannotExecutePrefix") + ex.getMessage(), Messages.tr("payments.insufficientFundsTitle"), JOptionPane.ERROR_MESSAGE);
        } catch (IllegalArgumentException ex) {
            // QA finding (fixed): approve() can now also fail with IllegalArgumentException
            // (e.g. the account was CLOSED after the request was queued) -- previously
            // uncaught here, it would have propagated out of this button handler instead of
            // showing the manager a clear message. ApprovalService.approve() already reverts
            // the request back to PENDING before this exception reaches here, so no funds
            // moved and the request is still safely sitting in the queue for a decision.
            JOptionPane.showMessageDialog(this, Messages.tr("approvals.cannotExecutePrefix") + ex.getMessage(), Messages.tr("common.invalidInputTitle"), JOptionPane.WARNING_MESSAGE);
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void showDbError(SQLException ex) {
        JOptionPane.showMessageDialog(this, Messages.tr("common.databaseErrorPrefix") + ex.getMessage(), Messages.tr("common.error"), JOptionPane.ERROR_MESSAGE);
    }
}
