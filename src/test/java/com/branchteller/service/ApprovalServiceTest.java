package com.branchteller.service;

import com.branchteller.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the maker-checker threshold check: requiresApproval() is pure logic
 * (no database) that decides whether a WITHDRAW/TRANSFER must be queued for a manager
 * instead of executing immediately.
 */
class ApprovalServiceTest {

    private final ApprovalService approvalService = new ApprovalService();

    private User userWithLimit(BigDecimal limit) {
        User u = new User();
        u.setApprovalLimit(limit);
        return u;
    }

    @Test
    void amountBelowLimit_doesNotRequireApproval() {
        User teller = userWithLimit(new BigDecimal("5000.00"));
        assertFalse(approvalService.requiresApproval(teller, new BigDecimal("4999.99")));
    }

    @Test
    void amountEqualToLimit_doesNotRequireApproval() {
        // The check is strictly-greater-than, so an amount exactly at the limit is still
        // within a teller's own authority.
        User teller = userWithLimit(new BigDecimal("5000.00"));
        assertFalse(approvalService.requiresApproval(teller, new BigDecimal("5000.00")));
    }

    @Test
    void amountAboveLimit_requiresApproval() {
        User teller = userWithLimit(new BigDecimal("5000.00"));
        assertTrue(approvalService.requiresApproval(teller, new BigDecimal("5000.01")));
    }

    @Test
    void managerWithHigherLimit_notTriggeredByTellerScaleAmount() {
        User manager = userWithLimit(new BigDecimal("1000000.00"));
        assertFalse(approvalService.requiresApproval(manager, new BigDecimal("50000.00")));
    }

    @Test
    void evenManagerLimitCanBeExceeded() {
        User manager = userWithLimit(new BigDecimal("1000000.00"));
        assertTrue(approvalService.requiresApproval(manager, new BigDecimal("1000000.01")));
    }

    // ---------- role/limit x amount permission matrix (data-driven) ----------

    @ParameterizedTest
    @CsvSource({
            // limit,       amount,      requiresApproval
            "5000.00,       1.00,        false",
            "5000.00,       5000.00,     false",
            "5000.00,       5000.01,     true",
            "5000.00,       100000.00,   true",
            "1000000.00,    999999.99,   false",
            "1000000.00,    1000000.00,  false",
            "1000000.00,    1000000.01,  true",
            "0.00,          0.01,        true"
    })
    void requiresApproval_matchesExpectedOutcomeAcrossLimitAmountMatrix(
            String limit, String amount, boolean expected) {
        User user = userWithLimit(new BigDecimal(limit));
        assertEquals(expected, approvalService.requiresApproval(user, new BigDecimal(amount)));
    }
}
