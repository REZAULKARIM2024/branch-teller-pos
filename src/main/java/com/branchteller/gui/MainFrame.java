package com.branchteller.gui;

import com.branchteller.i18n.Messages;
import com.branchteller.model.User;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;

public class MainFrame extends JFrame {

    /** Session idle timeout: log out automatically after this many minutes of no mouse/key activity. */
    private static final int IDLE_TIMEOUT_MINUTES = 10;
    private javax.swing.Timer idleTimer;
    private AWTEventListener activityListener;

    public MainFrame(User user) {
        super("Branch Teller - " + user.getFullName() + " (" + user.getRole() + ")");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1150, 760);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        getContentPane().setBackground(UITheme.BG_LIGHT);

        JPanel header = UITheme.buildHeaderBanner(Messages.tr("main.bankHeader"),
                user.getFullName() + "  |  " + user.getRole() + "  |  " + Messages.tr("main.branch") + user.getBranchId());
        header.add(UITheme.buildLanguageCombo(() -> {
            dispose();
            new MainFrame(user).setVisible(true);
        }), BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(Messages.tr("tab.teller"), new TellerPanel(user));
        tabs.addTab(Messages.tr("tab.drawer"), new DrawerPanel(user));
        tabs.addTab(Messages.tr("tab.cheques"), new ChequePanel(user));
        tabs.addTab(Messages.tr("tab.loans"), new LoanPanel(user));
        tabs.addTab(Messages.tr("tab.correspondence"), new CorrespondencePanel(user));
        tabs.addTab(Messages.tr("tab.products"), new ProductsPanel());
        tabs.addTab(Messages.tr("tab.holds"), new HoldsPanel(user));
        tabs.addTab(Messages.tr("tab.cards"), new CardsPanel(user));
        tabs.addTab(Messages.tr("tab.standingInstructions"), new StandingInstructionsPanel(user));
        tabs.addTab(Messages.tr("tab.payments"), new PaymentsPanel(user));
        tabs.addTab(Messages.tr("tab.complaints"), new ComplaintsPanel(user));
        tabs.addTab(Messages.tr("tab.notifications"), new NotificationsPanel(user));

        if (user.isManagerOrAbove()) {
            tabs.addTab(Messages.tr("tab.interest"), new InterestPanel(user));
            tabs.addTab(Messages.tr("tab.accounts"), new CustomerPanel(user));
            tabs.addTab(Messages.tr("tab.aml"), new AmlPanel(user));
            tabs.addTab(Messages.tr("tab.reports"), new ReportsPanel());
            tabs.addTab(Messages.tr("tab.approvals"), new ApprovalsPanel(user));
            tabs.addTab(Messages.tr("tab.gl"), new GlPanel());
            tabs.addTab(Messages.tr("tab.financialReports"), new FinancialReportsPanel());
            tabs.addTab(Messages.tr("tab.compliance"), new CompliancePanel(user));
            tabs.addTab(Messages.tr("tab.creditScoring"), new CreditScorePanel(user));
        }
        if ("ADMIN".equals(user.getRole())) {
            tabs.addTab(Messages.tr("tab.audit"), new AuditLogPanel());
            tabs.addTab(Messages.tr("tab.employees"), new EmployeePanel(user));
            tabs.addTab(Messages.tr("tab.branches"), new BranchesPanel(user));
        }

        tabs.addTab(Messages.tr("tab.security"), new SecurityPanel(user));
        tabs.addTab(Messages.tr("tab.help"), new HelpPanel());
        tabs.addTab(Messages.tr("tab.about"), new AboutPanel());

        UITheme.styleTabs(tabs);
        add(tabs, BorderLayout.CENTER);

        UITheme.applyHoverRecursively(tabs);

        applyComponentOrientation(Messages.isRtl() ? ComponentOrientation.RIGHT_TO_LEFT : ComponentOrientation.LEFT_TO_RIGHT);

        startIdleTimeout(user);
    }

    /** Logs the user out automatically after IDLE_TIMEOUT_MINUTES of no mouse/keyboard activity --
     *  a standard bank-application control against someone else using an unattended session. */
    private void startIdleTimeout(User user) {
        idleTimer = new javax.swing.Timer(IDLE_TIMEOUT_MINUTES * 60 * 1000, e -> {
            Toolkit.getDefaultToolkit().removeAWTEventListener(activityListener);
            idleTimer.stop();
            dispose();
            JOptionPane.showMessageDialog(null, "You were signed out after " + IDLE_TIMEOUT_MINUTES +
                    " minutes of inactivity.", "Session Timed Out", JOptionPane.INFORMATION_MESSAGE);
            new LoginFrame().setVisible(true);
        });
        idleTimer.setRepeats(false);
        idleTimer.start();

        activityListener = event -> idleTimer.restart();
        Toolkit.getDefaultToolkit().addAWTEventListener(activityListener,
                AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                Toolkit.getDefaultToolkit().removeAWTEventListener(activityListener);
                idleTimer.stop();
            }
        });
    }
}
