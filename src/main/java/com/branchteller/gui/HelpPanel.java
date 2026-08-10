package com.branchteller.gui;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** Interactive help browser: a topic list on the left, HTML guide content on the right.
 *  Ordered as a beginning-to-end manual, roughly following the tab order in MainFrame,
 *  so a new user can read top-to-bottom and learn the whole application. */
public class HelpPanel extends JPanel {

    private final Map<String, String> topics = new LinkedHashMap<>();
    private final JEditorPane contentPane = new JEditorPane();

    public HelpPanel() {
        setLayout(new BorderLayout());
        buildTopics();

        JList<String> topicList = new JList<>(topics.keySet().toArray(new String[0]));
        topicList.setBackground(UITheme.BG_LIGHT);
        topicList.setSelectionBackground(UITheme.ACCENT);
        topicList.setSelectionForeground(Color.WHITE);
        topicList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        topicList.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        topicList.setFixedCellHeight(28);
        topicList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && topicList.getSelectedValue() != null) {
                showTopic(topicList.getSelectedValue());
            }
        });

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBackground(UITheme.BG_LIGHT);
        leftPanel.setBorder(BorderFactory.createTitledBorder("Topics (read top to bottom for a full walkthrough)"));
        leftPanel.add(new JScrollPane(topicList), BorderLayout.CENTER);
        leftPanel.setPreferredSize(new Dimension(260, 0));

        contentPane.setContentType("text/html");
        contentPane.setEditable(false);
        contentPane.setBackground(UITheme.PANEL_WHITE);

        JScrollPane rightScroll = new JScrollPane(contentPane);
        rightScroll.setBorder(BorderFactory.createEmptyBorder());

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightScroll);
        split.setDividerLocation(260);
        split.setBorder(BorderFactory.createEmptyBorder());
        add(split, BorderLayout.CENTER);

        topicList.setSelectedIndex(0);
    }

    private void showTopic(String topic) {
        contentPane.setText(wrap(topics.get(topic)));
        contentPane.setCaretPosition(0);
    }

    private String wrap(String bodyHtml) {
        String navy = toHex(UITheme.NAVY);
        String accent = toHex(UITheme.ACCENT);
        return "<html><head><style>" +
                "h2{color:" + navy + "; border-bottom:2px solid " + accent + "; padding-bottom:4px;} " +
                "b{color:" + accent + ";} " +
                ".badge{background:" + navy + "; color:white; padding:2px 8px; border-radius:8px; font-size:11px;} " +
                "li{margin-bottom:6px;}" +
                "</style></head>"
                + "<body style='font-family:\"Segoe UI\",\"Nirmala UI\",sans-serif; padding:20px; color:#1E272E;'>"
                + bodyHtml
                + "</body></html>";
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private void buildTopics() {

        // ------------------------------------------------------------------
        // 0. Overview / roadmap
        // ------------------------------------------------------------------
        topics.put("0. Welcome & Roadmap",
                "<h2>Welcome to NY Financial Bank - Branch Teller</h2>"
                + "<p>This guide walks through the whole application, beginning to end, in the same order "
                + "the topics appear on the left. If you're brand new, start with <b>1. Logging In</b> and "
                + "work your way down.</p>"
                + "<p><b>Who sees what:</b></p>"
                + "<ul>"
                + "<li><span class='badge'>TELLER</span> &mdash; day-to-day counter work: deposits/withdrawals, "
                + "drawer, cheques, loans, cards, holds, standing instructions, payments, complaints, notifications.</li>"
                + "<li><span class='badge'>MANAGER</span> &mdash; everything a Teller sees, plus interest, KYC/account "
                + "opening, AML review, reports, maker-checker approvals, the general ledger, compliance/SAR-CTR "
                + "filing, and credit scoring.</li>"
                + "<li><span class='badge'>ADMIN</span> &mdash; everything a Manager sees, plus the audit log, "
                + "employees &amp; payroll, and branch administration.</li>"
                + "</ul>"
                + "<p>Every user, regardless of role, can change their own password under "
                + "<b>Security</b> near the end of this list.</p>");

        // ------------------------------------------------------------------
        // 1. Login / roles / 2FA
        // ------------------------------------------------------------------
        topics.put("1. Logging In",
                "<h2>Logging In</h2>"
                + "<ol>"
                + "<li>Enter the <b>Username</b> and <b>Password</b> given to you by your branch manager.</li>"
                + "<li>Click <b>Log In</b> (or just press Enter in the password field).</li>"
                + "<li>If your account requires two-factor authentication (most do by default), a dialog shows "
                + "a 6-digit one-time passcode. In this demo environment the code is shown to you directly "
                + "(standing in for a real SMS/email delivery) &mdash; click <b>OK</b>, then type that same "
                + "code into the next prompt. You get 3 attempts before the login is cancelled.</li>"
                + "<li>After 5 failed password attempts in a row, the account locks and an administrator must "
                + "reset it.</li>"
                + "</ol>"
                + "<p><b>Language:</b> use the dropdown in the top-right corner of the login window to switch "
                + "between English, বাংলা, Español, Français, and العربية at any time &mdash; the whole app "
                + "re-renders in the chosen language.</p>"
                + "<p><b>Automatic sign-out:</b> for security, you're signed out automatically after 10 minutes "
                + "of no mouse or keyboard activity. Simply log back in to resume.</p>");

        // ------------------------------------------------------------------
        // 2. Teller Counter
        // ------------------------------------------------------------------
        topics.put("2. Teller Counter",
                "<h2>Teller Counter</h2>"
                + "<p>This is the main counter screen for everyday customer transactions.</p>"
                + "<ol>"
                + "<li>Type the customer's <b>Account #</b> (e.g. <i>NYC-SAV-000001</i>) and click <b>Look Up</b>. "
                + "The customer's name, account type, status, and balance appear.</li>"
                + "<li>Choose a transaction <b>Type</b>: DEPOSIT, WITHDRAW, or TRANSFER.</li>"
                + "<li>Enter the <b>Amount</b>. For a TRANSFER, also enter the destination account in "
                + "<b>To account # (transfer only)</b>.</li>"
                + "<li>Optionally add a <b>Note</b>, then click <b>Submit Transaction</b>.</li>"
                + "</ol>"
                + "<p><b>Large transactions:</b> if a WITHDRAW or TRANSFER amount is larger than your personal "
                + "approval limit, it isn't executed immediately &mdash; it's sent to the <b>Approvals</b> queue "
                + "for a manager to review (see the Maker-Checker topic below). You'll see a message confirming "
                + "it was queued.</p>"
                + "<p>After a successful transaction, use <b>Print Last Receipt</b> for a receipt, or "
                + "<b>Print Statement...</b> to print a statement for any date range.</p>"
                + "<p>Every deposit and withdrawal you post automatically sends the customer a simulated SMS "
                + "and email alert (see <b>Notifications</b>).</p>");

        // ------------------------------------------------------------------
        // 3. Cash Drawer
        // ------------------------------------------------------------------
        topics.put("3. Cash Drawer",
                "<h2>Cash Drawer</h2>"
                + "<p>Track the physical cash in your till separately from customer account balances.</p>"
                + "<ol>"
                + "<li>Pick an action: <b>TILL_COUNT</b> (start/end of shift count), <b>PAID_IN</b>, "
                + "<b>PAID_OUT</b>, <b>CASH_PULL</b> (excess cash removed to the vault), or <b>NO_SALE</b> "
                + "(drawer opened with no transaction).</li>"
                + "<li>Enter the amount and an optional note, then submit.</li>"
                + "</ol>"
                + "<p>The log below shows your drawer history for reconciliation at end of shift.</p>");

        // ------------------------------------------------------------------
        // 4. Cheques
        // ------------------------------------------------------------------
        topics.put("4. Cheques",
                "<h2>Cheques</h2>"
                + "<ol>"
                + "<li>Deposit a cheque against an account: enter the account, cheque number, amount, and "
                + "deposit date.</li>"
                + "<li>The cheque sits in <b>PENDING</b> status until you (or a manager) mark it "
                + "<b>CLEARED</b> once funds are confirmed, or <b>BOUNCED</b> if it's returned unpaid.</li>"
                + "</ol>");

        // ------------------------------------------------------------------
        // 5. Loans
        // ------------------------------------------------------------------
        topics.put("5. Loans",
                "<h2>Loans</h2>"
                + "<ol>"
                + "<li><b>Apply:</b> pick the customer and their account, choose a loan type, and enter "
                + "principal, interest rate, and tenure (months).</li>"
                + "<li><b>Approve / Reject</b> (Manager/Admin only): review APPLIED loans and decide.</li>"
                + "<li><b>Disburse:</b> once APPROVED, disbursing credits the principal to the customer's "
                + "account and automatically builds the full EMI repayment schedule.</li>"
                + "<li>Select a loan in the list to see its installment schedule below, then use "
                + "<b>Pay Next Installment</b> to collect each payment as it's due.</li>"
                + "</ol>"
                + "<p><b>Tip:</b> check a customer's <b>Credit Scoring</b> result before approving a large "
                + "loan &mdash; it's a supporting signal, not an automatic decision.</p>");

        // ------------------------------------------------------------------
        // 6. Correspondence
        // ------------------------------------------------------------------
        topics.put("6. Correspondence",
                "<h2>Correspondence</h2>"
                + "<p>Generate official bank letters directly from live data &mdash; account opening "
                + "confirmations, balance certificates, No-Objection Certificates, loan sanction letters, "
                + "reference letters, closure letters, and interest certificates.</p>"
                + "<ol>"
                + "<li>Pick a letter type and the relevant account/customer/loan.</li>"
                + "<li>Preview the generated letter.</li>"
                + "<li><b>Print...</b> or <b>Save to File...</b> as needed.</li>"
                + "</ol>");

        // ------------------------------------------------------------------
        // 7. Products & Services
        // ------------------------------------------------------------------
        topics.put("7. Products & Services",
                "<h2>Products &amp; Services</h2>"
                + "<p>A read-only reference catalog of everything NY Financial Bank offers, organized into "
                + "<b>Personal</b>, <b>Business</b>, and <b>Commercial</b> categories. Click a category on the "
                + "left to browse its products and short descriptions &mdash; handy when a customer asks "
                + "\"what else do you offer?\"</p>");

        // ------------------------------------------------------------------
        // 8. Holds
        // ------------------------------------------------------------------
        topics.put("8. Account Holds / Liens",
                "<h2>Account Holds / Liens</h2>"
                + "<p>Place a temporary hold on part of an account's balance &mdash; for a fraud investigation, "
                + "a court order, or an uncleared cheque &mdash; without freezing the whole account.</p>"
                + "<ol>"
                + "<li>Look up the account. Its <b>available balance</b> (balance minus any active holds) is "
                + "shown &mdash; this is the maximum a customer can actually withdraw or transfer.</li>"
                + "<li>Enter a <b>Hold Amount</b> and a <b>Reason</b>, then click <b>Place Hold</b>.</li>"
                + "<li>To release a hold once it's resolved, select it in the table and click "
                + "<b>Release Selected Hold</b>.</li>"
                + "</ol>"
                + "<p>Holds are enforced automatically &mdash; the Teller Counter, Payments, and other modules "
                + "will refuse a withdrawal that would dip into held funds.</p>");

        // ------------------------------------------------------------------
        // 9. Cards
        // ------------------------------------------------------------------
        topics.put("9. Card Management",
                "<h2>Card Management</h2>"
                + "<ol>"
                + "<li>Enter the <b>Account #</b>, choose <b>DEBIT</b> or <b>CREDIT</b>, and (for CREDIT) a "
                + "credit limit, then click <b>Issue Card</b>. A masked card number and expiry date are shown.</li>"
                + "<li>Select an existing card to <b>Block</b> it (lost/stolen), <b>Unblock</b> it, "
                + "<b>Cancel Card</b> permanently, or <b>Reset PIN</b> (a new simulated PIN is generated and "
                + "shown once &mdash; share it with the cardholder securely).</li>"
                + "</ol>"
                + "<p>Card numbers are always masked on screen (<i>**** **** **** 1234</i>) for security.</p>");

        // ------------------------------------------------------------------
        // 10. Standing Instructions
        // ------------------------------------------------------------------
        topics.put("10. Standing Instructions",
                "<h2>Standing Instructions / Auto-Pay</h2>"
                + "<p>Set up a recurring transfer that runs automatically instead of the customer coming in "
                + "every month.</p>"
                + "<ol>"
                + "<li>Enter the <b>From Account #</b>, <b>To Account #</b>, <b>Amount</b>, and how often it "
                + "should run (<b>MONTHLY</b> or <b>WEEKLY</b>), then click <b>Create Instruction</b>.</li>"
                + "<li>Use <b>Pause</b>/<b>Resume</b>/<b>Cancel</b> on a selected instruction to manage it.</li>"
                + "<li>Click <b>Run Due Instructions Now</b> to execute every ACTIVE instruction whose next "
                + "run date has arrived &mdash; each one moves the funds via a normal transfer and then "
                + "advances its schedule. Results (success/failure per instruction) are shown afterward.</li>"
                + "</ol>"
                + "<p>In a production deployment this batch job would run automatically overnight; here it's "
                + "triggered manually with the button so you can see it work.</p>");

        // ------------------------------------------------------------------
        // 11. Payments
        // ------------------------------------------------------------------
        topics.put("11. Payments (Wire / Bill Pay)",
                "<h2>Payments</h2>"
                + "<p>Two sub-tabs handle money leaving the bank to somewhere external.</p>"
                + "<p><b>Wire / NEFT / RTGS:</b></p>"
                + "<ol>"
                + "<li>Enter the sending <b>Account #</b> and pick a <b>Transfer Type</b> (NEFT, RTGS, or WIRE).</li>"
                + "<li>Fill in the beneficiary's name, bank, account number, and routing/SWIFT code.</li>"
                + "<li>Enter the <b>Amount</b> and click <b>Send Transfer</b>. It settles instantly in this "
                + "simulation and a reference number is shown.</li>"
                + "</ol>"
                + "<p><b>Bill Payment:</b></p>"
                + "<ol>"
                + "<li>Enter the <b>Account #</b>, choose a registered <b>Biller</b> (utilities, telecom, "
                + "credit card, government, etc.), enter the amount, and click <b>Pay Bill</b>.</li>"
                + "</ol>"
                + "<p>Both screens keep a running history table of everything sent.</p>");

        // ------------------------------------------------------------------
        // 12. Complaints
        // ------------------------------------------------------------------
        topics.put("12. Complaints",
                "<h2>Customer Complaints</h2>"
                + "<ol>"
                + "<li>Enter the <b>Customer ID</b>, choose a <b>Category</b> and <b>Priority</b>, describe "
                + "the issue, and click <b>Log Complaint</b>. It starts in <b>OPEN</b> status.</li>"
                + "<li>Click <b>Assign to Me</b> to take ownership &mdash; status moves to <b>IN_PROGRESS</b>.</li>"
                + "<li>Once fixed, select it and click <b>Resolve Selected</b>, then enter a resolution note.</li>"
                + "<li><b>Close Selected</b> archives a resolved complaint.</li>"
                + "</ol>");

        // ------------------------------------------------------------------
        // 13. Notifications
        // ------------------------------------------------------------------
        topics.put("13. Notifications & E-Statements",
                "<h2>Notifications &amp; E-Statements</h2>"
                + "<p>A log of every simulated SMS/email alert the bank has sent &mdash; transaction alerts "
                + "fire automatically whenever a teller posts a deposit or withdrawal.</p>"
                + "<ol>"
                + "<li>Click <b>Refresh Log</b> to see the latest notifications.</li>"
                + "<li>To send a full e-statement by email, enter an <b>Account #</b> and click "
                + "<b>Email E-Statement (last 30 days)</b>. It appears in the log as an EMAIL entry.</li>"
                + "</ol>"
                + "<p>No real SMS/email gateway is connected &mdash; this is a logged simulation so you can "
                + "see exactly what would have been sent.</p>");

        // ------------------------------------------------------------------
        // 14. Interest Accrual (manager+)
        // ------------------------------------------------------------------
        topics.put("14. Interest Accrual (Manager+)",
                "<h2>Interest Accrual</h2>"
                + "<p><b>Who can access:</b> Branch Managers and above only (not visible to Tellers).</p>"
                + "<p>Enter a period (YYYY-MM, defaults to the current month) and click <b>Run Accrual</b> to "
                + "post monthly interest to every active savings account:</p>"
                + "<ul>"
                + "<li>Each account is checked first &mdash; if this period was already accrued for that "
                + "account, it is skipped so re-running a period never double-credits interest.</li>"
                + "<li>Interest is simple interest: balance &times; annual rate% &divide; 12, rounded to the "
                + "nearest cent.</li>"
                + "<li>When the amount is greater than zero, the account balance is credited, a deposit "
                + "transaction and audit log entry are recorded, and the amount is posted to the General "
                + "Ledger (debiting Interest Expense, crediting Customer Deposits Control) so the books stay "
                + "balanced.</li>"
                + "<li>When the amount is zero (a 0% rate or a $0 balance), the period is still marked as "
                + "accrued for that account, but no balance change, transaction, or ledger entry is written.</li>"
                + "<li>Every account is posted in its own transaction, so a problem with one account never "
                + "blocks or rolls back the others.</li>"
                + "</ul>"
                + "<p>Click <b>Show History</b> to list every account already accrued for the entered period, "
                + "ordered by account number, without re-running the job.</p>");

        // ------------------------------------------------------------------
        // 15. Accounts & KYC (manager+)
        // ------------------------------------------------------------------
        topics.put("15. Accounts & KYC (Manager+)",
                "<h2>Accounts &amp; KYC</h2>"
                + "<p><b>Who can access:</b> Branch Managers and above only (not visible to Tellers).</p>"
                + "<p><b>Register a customer:</b> enter <b>Name</b> and <b>Phone</b> (required), plus optional "
                + "<b>Email</b> and <b>Address</b>, then click <b>Register</b>. The new customer starts with "
                + "KYC status <b>PENDING</b> and appears in the table below with their ID, name, phone, "
                + "email, and a colored KYC status badge. Phone numbers must be unique across all customers.</p>"
                + "<p><b>Review KYC:</b> select a customer row, then click:</p>"
                + "<ul>"
                + "<li><b>Verify KYC</b> &mdash; marks the customer <b>VERIFIED</b>, unlocking account "
                + "opening for them.</li>"
                + "<li><b>Reject KYC</b> &mdash; marks the customer <b>REJECTED</b>. A rejected customer "
                + "cannot have a new account opened; any accounts already opened for them beforehand are "
                + "not affected or closed.</li>"
                + "</ul>"
                + "<p><b>Open Account:</b> select a <b>VERIFIED</b> customer and click <b>Open Account...</b>. "
                + "Choose an account <b>Type</b> (SAVINGS, CURRENT, FD, or RD) and an <b>Interest Rate</b>, "
                + "then confirm. A new account is created with a system-generated account number and a "
                + "zero opening balance; the account number is shown in a confirmation dialog. A customer "
                + "can have multiple accounts. Attempting this on a customer who is not VERIFIED (still "
                + "PENDING, or REJECTED) is blocked with an explanatory message instead of opening the "
                + "account.</p>"
                + "<p>Every registration, KYC decision, and account opening is written to the <b>Audit "
                + "Log</b> with the customer's actual before/after status at that moment, so the full "
                + "onboarding history for a customer can always be traced back accurately.</p>");

        // ------------------------------------------------------------------
        // 16. AML Flags (manager+)
        // ------------------------------------------------------------------
        topics.put("16. AML Flags (Manager+)",
                "<h2>AML Flags</h2>"
                + "<p><b>Who can access:</b> Branch Managers and above only (not visible to Tellers).</p>"
                + "<p>Any single cash-touching transaction &mdash; a deposit, a withdrawal, or the outgoing "
                + "leg of a transfer &mdash; at or above the $10,000 reporting threshold is flagged "
                + "automatically the instant it posts, in the same database transaction as the money "
                + "movement itself. This is a simplified stand-in for real Currency Transaction Report "
                + "(CTR) logic. The incoming side of an internal transfer is not separately flagged.</p>"
                + "<ul>"
                + "<li><b>Unreviewed only</b> (checked by default) shows just the flags still waiting on "
                + "you; uncheck it and click <b>Refresh</b> to see the full history, reviewed and "
                + "unreviewed, most recent first.</li>"
                + "<li>Each row shows the account, a reason describing which transaction type and amount "
                + "triggered it, the amount, when it was flagged, and whether it's been reviewed.</li>"
                + "<li>Select a flag and click <b>Mark Reviewed</b> once you've investigated it. This "
                + "records who reviewed it and when, removes it from the unreviewed queue, and is itself "
                + "written to the <b>Audit Log</b> so every review decision stays traceable.</li>"
                + "</ul>"
                + "<p>For anything serious enough to formally report, go to <b>Compliance</b> and file a "
                + "SAR (Suspicious Activity Report) or CTR (Currency Transaction Report) against the same "
                + "flag &mdash; filing a report there also marks the flag reviewed automatically.</p>");

        // ------------------------------------------------------------------
        // 17. Reports (manager+)
        // ------------------------------------------------------------------
        topics.put("17. Reports (Manager+)",
                "<h2>Reports</h2>"
                + "<p><b>Who can access:</b> Branch Managers and above only (not visible to Tellers).</p>"
                + "<p>A daily branch summary computed live from the same data every other module writes to "
                + "&mdash; nothing here is separately entered, so it always matches the books.</p>"
                + "<ul>"
                + "<li>Enter a <b>Date</b> (YYYY-MM-DD, defaults to today) and click <b>Preview</b> to see "
                + "that day's transactions grouped by type (DEPOSIT, WITHDRAW, TRANSFER_IN, TRANSFER_OUT, "
                + "etc.), each with a count and a total amount, covering the full calendar day from "
                + "midnight to midnight.</li>"
                + "<li>Click <b>Export CSV</b> to save the same summary to a file, plus a TOTAL line "
                + "(combined count and amount across every type) and a count of AML flags raised that same "
                + "day &mdash; useful for handing a day's activity to management or a regulator without "
                + "them needing access to the live system.</li>"
                + "</ul>"
                + "<p>This is a simplified daily branch report, not a compliance-grade regulatory filing "
                + "&mdash; for formal SAR/CTR reporting on a specific suspicious transaction, use the "
                + "<b>Compliance</b> tab instead.</p>");

        // ------------------------------------------------------------------
        // 18. Approvals / Maker-Checker (manager+)
        // ------------------------------------------------------------------
        topics.put("18. Approvals (Maker-Checker, Manager+)",
                "<h2>Approvals &mdash; Maker-Checker</h2>"
                + "<p><b>Who can access:</b> Branch Managers and above only (not visible to Tellers).</p>"
                + "<p>When a teller submits a withdrawal or transfer whose amount is larger than their "
                + "personal approval limit, it is not executed right away. Instead it is queued here as a "
                + "<i>pending approval</i> &mdash; a standard \"maker-checker\" dual control so no single "
                + "person can move a large sum of money alone. The teller who submitted it is the \"maker\"; "
                + "you, the manager, are the \"checker.\"</p>"
                + "<ol>"
                + "<li>The table lists every request still <b>PENDING</b>: its ID, type (WITHDRAW/TRANSFER), "
                + "the account(s) involved, the amount, who requested it, their note, and when it was "
                + "submitted. Click <b>Refresh</b> to pull the latest queue.</li>"
                + "<li>Select a row and click <b>Approve</b> to actually execute the withdrawal or transfer "
                + "through the same banking engine as a normal teller transaction (the resulting transaction "
                + "note is automatically prefixed <i>[Manager-approved]</i>), or click <b>Reject</b> to close "
                + "the request with no money moved. Either way you'll be prompted for a short decision note "
                + "that is saved alongside the request.</li>"
                + "</ol>"
                + "<p>Every user's own approval limit is shown on their <b>Security</b> tab. Managers and "
                + "Admins are given very high limits by default so their own transactions rarely queue.</p>"
                + "<p><b>Safety guarantees behind the scenes:</b></p>"
                + "<ul>"
                + "<li>A request can only be decided once. If it has already been approved or rejected "
                + "(for example by another manager, or accidentally double-clicked), a second attempt is "
                + "refused with an error instead of silently re-running the money movement or overwriting "
                + "the earlier decision.</li>"
                + "<li>The approve/reject decision is claimed atomically before any funds move, so if two "
                + "managers try to approve the exact same request at the same instant, only one of them can "
                + "win the claim &mdash; the money is moved exactly once, never twice.</li>"
                + "<li>If an approval is claimed but the underlying withdrawal/transfer then fails (for "
                + "example the account no longer has sufficient funds), the request is automatically put back "
                + "to PENDING rather than being left stuck in a falsely \"approved\" state with no money "
                + "actually moved.</li>"
                + "<li>Every approval and rejection is written to the <b>Audit Log</b> with the real prior "
                + "status, so the trail always reflects what genuinely happened to the request.</li>"
                + "</ul>");

        // ------------------------------------------------------------------
        // 19. General Ledger (manager+)
        // ------------------------------------------------------------------
        topics.put("19. General Ledger (Manager+)",
                "<h2>General Ledger</h2>"
                + "<p><b>Who can access:</b> Branch Managers and above only (not visible to Tellers).</p>"
                + "<p>A real double-entry accounting ledger running quietly behind every money-movement "
                + "screen. Deposits, withdrawals, loan disbursement/repayment, and payroll runs each post a "
                + "balanced debit and credit automatically, inside the same database transaction as the "
                + "underlying customer-facing action &mdash; so the ledger commits or rolls back together with "
                + "it and can never end up half-posted. This tab has three sub-tabs across the top.</p>"
                + "<p><b>Trial Balance:</b> click <b>Refresh</b> to see every GL account's totals (Cash, "
                + "Customer Deposits, Loans Receivable, Interest Income/Expense, Fee Income, Salaries Expense, "
                + "Owners Equity, etc). Total Debits should always equal Total Credits &mdash; the header shows "
                + "<b>(Balanced)</b> when they do. If this ever showed out of balance, it would mean a posting "
                + "bug somewhere wrote one leg without its matching other leg.</p>"
                + "<p><b>Journal:</b> the <i>General Journal</i> &mdash; every single posted debit/credit leg, "
                + "across every account, in the order it happened. Leave the From/To date fields blank to see "
                + "full history, or enter dates (YYYY-MM-DD) to narrow the range, then click <b>Refresh</b>.</p>"
                + "<p><b>Ledger:</b> the <i>General Ledger</i> for one account &mdash; pick an account from the "
                + "dropdown (optionally narrow by date), click <b>Refresh</b>, and see every posting for that "
                + "account with a running balance, like a classic T-account. When you narrow the range with a "
                + "From date, the running balance and the <b>Ending balance</b> shown below the table correctly "
                + "carry forward everything posted before that date, so they always reflect the account's real "
                + "balance &mdash; not just the net change within the filtered window.</p>"
                + "<p>You won't normally need to change anything here; it's a transparency/audit tool for "
                + "confirming the books actually balance and for tracing any single posting back to its source.</p>");

        // ------------------------------------------------------------------
        // 20. Financial Reports (manager+)
        // ------------------------------------------------------------------
        topics.put("20. Financial Reports (Manager+)",
                "<h2>Financial Reports</h2>"
                + "<p><b>Who can access:</b> Branch Managers and above only (not visible to Tellers).</p>"
                + "<p>Three standard financial statements, computed live from the General Ledger &mdash; nothing "
                + "here is manually entered, so they always match the books. Each has its own sub-tab.</p>"
                + "<p><b>Balance Sheet</b> (as of today): click <b>Refresh</b> to see Assets, Liabilities, and "
                + "Equity. \"Net Income To Date\" is added into Equity because this ledger doesn't run a formal "
                + "period-end closing entry &mdash; it keeps the sheet balanced (Assets = Liabilities + Equity) "
                + "without one. The footer confirms <b>(Balanced)</b> or flags an out-of-balance condition.</p>"
                + "<p><b>Income Statement:</b> enter an optional From/To date range (YYYY-MM-DD, blank = "
                + "all-time) and click <b>Refresh</b> to see Income accounts, Expense accounts, and the "
                + "resulting Net Income (or Net Loss) for that period. Note: in the current system, the only "
                + "postings are on the expense side (interest paid to depositors, payroll) &mdash; no fee or "
                + "interest-income postings exist yet &mdash; so this statement will show $0.00 Income and a "
                + "Net Loss until a revenue-generating feature is added.</p>"
                + "<p><b>Cash Flow:</b> enter an optional From/To date range and click <b>Refresh</b> to see "
                + "Beginning Cash Balance, then every cash movement grouped into <b>Operating</b> (customer "
                + "deposits/withdrawals, bill payments, wire transfers out, payroll), <b>Investing</b> (loan "
                + "funding activity, when it touches cash), and <b>Financing</b> (owner capital contributions), "
                + "ending with Net Change in Cash and Ending Cash Balance. Beginning Cash Balance correctly "
                + "reflects everything posted strictly before the From date, never double-counting the From "
                + "date's own activity. A footer line confirms the ending balance reconciles against the Cash "
                + "account's own ledger.</p>"
                + "<p>All three reports have an <b>Export CSV</b> button to save a copy for outside use.</p>");

        // ------------------------------------------------------------------
        // 21. Compliance (manager+)
        // ------------------------------------------------------------------
        topics.put("21. Compliance / SAR-CTR (Manager+)",
                "<h2>Compliance</h2>"
                + "<p><b>Who can access:</b> Branch Managers and above only (not visible to Tellers).</p>"
                + "<p>Two related tools live here: sanctions/PEP name screening, and formal SAR/CTR filing "
                + "against AML flags raised elsewhere in the app.</p>"
                + "<p><b>Sanctions/PEP Screening tab:</b> enter a Customer ID and click <b>Screen Customer</b> "
                + "to check their name against a sanctions/politically-exposed-persons watchlist (a simplified "
                + "stand-in for a real OFAC/PEP fuzzy-matching engine). The check compares the customer's name "
                + "and each watchlist name word-by-word and scores what fraction of the shorter name's words "
                + "are also found in the longer name: <b>CLEAR</b> (score under 40%), <b>POTENTIAL_MATCH</b> "
                + "(40% to just under 80%) &mdash; worth a manual look, or <b>CONFIRMED_MATCH</b> (80% or "
                + "higher). Every screening is saved to the results table below and logged to the audit trail, "
                + "whatever the outcome.</p>"
                + "<p><b>SAR/CTR Filing tab:</b> unreviewed AML flags (the same ones raised on the <b>AML "
                + "Flags</b> tab whenever a single transaction hits the $10,000 reporting threshold) appear "
                + "here as candidates. Select one, choose <b>SAR</b> (Suspicious Activity Report) or <b>CTR</b> "
                + "(Currency Transaction Report), write a narrative describing why it's being filed, and click "
                + "<b>File Report for Selected Flag</b>. Filing does two things together, as a single all-or-"
                + "nothing step: it creates the regulatory report with a reference number, and it marks the "
                + "source flag reviewed so it drops off the Unreviewed Flags list. That way a flag can never "
                + "end up double-filed because the \"mark reviewed\" half of the job silently failed after the "
                + "report was already created. Filed reports appear in the table at the bottom with their "
                + "reference number, related account, and who filed them.</p>");

        // ------------------------------------------------------------------
        // 22. Credit Scoring (manager+)
        // ------------------------------------------------------------------
        topics.put("22. Credit Scoring (Manager+)",
                "<h2>Credit Scoring / Underwriting</h2>"
                + "<p>Computes a simplified 300&ndash;850 score from relationship tenure, balances held, "
                + "on-time loan repayment history, KYC status, and any AML flags.</p>"
                + "<ol>"
                + "<li>Enter a <b>Customer ID</b> and click <b>Compute Score</b>.</li>"
                + "<li>The result and rating (POOR/FAIR/GOOD/VERY_GOOD/EXCELLENT) are shown and saved to "
                + "history, and to the customer's record for reference during loan review.</li>"
                + "</ol>"
                + "<p>Treat this as one input into a lending decision, not an automatic approve/deny.</p>");

        // ------------------------------------------------------------------
        // 23. Audit Log (admin)
        // ------------------------------------------------------------------
        topics.put("23. Audit Log (Admin)",
                "<h2>Audit Log</h2>"
                + "<p>A complete, read-only trail of every money-movement and account action taken in the "
                + "system &mdash; who did it, when, and the before/after values. Use this to investigate any "
                + "question about \"who did what.\"</p>");

        // ------------------------------------------------------------------
        // 24. Employees & Payroll (admin)
        // ------------------------------------------------------------------
        topics.put("24. Employees & Payroll (Admin)",
                "<h2>Employees &amp; Payroll</h2>"
                + "<ol>"
                + "<li>Maintain staff records: name, position, hourly rate.</li>"
                + "<li>Employees <b>Clock In</b>/<b>Clock Out</b> for their shifts.</li>"
                + "<li><b>Run Payroll</b> for a period to compute gross pay, tax withheld, and net pay from "
                + "clocked hours; history is kept per employee.</li>"
                + "</ol>");

        // ------------------------------------------------------------------
        // 25. Branches (admin)
        // ------------------------------------------------------------------
        topics.put("25. Branches (Admin)",
                "<h2>Branches</h2>"
                + "<p>A live overview of every branch: address, routing code, number of accounts, staff "
                + "count, and total deposits held.</p>"
                + "<p>Admins can click <b>Open New Branch</b> to add another location (name, address, and a "
                + "unique routing code).</p>");

        // ------------------------------------------------------------------
        // 26. Security (all users)
        // ------------------------------------------------------------------
        topics.put("26. Security (All Users)",
                "<h2>Security</h2>"
                + "<p>Everyone can manage their own account security here:</p>"
                + "<ul>"
                + "<li><b>Change Password:</b> enter your current password plus a new one twice. New "
                + "passwords must be at least 8 characters with an uppercase letter, a lowercase letter, a "
                + "digit, and a symbol.</li>"
                + "<li>The panel also shows whether two-factor authentication is enabled for your account, "
                + "and your personal approval limit for withdrawals/transfers.</li>"
                + "</ul>");

        // ------------------------------------------------------------------
        // FAQ
        // ------------------------------------------------------------------
        topics.put("FAQ / Troubleshooting",
                "<h2>FAQ / Troubleshooting</h2>"
                + "<p><b>Q: I get a database error on login.</b><br>"
                + "Make sure the MySQL80 service is running on the server, and that you have network "
                + "access to it.</p>"
                + "<p><b>Q: \"Account not found\" when I know the account exists.</b><br>"
                + "Double-check the account number format, e.g. <i>NYC-SAV-000123</i> &mdash; it's "
                + "case-sensitive and must match exactly.</p>"
                + "<p><b>Q: My withdrawal/transfer didn't go through immediately.</b><br>"
                + "If the amount is above your approval limit, it was sent to the <b>Approvals</b> queue for "
                + "a manager instead of failing &mdash; that's expected maker-checker behavior, not an error.</p>"
                + "<p><b>Q: A loan action is greyed out or refused.</b><br>"
                + "Approve/Reject/Disburse follow a strict status order (APPLIED &rarr; APPROVED &rarr; "
                + "DISBURSED); some actions also require Manager or Admin privileges.</p>"
                + "<p><b>Q: I didn't get my one-time passcode.</b><br>"
                + "In this environment the code is shown directly in the on-screen dialog rather than sent "
                + "by real SMS/email &mdash; check the \"Your code:\" message right after logging in with your "
                + "password.</p>"
                + "<p><b>Q: I was logged out while working.</b><br>"
                + "Sessions time out automatically after 10 minutes of inactivity for security. Just log back in.</p>"
                + "<p><b>Q: I forgot my password.</b><br>"
                + "Ask an administrator to reset it &mdash; there's no self-service reset without knowing the "
                + "current password.</p>");

        // ------------------------------------------------------------------
        // Contact
        // ------------------------------------------------------------------
        topics.put("Contact Support",
                "<h2>Contact Support</h2>"
                + "<p>NY Financial Bank &mdash; IT Support<br>"
                + "Phone: (212) 555-0142<br>"
                + "Email: support@nyfinancialbank.bank<br>"
                + "Hours: Mon&ndash;Fri, 8:00 AM &ndash; 6:00 PM</p>");
    }
}
