package com.branchteller.gui;

import javax.swing.*;
import java.awt.*;

/** Static "About" screen -- bank info, app modules, and version, styled as a clean HTML page. */
public class AboutPanel extends JPanel {

    public AboutPanel() {
        setLayout(new BorderLayout());
        setBackground(UITheme.PANEL_WHITE);

        JEditorPane pane = new JEditorPane();
        pane.setContentType("text/html");
        pane.setEditable(false);
        pane.setBackground(UITheme.PANEL_WHITE);
        pane.setText(html());
        pane.setCaretPosition(0);

        JScrollPane scroll = new JScrollPane(pane);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);
    }

    private String html() {
        String navy = toHex(UITheme.NAVY);
        String accent = toHex(UITheme.ACCENT);
        String textDark = toHex(UITheme.TEXT_DARK);
        return "<html><body style='font-family:Segoe UI, sans-serif; padding:24px; color:" + textDark + ";'>"
                + "<h1 style='color:" + navy + "; margin-bottom:0;'>NY Financial Bank</h1>"
                + "<div style='color:" + accent + "; font-weight:bold; margin-top:2px;'>Branch Teller Platform</div>"
                + "<hr style='border:none; border-top:2px solid " + accent + "; margin:16px 0;'>"

                + "<h2 style='color:" + navy + ";'>About This Application</h2>"
                + "<p>Branch Teller is an internal desktop banking platform used by tellers, branch managers, "
                + "and administrators to service customer accounts, process transactions, manage loans and "
                + "cheques, and meet compliance and reporting obligations -- all from a single workstation "
                + "application.</p>"

                + "<h2 style='color:" + navy + ";'>Modules</h2>"
                + "<ul>"
                + "<li><b>Teller Counter</b> -- deposits, withdrawals, transfers, receipts and statements</li>"
                + "<li><b>Cash Drawer</b> -- till counts, paid-in/paid-out, drawer reconciliation</li>"
                + "<li><b>Cheques</b> -- cheque deposit and clearing workflow</li>"
                + "<li><b>Loans</b> -- applications, approval, disbursement, EMI schedules</li>"
                + "<li><b>Correspondence</b> -- generate official bank letters and certificates</li>"
                + "<li><b>Interest Accrual</b> -- periodic interest posting to savings accounts</li>"
                + "<li><b>Accounts &amp; KYC</b> -- customer onboarding, KYC verification, account opening</li>"
                + "<li><b>AML Flags</b> -- suspicious activity monitoring and review</li>"
                + "<li><b>Reports</b> -- regulatory and management reporting</li>"
                + "<li><b>Audit Log</b> -- full trail of money-movement and account actions</li>"
                + "<li><b>Employees &amp; Payroll</b> -- staff records, time clock, payroll runs</li>"
                + "</ul>"

                + "<h2 style='color:" + navy + ";'>Main Branch</h2>"
                + "<p>1 Market Plaza, New York, NY 10004<br>"
                + "Phone: (212) 555-0142<br>"
                + "support@nyfinancialbank.bank</p>"

                + "<h2 style='color:" + navy + ";'>Version</h2>"
                + "<p>Branch Teller Desktop &mdash; internal build<br>"
                + "Java Swing / MySQL desktop application</p>"

                + "</body></html>";
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }
}
