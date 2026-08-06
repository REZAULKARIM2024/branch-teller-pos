package com.branchteller.gui;

import com.branchteller.i18n.Messages;
import com.branchteller.model.Account;
import com.branchteller.model.Notification;
import com.branchteller.model.User;
import com.branchteller.service.BankingService;
import com.branchteller.service.NotificationService;
import com.branchteller.service.StatementService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Customer notification log (SMS/email alerts auto-fired on transactions) plus an
 *  e-statement generator that "emails" a statement to the customer on file. */
public class NotificationsPanel extends JPanel {

    private final NotificationService notificationService = new NotificationService();
    private final StatementService statementService = new StatementService();
    private final BankingService bankingService = new BankingService();

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{Messages.tr("notif.col.id"), Messages.tr("notif.col.customer"), Messages.tr("notif.col.channel"),
                    Messages.tr("notif.col.subject"), Messages.tr("notif.col.message"), Messages.tr("notif.col.status"),
                    Messages.tr("notif.col.sentAt")}, 0);
    private final JTable table = new JTable(model);

    private final JTextField accountField = new JTextField(14);

    public NotificationsPanel(User currentUser) {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        add(buildControls(), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        UITheme.installStatusRenderer(table, 5);
        loadNotifications();
    }

    private JPanel buildControls() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(BorderFactory.createTitledBorder(Messages.tr("notif.formTitle")));
        JButton refreshBtn = new JButton(Messages.tr("notif.refreshLogBtn"));
        refreshBtn.addActionListener(e -> loadNotifications());
        panel.add(refreshBtn);

        panel.add(new JLabel(Messages.tr("notif.accountForStatement")));
        panel.add(accountField);
        JButton eStatementBtn = new JButton(Messages.tr("notif.emailStatementBtn"));
        eStatementBtn.addActionListener(e -> emailStatement());
        panel.add(eStatementBtn);
        return panel;
    }

    private void emailStatement() {
        try {
            Optional<Account> account = bankingService.lookupAccount(accountField.getText().trim());
            if (account.isEmpty()) {
                JOptionPane.showMessageDialog(this, Messages.tr("common.accountNotFoundMsg"), Messages.tr("common.invalidAccountTitle"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            List<String> lines = statementService.buildStatement(account.get().getAccountNumber(),
                    LocalDate.now().minusDays(30), LocalDate.now());
            String message = String.join("\n", lines);
            notificationService.send(account.get().getCustomerId(), "EMAIL", "Your E-Statement", message);
            JOptionPane.showMessageDialog(this, Messages.tr("notif.emailedMsg", account.get().getCustomerName()));
            loadNotifications();
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void loadNotifications() {
        model.setRowCount(0);
        try {
            List<Notification> notifications = notificationService.recent(300);
            for (Notification n : notifications) {
                String shortMsg = n.getMessage().length() > 80 ? n.getMessage().substring(0, 80) + "..." : n.getMessage();
                model.addRow(new Object[]{n.getId(), n.getCustomerName(), n.getChannel(), n.getSubject(), shortMsg, n.getStatus(), n.getCreatedAt()});
            }
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void showDbError(SQLException ex) {
        JOptionPane.showMessageDialog(this, Messages.tr("common.databaseErrorPrefix") + ex.getMessage(), Messages.tr("common.error"), JOptionPane.ERROR_MESSAGE);
    }
}
