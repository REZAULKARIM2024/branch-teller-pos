package com.branchteller.service;

import com.branchteller.config.DBConnection;
import com.branchteller.dao.CreditScoreDAO;
import com.branchteller.dao.CustomerDAO;
import com.branchteller.model.Customer;
import com.branchteller.model.CreditScoreHistory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Simplified underwriting score in the familiar 300-850 range. Not a real bureau score --
 * a transparent heuristic built from data already in this system: relationship tenure,
 * balance held, on-time loan repayment history, KYC status, and AML flags. Feeds into
 * loan approval decisions as a supporting signal, not an automatic approve/deny.
 */
public class CreditScoreService {

    private final CreditScoreDAO creditScoreDAO = new CreditScoreDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final AuditService auditService = new AuditService();

    /**
     * QA finding (fixed): the three writes below -- history row, the customer's stored
     * credit_score column, and the audit log entry -- used to run as three independent
     * autocommit statements. If the connection dropped or an exception hit between them, the
     * credit_score_history table and the customers.credit_score column could disagree about a
     * customer's latest score. Nothing in the UI currently reads customers.credit_score (only
     * the history feed is displayed), so this was low-impact today, but it's a real
     * data-integrity gap the moment anything starts trusting that column. Wrapping all three
     * writes in one transaction closes it, matching the same fix applied to
     * ComplianceService#fileReport this session.
     */
    public CreditScoreHistory computeScore(int customerId, int actorId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Customer customer = customerDAO.findById(conn, customerId)
                        .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));

                BigDecimal balance = creditScoreDAO.totalBalance(conn, customerId);
                LocalDate earliest = creditScoreDAO.earliestAccountDate(conn, customerId);
                long tenureMonths = earliest == null ? 0 : ChronoUnit.MONTHS.between(earliest, LocalDate.now());
                double onTimeRatio = creditScoreDAO.loanOnTimeRatio(conn, customerId);
                int flagCount = creditScoreDAO.amlFlagCount(conn, customerId);
                boolean verifiedKyc = "VERIFIED".equals(customer.getKycStatus());

                int score = 300; // floor
                // Balance held: up to 150 points, saturating around $50,000
                double balancePoints = Math.min(150, balance.doubleValue() / 50000.0 * 150);
                // Relationship tenure: up to 120 points, saturating at 10 years (120 months)
                double tenurePoints = Math.min(120, tenureMonths / 120.0 * 120);
                // On-time repayment history: up to 200 points. Note: a customer with NO loan
                // repayments at all also gets the full 200 here (loanOnTimeRatio() returns 1.0
                // when there's nothing to judge) -- so "no borrowing history" scores exactly as
                // well as "perfect repayment history", and strictly BETTER than any customer who
                // has actually missed or paid a single installment late. This is a documented
                // characteristic of the simplified heuristic, not a bug -- see
                // CreditScoreIntegrationTest for tests that pin this behavior down.
                double repaymentPoints = onTimeRatio * 200;
                // KYC verified: flat 60 points
                double kycPoints = verifiedKyc ? 60 : 0;
                // Base points so an average clean customer lands in the "GOOD" band
                double basePoints = 20;

                score += (int) Math.round(balancePoints + tenurePoints + repaymentPoints + kycPoints + basePoints);
                // AML flags are a heavy penalty: 40 points each
                score -= flagCount * 40;
                score = Math.max(300, Math.min(850, score));

                String rating = rate(score);

                creditScoreDAO.insertHistory(conn, customerId, score, rating);
                customerDAO.updateCreditScore(conn, customerId, score);
                auditService.log(conn, actorId, "CREDIT_SCORE_COMPUTED", "customer", customerId, null,
                        "Score " + score + " (" + rating + ")");

                conn.commit();

                CreditScoreHistory h = new CreditScoreHistory();
                h.setCustomerId(customerId);
                h.setCustomerName(customer.getFullName());
                h.setScore(score);
                h.setRating(rating);
                return h;
            } catch (Exception e) {
                conn.rollback();
                if (e instanceof SQLException) throw (SQLException) e;
                throw (RuntimeException) e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private String rate(int score) {
        if (score >= 800) return "EXCELLENT";
        if (score >= 740) return "VERY_GOOD";
        if (score >= 670) return "GOOD";
        if (score >= 580) return "FAIR";
        return "POOR";
    }

    public List<CreditScoreHistory> historyForCustomer(int customerId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return creditScoreDAO.findByCustomer(conn, customerId);
        }
    }

    public List<CreditScoreHistory> recentAll(int limit) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return creditScoreDAO.recentAll(conn, limit);
        }
    }
}
