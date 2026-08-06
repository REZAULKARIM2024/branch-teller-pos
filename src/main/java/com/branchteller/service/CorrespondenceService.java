package com.branchteller.service;

import com.branchteller.config.DBConnection;
import com.branchteller.dao.AccountDAO;
import com.branchteller.dao.CustomerDAO;
import com.branchteller.dao.InterestAccrualDAO;
import com.branchteller.dao.LoanDAO;
import com.branchteller.i18n.Messages;
import com.branchteller.model.Account;
import com.branchteller.model.Customer;
import com.branchteller.model.InterestAccrual;
import com.branchteller.model.Loan;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates official bank correspondence -- letters and certificates handed to customers
 * or third parties, built from live account/customer/loan data. Follows the same pattern
 * as StatementService: return plain-text lines, ready for PrintableText or an on-screen
 * preview, so both printing and saving to file are free.
 */
public class CorrespondenceService {

    public enum LetterType {
        ACCOUNT_OPENING("corr.letterType.accountOpening"),
        BALANCE_CERTIFICATE("corr.letterType.balanceCert"),
        NO_OBJECTION_CERTIFICATE("corr.letterType.noc"),
        LOAN_SANCTION("corr.letterType.loanSanction"),
        REFERENCE_LETTER("corr.letterType.reference"),
        ACCOUNT_CLOSURE("corr.letterType.closure"),
        INTEREST_CERTIFICATE("corr.letterType.interestCert");

        private final String messageKey;
        LetterType(String messageKey) { this.messageKey = messageKey; }
        @Override public String toString() { return Messages.tr(messageKey); }
    }

    private static final String BANK_NAME = "NY Financial Bank";
    private static final String BANK_ADDRESS = "1 Market Plaza, New York, NY 10004";
    private static final String BANK_CONTACT = "Phone: (212) 555-0142   |   support@nyfinancialbank.bank";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMMM yyyy");

    private final AccountDAO accountDAO = new AccountDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final LoanDAO loanDAO = new LoanDAO();
    private final InterestAccrualDAO interestAccrualDAO = new InterestAccrualDAO();

    /**
     * @param accountNumber required for every type except LOAN_SANCTION
     * @param loanIdRaw     required only for LOAN_SANCTION
     * @param extra         purpose (NOC), addressee (REFERENCE_LETTER), or year (INTEREST_CERTIFICATE)
     */
    public List<String> generate(LetterType type, String accountNumber, String loanIdRaw, String extra) throws SQLException {
        return switch (type) {
            case ACCOUNT_OPENING -> accountOpeningLetter(accountNumber);
            case BALANCE_CERTIFICATE -> balanceCertificate(accountNumber);
            case NO_OBJECTION_CERTIFICATE -> noc(accountNumber, extra);
            case LOAN_SANCTION -> loanSanctionLetter(loanIdRaw);
            case REFERENCE_LETTER -> referenceLetter(accountNumber, extra);
            case ACCOUNT_CLOSURE -> accountClosureLetter(accountNumber);
            case INTEREST_CERTIFICATE -> interestCertificate(accountNumber, extra);
        };
    }

    // ---- shared letterhead / footer ----

    private List<String> letterhead(String refPrefix, int refId) {
        List<String> lines = new ArrayList<>();
        lines.add(BANK_NAME.toUpperCase());
        lines.add(BANK_ADDRESS);
        lines.add(BANK_CONTACT);
        lines.add("=".repeat(72));
        lines.add("Ref: " + refPrefix + "-" + refId + "/" + LocalDate.now().getYear());
        lines.add("Date: " + DATE_FMT.format(LocalDate.now()));
        lines.add("");
        return lines;
    }

    private void footer(List<String> lines) {
        lines.add("");
        lines.add("");
        lines.add("For " + BANK_NAME + ",");
        lines.add("");
        lines.add("");
        lines.add("_______________________________");
        lines.add("Authorized Signatory / Branch Manager");
        lines.add("");
        lines.add("This is a system-generated letter and does not require a physical signature");
        lines.add("unless presented for legal or regulatory purposes.");
    }

    private Account requireAccount(Connection conn, String accountNumber) throws SQLException {
        String trimmed = accountNumber == null ? "" : accountNumber.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("Enter an account number.");
        return accountDAO.findByAccountNumber(conn, trimmed)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + trimmed));
    }

    // ---- letters ----

    private List<String> accountOpeningLetter(String accountNumber) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            Account acct = requireAccount(conn, accountNumber);
            Customer cust = customerDAO.findById(conn, acct.getCustomerId()).orElse(null);

            List<String> lines = letterhead("AOC", acct.getId());
            lines.add("To,");
            lines.add(acct.getCustomerName());
            if (cust != null && cust.getAddress() != null && !cust.getAddress().isBlank()) lines.add(cust.getAddress());
            lines.add("");
            lines.add("Subject: Confirmation of Account Opening");
            lines.add("");
            lines.add("Dear " + acct.getCustomerName() + ",");
            lines.add("");
            lines.add("We are pleased to confirm that your " + acct.getAccountType() + " account has been");
            lines.add("successfully opened with us. The details of your account are as follows:");
            lines.add("");
            lines.add("    Account Number   : " + acct.getAccountNumber());
            lines.add("    Account Type     : " + acct.getAccountType());
            lines.add("    Date Opened      : " + (acct.getOpenedDate() == null ? "-" : DATE_FMT.format(acct.getOpenedDate())));
            lines.add("    Interest Rate    : " + acct.getInterestRate() + "% p.a.");
            lines.add("    Account Status   : " + acct.getStatus());
            lines.add("");
            lines.add("Thank you for choosing " + BANK_NAME + ". We look forward to serving your");
            lines.add("banking needs.");
            footer(lines);
            return lines;
        }
    }

    private List<String> balanceCertificate(String accountNumber) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            Account acct = requireAccount(conn, accountNumber);

            List<String> lines = letterhead("BAL", acct.getId());
            lines.add("BALANCE CERTIFICATE");
            lines.add("");
            lines.add("This is to certify that, as per our records, the account below maintained");
            lines.add("with " + BANK_NAME + " shows the balance stated as of the date of this certificate:");
            lines.add("");
            lines.add("    Account Holder   : " + acct.getCustomerName());
            lines.add("    Account Number   : " + acct.getAccountNumber());
            lines.add("    Account Type     : " + acct.getAccountType());
            lines.add("    Account Status   : " + acct.getStatus());
            lines.add("    Current Balance  : $" + acct.getBalance());
            lines.add("");
            lines.add("This certificate is issued upon the account holder's request for whatever");
            lines.add("legitimate purpose it may serve.");
            footer(lines);
            return lines;
        }
    }

    private List<String> noc(String accountNumber, String purpose) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            Account acct = requireAccount(conn, accountNumber);
            String purposeText = (purpose == null || purpose.isBlank())
                    ? "the purpose stated by the account holder" : purpose.trim();

            List<String> lines = letterhead("NOC", acct.getId());
            lines.add("NO OBJECTION CERTIFICATE");
            lines.add("");
            lines.add("This is to certify that " + acct.getCustomerName() + ", holder of account number");
            lines.add(acct.getAccountNumber() + " with " + BANK_NAME + ", is in good standing with this");
            lines.add("institution, and " + BANK_NAME + " has NO OBJECTION to " + purposeText + ".");
            lines.add("");
            lines.add("    Account Status   : " + acct.getStatus());
            lines.add("    Current Balance  : $" + acct.getBalance());
            lines.add("");
            lines.add("This certificate is issued without any liability on the part of " + BANK_NAME + ".");
            footer(lines);
            return lines;
        }
    }

    private List<String> loanSanctionLetter(String loanIdRaw) throws SQLException {
        int loanId;
        try {
            loanId = Integer.parseInt(loanIdRaw == null ? "" : loanIdRaw.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Enter a valid numeric loan ID.");
        }
        try (Connection conn = DBConnection.getConnection()) {
            Loan loan = loanDAO.findById(conn, loanId)
                    .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));

            List<String> lines = letterhead("LSL", loan.getId());
            lines.add("To,");
            lines.add(loan.getCustomerName());
            lines.add("");
            lines.add("Subject: " + ("APPLIED".equals(loan.getStatus()) ? "Loan Application Received" : "Sanction of " + loan.getLoanType() + " Loan"));
            lines.add("");
            lines.add("Dear " + loan.getCustomerName() + ",");
            lines.add("");
            if ("APPLIED".equals(loan.getStatus())) {
                lines.add("We confirm receipt of your loan application, currently under review. Once a");
                lines.add("decision is made, a follow-up letter will confirm approval or rejection.");
            } else {
                lines.add("We are pleased to inform you that the following credit facility has been");
                lines.add(loan.getStatus().toLowerCase() + " on the terms summarized below:");
            }
            lines.add("");
            lines.add("    Loan ID          : " + loan.getId());
            lines.add("    Loan Type        : " + loan.getLoanType());
            lines.add("    Linked Account   : " + loan.getAccountNumber());
            lines.add("    Principal Amount : $" + loan.getPrincipal());
            lines.add("    Interest Rate    : " + loan.getInterestRate() + "% p.a.");
            lines.add("    Tenure           : " + loan.getTenureMonths() + " months");
            lines.add("    Applied Date     : " + (loan.getAppliedDate() == null ? "-" : DATE_FMT.format(loan.getAppliedDate())));
            lines.add("    Status           : " + loan.getStatus());
            if (loan.getDisbursedDate() != null) {
                lines.add("    Disbursed Date   : " + DATE_FMT.format(loan.getDisbursedDate()));
            }
            lines.add("");
            lines.add("This facility, once disbursed, is subject to the standard terms and conditions");
            lines.add("of " + BANK_NAME + " governing loans of this type, including repayment via the");
            lines.add("agreed EMI schedule.");
            footer(lines);
            return lines;
        }
    }

    private List<String> referenceLetter(String accountNumber, String addressedTo) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            Account acct = requireAccount(conn, accountNumber);
            String addressee = (addressedTo == null || addressedTo.isBlank()) ? "To Whom It May Concern" : addressedTo.trim();

            List<String> lines = letterhead("REF", acct.getId());
            lines.add(addressee + ",");
            lines.add("");
            lines.add("Subject: Bank Reference for " + acct.getCustomerName());
            lines.add("");
            lines.add("This is to confirm that " + acct.getCustomerName() + " has maintained a " + acct.getAccountType());
            lines.add("account with " + BANK_NAME + " since " +
                    (acct.getOpenedDate() == null ? "an unspecified date" : DATE_FMT.format(acct.getOpenedDate())) + ".");
            lines.add("");
            lines.add("The account is presently " + acct.getStatus().toLowerCase() + " and, to the best of our");
            lines.add("knowledge, has been operated in a satisfactory manner throughout the relationship.");
            lines.add("");
            lines.add("This reference is provided in good faith based on our records at the time of");
            lines.add("issue, without any responsibility or liability on the part of " + BANK_NAME + " or");
            lines.add("its officers.");
            footer(lines);
            return lines;
        }
    }

    private List<String> accountClosureLetter(String accountNumber) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            Account acct = requireAccount(conn, accountNumber);
            boolean closed = "CLOSED".equalsIgnoreCase(acct.getStatus());

            List<String> lines = letterhead("CLS", acct.getId());
            lines.add("To,");
            lines.add(acct.getCustomerName());
            lines.add("");
            lines.add("Subject: Account Closure " + (closed ? "Confirmation" : "Status"));
            lines.add("");
            lines.add("Dear " + acct.getCustomerName() + ",");
            lines.add("");
            if (closed) {
                lines.add("This letter confirms that your account below has been closed and the final");
                lines.add("balance settled as per our records.");
            } else {
                lines.add("This letter documents the current status of your account with reference to");
                lines.add("a closure request. The account is presently recorded as follows:");
            }
            lines.add("");
            lines.add("    Account Number   : " + acct.getAccountNumber());
            lines.add("    Account Type     : " + acct.getAccountType());
            lines.add("    Account Status   : " + acct.getStatus());
            lines.add("    Balance on Record: $" + acct.getBalance());
            lines.add("");
            lines.add("Thank you for having banked with " + BANK_NAME + ".");
            footer(lines);
            return lines;
        }
    }

    private List<String> interestCertificate(String accountNumber, String yearRaw) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            Account acct = requireAccount(conn, accountNumber);
            String year = (yearRaw == null || yearRaw.isBlank()) ? String.valueOf(LocalDate.now().getYear()) : yearRaw.trim();

            List<InterestAccrual> accruals = interestAccrualDAO.findByAccountId(conn, acct.getId());
            List<InterestAccrual> forYear = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            for (InterestAccrual a : accruals) {
                if (a.getPeriod() != null && a.getPeriod().startsWith(year)) {
                    forYear.add(a);
                    total = total.add(a.getAmount());
                }
            }

            List<String> lines = letterhead("INT", acct.getId());
            lines.add("CERTIFICATE OF INTEREST EARNED - " + year);
            lines.add("");
            lines.add("This is to certify that the account detailed below earned the following");
            lines.add("interest during " + year + ", as per our records:");
            lines.add("");
            lines.add("    Account Holder   : " + acct.getCustomerName());
            lines.add("    Account Number   : " + acct.getAccountNumber());
            lines.add("    Account Type     : " + acct.getAccountType());
            lines.add("");
            if (forYear.isEmpty()) {
                lines.add("    (no interest accrual records found for " + year + ")");
            } else {
                lines.add(String.format("    %-10s %10s %14s", "Period", "Rate %", "Amount"));
                for (InterestAccrual a : forYear) {
                    lines.add(String.format("    %-10s %10s %14s", a.getPeriod(), a.getRateApplied(), "$" + a.getAmount()));
                }
            }
            lines.add("");
            lines.add("    Total Interest Earned in " + year + ": $" + total);
            lines.add("");
            lines.add("This certificate is issued for the account holder's tax or personal records.");
            footer(lines);
            return lines;
        }
    }
}
