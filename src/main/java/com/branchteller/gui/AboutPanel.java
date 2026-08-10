package com.branchteller.gui;

import javax.swing.*;
import java.awt.*;

/** Static "About" screen -- bank info, app modules, and version, styled as a clean HTML page. */
public class AboutPanel extends JPanel {

    public AboutPanel() {
        setLayout(new BorderLayout());
        setBackground(UITheme.PANEL_WHITE);

        JEditorPane pane = new JEditorPane();
        pane.setName("aboutContentPane");
        pane.setContentType("text/html");
        pane.setEditable(false);
        pane.setBackground(UITheme.PANEL_WHITE);
        pane.setText(html());
        pane.setCaretPosition(0);

        JScrollPane scroll = new JScrollPane(pane);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);
    }

    /**
     * QA finding (fixed): this used to list only 11 of the application's 25 real tabs (see
     * MainFrame's addTab calls / the tab.* keys in messages.properties) -- missing Products
     * &amp; Services, Holds, Cards, Standing Instructions, Payments, Complaints, Notifications,
     * Approvals, General Ledger, Financial Reports, Compliance, Credit Scoring, Branches, and
     * Security entirely. That's more than half the application's actual functionality
     * undocumented on its own "About" page -- almost certainly because this page was written
     * early in the project (see AboutPanel's original build task) and never updated as Phases
     * 8-19 added the general ledger, maker-checker, holds, cards, standing instructions,
     * AML/compliance upgrades, multi-branch support, payments, notifications, credit scoring,
     * security hardening, and complaint management. Rewritten to list every real module,
     * grouped by who can access it exactly the way MainFrame actually gates the tabs (mirrors
     * HelpPanel's "0. Welcome &amp; Roadmap" topic for consistency). {@code AboutContentTest}
     * pins this by reading the same tab.* keys from messages.properties (the single source of
     * truth for tab names) and asserting every one of them is actually mentioned here, so this
     * specific staleness can't silently recur the next time a module is added.
     *
     * <p>Deliberately package-private and {@code static} (no instance state is used) so
     * {@code AboutContentTest} can call it directly without constructing any Swing component or
     * needing a display.</p>
     */
    static String html() {
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
                + "<p>Grouped by who can access each one, exactly matching the tabs a signed-in user "
                + "actually sees:</p>"

                + "<p><b>Every role (Teller, Manager, Admin):</b></p>"
                + "<ul>"
                + "<li><b>Teller Counter</b> -- deposits, withdrawals, transfers, receipts and statements</li>"
                + "<li><b>Cash Drawer</b> -- till counts, paid-in/paid-out, drawer reconciliation</li>"
                + "<li><b>Cheques</b> -- cheque deposit and clearing workflow</li>"
                + "<li><b>Loans</b> -- applications, approval, disbursement, EMI schedules</li>"
                + "<li><b>Correspondence</b> -- generate official bank letters and certificates</li>"
                + "<li><b>Products &amp; Services</b> -- read-only catalog of everything the bank offers</li>"
                + "<li><b>Holds</b> -- place/release holds on part of an account's balance</li>"
                + "<li><b>Cards</b> -- issue, block/unblock, cancel, and reset PINs on debit/credit cards</li>"
                + "<li><b>Standing Instructions</b> -- recurring transfers / auto-pay</li>"
                + "<li><b>Payments</b> -- outbound wire/NEFT/RTGS transfers and bill payments</li>"
                + "<li><b>Complaints</b> -- log, assign, resolve, and close customer complaints</li>"
                + "<li><b>Notifications</b> -- simulated SMS/email alert log and e-statements</li>"
                + "<li><b>Security</b> -- self-service password change, OTP status, approval limit</li>"
                + "</ul>"

                + "<p><b>Branch Manager and above:</b></p>"
                + "<ul>"
                + "<li><b>Interest Accrual</b> -- periodic interest posting to savings accounts</li>"
                + "<li><b>Accounts &amp; KYC</b> -- customer onboarding, KYC verification, account opening</li>"
                + "<li><b>AML Flags</b> -- suspicious activity monitoring and review</li>"
                + "<li><b>Reports</b> -- daily branch transaction summaries</li>"
                + "<li><b>Approvals</b> -- maker-checker review of teller requests above their limit</li>"
                + "<li><b>General Ledger</b> -- trial balance, journal, and per-account ledger</li>"
                + "<li><b>Financial Reports</b> -- balance sheet, income statement, cash flow</li>"
                + "<li><b>Compliance</b> -- sanctions/PEP screening and SAR/CTR filing</li>"
                + "<li><b>Credit Scoring</b> -- underwriting score computed from data already in the system</li>"
                + "</ul>"

                + "<p><b>Admin only:</b></p>"
                + "<ul>"
                + "<li><b>Audit Log</b> -- full trail of money-movement and account actions</li>"
                + "<li><b>Employees &amp; Payroll</b> -- staff records, time clock, payroll runs</li>"
                + "<li><b>Branches</b> -- live overview of every branch, and opening new ones</li>"
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
