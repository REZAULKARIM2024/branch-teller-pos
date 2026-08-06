package com.branchteller.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Permission-focused unit tests for the role checks the rest of the app relies on
 * (manager/admin-only tabs and actions in the Swing app, ApprovalService's dual-control
 * threshold). Pure logic on the User model -- no database.
 */
class UserPermissionTest {

    @ParameterizedTest
    @CsvSource({
            "ADMIN, true",
            "MANAGER, true",
            "TELLER, false"
    })
    void isManagerOrAbove_matchesExpectedRoleMatrix(String role, boolean expected) {
        User user = new User(1, "u", "Full Name", role, 1);
        assertEquals(expected, user.isManagerOrAbove());
    }

    @Test
    void isManagerOrAbove_isCaseSensitive_unknownCasingIsNotElevated() {
        User user = new User(1, "u", "Full Name", "admin", 1); // lowercase -- not the stored convention
        assertFalse(user.isManagerOrAbove());
    }

    @Test
    void newUser_defaultApprovalLimitIs5000() {
        User user = new User();
        assertEquals(0, new BigDecimal("5000").compareTo(user.getApprovalLimit()));
    }

    @Test
    void approvalLimit_canBeOverridden() {
        User user = new User();
        user.setApprovalLimit(new BigDecimal("1000000"));
        assertEquals(0, new BigDecimal("1000000").compareTo(user.getApprovalLimit()));
    }

    @Test
    void newUser_otpRequiredDefaultsToTrue() {
        User user = new User();
        assertTrue(user.isOtpRequired());
    }

    @Test
    void otpRequired_canBeDisabled() {
        User user = new User();
        user.setOtpRequired(false);
        assertFalse(user.isOtpRequired());
    }

    @Test
    void newUser_failedLoginAttemptsDefaultsToZero() {
        User user = new User();
        assertEquals(0, user.getFailedLoginAttempts());
    }

    @Test
    void failedLoginAttempts_roundTripsThroughSetter() {
        User user = new User();
        user.setFailedLoginAttempts(3);
        assertEquals(3, user.getFailedLoginAttempts());
    }

    @Test
    void roleAndBranch_roundTripThroughConstructor() {
        User user = new User(42, "jdoe", "Jane Doe", "TELLER", 7);
        assertEquals(42, user.getId());
        assertEquals("jdoe", user.getUsername());
        assertEquals("Jane Doe", user.getFullName());
        assertEquals("TELLER", user.getRole());
        assertEquals(7, user.getBranchId());
    }

    @Test
    void active_defaultsToFalseUntilExplicitlySet() {
        // Documents the current construction contract: active isn't implicitly true just
        // because a User object exists -- callers (AuthService/UserDAO) must set it from
        // the DB row explicitly.
        User user = new User(1, "u", "Full Name", "TELLER", 1);
        assertFalse(user.isActive());
    }
}
