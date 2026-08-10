package com.branchteller.service;

import com.branchteller.config.DBConnection;
import com.branchteller.dao.AmlDAO;
import com.branchteller.dao.ComplianceDAO;
import com.branchteller.dao.CustomerDAO;
import com.branchteller.model.Customer;
import com.branchteller.model.RegulatoryReport;
import com.branchteller.model.ScreeningResult;
import com.branchteller.model.SuspiciousActivityFlag;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Simplified sanctions/PEP screening: word-overlap name matching against sanctions_list
 * (a stand-in for a real OFAC/PEP fuzzy-matching engine), plus SAR/CTR regulatory report
 * generation from existing suspicious-activity flags.
 */
public class ComplianceService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final ComplianceDAO complianceDAO = new ComplianceDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final AmlDAO amlDAO = new AmlDAO();
    private final AuditService auditService = new AuditService();

    public ScreeningResult screenCustomer(int customerId, int actorId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            Customer customer = customerDAO.findById(conn, customerId)
                    .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));

            List<String[]> list = complianceDAO.sanctionsList(conn);
            double bestScore = 0;
            Integer bestEntryId = null;
            for (String[] entry : list) {
                double score = nameSimilarity(customer.getFullName(), entry[1]);
                if (score > bestScore) {
                    bestScore = score;
                    bestEntryId = Integer.parseInt(entry[0]);
                }
            }

            String status = bestScore >= 0.8 ? "CONFIRMED_MATCH" : bestScore >= 0.4 ? "POTENTIAL_MATCH" : "CLEAR";

            ScreeningResult r = new ScreeningResult();
            r.setCustomerId(customerId);
            r.setMatchedEntryId(status.equals("CLEAR") ? null : bestEntryId);
            r.setMatchScore(Math.round(bestScore * 10000.0) / 100.0);
            r.setStatus(status);
            int id = complianceDAO.insertScreeningResult(conn, r);
            r.setId(id);

            auditService.log(conn, actorId, "AML_SCREENING", "customer", customerId, null,
                    "Screened: " + status + " (score " + r.getMatchScore() + ")");
            return r;
        }
    }

    /** Word-overlap similarity in [0,1]: fraction of shorter name's tokens found in the longer name. */
    private double nameSimilarity(String a, String b) {
        Set<String> tokensA = new HashSet<>(Arrays.asList(a.toUpperCase().split("\\s+")));
        Set<String> tokensB = new HashSet<>(Arrays.asList(b.toUpperCase().split("\\s+")));
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0;
        Set<String> smaller = tokensA.size() <= tokensB.size() ? tokensA : tokensB;
        Set<String> larger = tokensA.size() <= tokensB.size() ? tokensB : tokensA;
        long matches = smaller.stream().filter(larger::contains).count();
        return (double) matches / smaller.size();
    }

    public List<ScreeningResult> allScreeningResults() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return complianceDAO.allScreeningResults(conn);
        }
    }

    /**
     * Files a SAR/CTR AND marks the source flag reviewed in one atomic transaction.
     *
     * QA finding (fixed): this used to be two separate service calls -- ComplianceService.fileReport()
     * followed by a second, independent AmlService.markReviewed() call made by the GUI after this one
     * returned. If the first succeeded but the second failed (dropped connection, concurrent edit, etc.),
     * a regulatory report would exist referencing a flag that was STILL "unreviewed" -- so it would keep
     * showing up in the Unreviewed Flags list and could be filed a second time as a duplicate SAR/CTR for
     * the exact same suspicious activity. Doing both writes on one connection/transaction here closes
     * that window: either both the filing and the review land, or neither does.
     */
    public RegulatoryReport fileReport(String reportType, SuspiciousActivityFlag flag, int filedBy, String narrative) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                RegulatoryReport r = new RegulatoryReport();
                r.setReportType(reportType);
                r.setReferenceNo(reportType + "-" + System.currentTimeMillis() % 1000000 + "-" + (100 + RANDOM.nextInt(900)));
                r.setRelatedAccountId(flag.getAccountId());
                r.setRelatedFlagId(flag.getId());
                r.setFiledBy(filedBy);
                r.setNarrative(narrative);
                int id = complianceDAO.insertReport(conn, r);
                r.setId(id);

                boolean flagUpdated = amlDAO.markReviewed(conn, flag.getId(), filedBy);
                if (!flagUpdated) {
                    throw new IllegalArgumentException("AML flag not found: " + flag.getId());
                }

                auditService.log(conn, filedBy, reportType + "_FILED", "regulatory_report", id, null, r.getReferenceNo());
                auditService.log(conn, filedBy, "AML_FLAG_REVIEWED", "aml_flag", flag.getId(), "UNREVIEWED", "REVIEWED");

                conn.commit();
                return r;
            } catch (Exception e) {
                conn.rollback();
                if (e instanceof SQLException) throw (SQLException) e;
                throw (RuntimeException) e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public List<RegulatoryReport> allReports() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return complianceDAO.allReports(conn);
        }
    }
}
