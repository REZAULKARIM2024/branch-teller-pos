package com.branchteller.service;

import com.branchteller.model.AuditLog;
import com.branchteller.model.User;
import com.branchteller.support.TestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Senior-QA-style integration coverage for the Security feature (AuthService's login/lockout,
 * OTP second factor, and self-service password change, shown on the Security tab and driving
 * LoginFrame), which had ZERO existing integration coverage before this class -- only the pure
 * hashing/complexity utility ({@code PasswordUtilTest}) was tested, never AuthService itself.
 *
 * <p>This review also found the shared test schema ({@code TestDatabase}) was missing the
 * failed_login_attempts / otp_required / approval_limit / password_last_changed columns and the
 * whole login_otps table entirely (Phase 18 columns, present in the real database/schema.sql but
 * never added here) -- meaning none of this could have been integration-tested against the
 * shared H2 database even if someone had tried. Both gaps are fixed by this class.
 *
 * <p>This review found one real defect, now fixed in {@link AuthService#changePassword}: the
 * password UPDATE and its audit log entry ran on two separate connections --
 * {@code UserDAO.changePassword(...)} used to open and commit its own connection internally,
 * completely independent of the audit log write. For a security-sensitive action like a password
 * change, that meant a failed audit insert could leave the password silently changed with zero
 * record of who changed it or when. Fixed by giving {@code UserDAO.changePassword} the caller's
 * connection and wrapping both writes in one explicit transaction, the same pattern already used
 * by ComplianceService/CreditScoreService/PayrollService. {@link
 * #changePassword_actuallyPersistsAndIsUsableForTheNextLogin_regressionTest()} guards the
 * practical effect of this fix.
 */
class AuthIntegrationTest {

    private final AuthService authService = new AuthService();
    private final AuditService auditService = new AuditService();

    @BeforeAll
    static void setUpSchema() throws Exception {
        TestDatabase.ensureSchema();
    }

    // ------------------------------------------------------------------
    // verifyPassword -- login
    // ------------------------------------------------------------------

    @Test
    void verifyPassword_withCorrectCredentials_returnsTheUser() throws Exception {
        int userId = TestDatabase.insertUserWithPassword("teller", "TELLER", "Correct#Horse1");

        Optional<User> result = authService.verifyPassword(userNameFor(userId), "Correct#Horse1");

        assertTrue(result.isPresent());
        assertEquals(userId, result.get().getId());
    }

    @Test
    void verifyPassword_withUnknownUsername_returnsEmpty_doesNotThrow() throws Exception {
        Optional<User> result = authService.verifyPassword("no-such-user-" + TestDatabase.nextSeq(), "whatever");
        assertTrue(result.isEmpty());
    }

    @Test
    void verifyPassword_withWrongPassword_returnsEmpty_andIncrementsFailedAttempts() throws Exception {
        int userId = TestDatabase.insertUserWithPassword("teller", "TELLER", "Correct#Horse1");
        String username = userNameFor(userId);

        Optional<User> result = authService.verifyPassword(username, "TotallyWrong1!");

        assertTrue(result.isEmpty());
        assertEquals(1, TestDatabase.failedLoginAttemptsOf(userId));
    }

    @Test
    void verifyPassword_withCorrectPasswordAfterPriorFailures_resetsFailedAttemptsToZero() throws Exception {
        int userId = TestDatabase.insertUserWithPassword("teller", "TELLER", "Correct#Horse1");
        String username = userNameFor(userId);

        authService.verifyPassword(username, "wrong1!!A");
        authService.verifyPassword(username, "wrong1!!A");
        assertEquals(2, TestDatabase.failedLoginAttemptsOf(userId));

        Optional<User> result = authService.verifyPassword(username, "Correct#Horse1");

        assertTrue(result.isPresent());
        assertEquals(0, TestDatabase.failedLoginAttemptsOf(userId), "A successful login must clear the failure count");
    }

    @Test
    void verifyPassword_afterFiveFailedAttempts_locksTheAccount_regressionTest() throws Exception {
        int userId = TestDatabase.insertUserWithPassword("teller", "TELLER", "Correct#Horse1");
        String username = userNameFor(userId);

        for (int i = 0; i < AuthService.MAX_FAILED_ATTEMPTS; i++) {
            assertTrue(authService.verifyPassword(username, "wrong1!!A").isEmpty());
        }
        assertEquals(AuthService.MAX_FAILED_ATTEMPTS, TestDatabase.failedLoginAttemptsOf(userId));

        assertThrows(AuthService.AccountLockedException.class,
                () -> authService.verifyPassword(username, "wrong1!!A"),
                "The 6th attempt (the failed count already at the max) must lock instead of silently failing again");
    }

    @Test
    void verifyPassword_lockedAccount_rejectsEvenTheCorrectPassword_regressionTest() throws Exception {
        // The lockout check runs BEFORE password verification -- once locked, the account stays
        // locked regardless of whether the *next* attempt happens to use the right password.
        // AuthService's own AccountLockedException message says "Contact an administrator to
        // reset", which is the intended remedy (there's deliberately no self-service unlock).
        int userId = TestDatabase.insertUserWithPassword("teller", "TELLER", "Correct#Horse1");
        String username = userNameFor(userId);

        for (int i = 0; i < AuthService.MAX_FAILED_ATTEMPTS; i++) {
            authService.verifyPassword(username, "wrong1!!A");
        }

        assertThrows(AuthService.AccountLockedException.class,
                () -> authService.verifyPassword(username, "Correct#Horse1"));
    }

    // ------------------------------------------------------------------
    // changePassword
    // ------------------------------------------------------------------

    @Test
    void changePassword_withWrongCurrentPassword_throwsWrongPasswordException_regressionTest() throws Exception {
        User user = registerAndFetch("Correct#Horse1");

        assertThrows(AuthService.WrongPasswordException.class,
                () -> authService.changePassword(user, "NotTheCurrentOne1!", "NewValid#Pass2"));
    }

    @Test
    void changePassword_withWeakNewPassword_throwsWeakPasswordException() throws Exception {
        User user = registerAndFetch("Correct#Horse1");

        assertThrows(AuthService.WeakPasswordException.class,
                () -> authService.changePassword(user, "Correct#Horse1", "short"));
    }

    @Test
    void changePassword_actuallyPersistsAndIsUsableForTheNextLogin_regressionTest() throws Exception {
        User user = registerAndFetch("Correct#Horse1");
        String username = user.getUsername();

        authService.changePassword(user, "Correct#Horse1", "BrandNew#Pass9");

        Optional<User> loginWithOld = authService.verifyPassword(username, "Correct#Horse1");
        assertTrue(loginWithOld.isEmpty(), "The old password must stop working immediately");

        Optional<User> loginWithNew = authService.verifyPassword(username, "BrandNew#Pass9");
        assertTrue(loginWithNew.isPresent(), "The new password must work for the very next login");
    }

    @Test
    void changePassword_updatesTheInMemoryUserObjectPassedIn() throws Exception {
        User user = registerAndFetch("Correct#Horse1");
        String oldHash = user.getPasswordHash();

        authService.changePassword(user, "Correct#Horse1", "BrandNew#Pass9");

        assertNotEquals(oldHash, user.getPasswordHash(),
                "The caller's User object should reflect the new hash without needing a re-fetch");
    }

    @Test
    void changePassword_writesAuditTrailEntry_regressionTest() throws Exception {
        User user = registerAndFetch("Correct#Horse1");

        authService.changePassword(user, "Correct#Horse1", "BrandNew#Pass9");

        List<AuditLog> logs = auditService.byEntityType("user", 500);
        boolean found = logs.stream().anyMatch(l -> l.getEntityId() == user.getId() && "PASSWORD_CHANGED".equals(l.getAction()));
        assertTrue(found, "Expected a PASSWORD_CHANGED audit entry for the user");
    }

    // ------------------------------------------------------------------
    // OTP second factor
    // ------------------------------------------------------------------

    @Test
    void issueOtp_generatesASixDigitCode() throws Exception {
        User user = registerAndFetch("Correct#Horse1");
        String otp = authService.issueOtp(user);
        assertEquals(6, otp.length());
        assertTrue(otp.chars().allMatch(Character::isDigit));
    }

    @Test
    void verifyOtp_withTheIssuedCode_succeeds_andConsumesIt() throws Exception {
        User user = registerAndFetch("Correct#Horse1");
        String otp = authService.issueOtp(user);

        assertTrue(authService.verifyOtp(user, otp));
        assertFalse(authService.verifyOtp(user, otp), "A used OTP must not be usable a second time");
    }

    @Test
    void verifyOtp_withAWrongCode_fails() throws Exception {
        User user = registerAndFetch("Correct#Horse1");
        authService.issueOtp(user);

        assertFalse(authService.verifyOtp(user, "000000"));
    }

    @Test
    void verifyOtp_withAnExpiredCode_fails_regressionTest() throws Exception {
        User user = registerAndFetch("Correct#Horse1");
        TestDatabase.insertOtpAt(user.getId(), "654321", LocalDateTime.now().minusMinutes(1), false);

        assertFalse(authService.verifyOtp(user, "654321"), "An expired code must be rejected even though it was never used");
    }

    @Test
    void verifyOtp_toleratesSurroundingWhitespaceInTheEnteredCode() throws Exception {
        User user = registerAndFetch("Correct#Horse1");
        String otp = authService.issueOtp(user);

        assertTrue(authService.verifyOtp(user, "  " + otp + "  "));
    }

    @Test
    void verifyOtp_writesAuditTrailEntries_forBothSuccessAndFailure() throws Exception {
        User user = registerAndFetch("Correct#Horse1");
        String otp = authService.issueOtp(user);

        authService.verifyOtp(user, "000000");
        authService.verifyOtp(user, otp);

        List<AuditLog> logs = auditService.byEntityType("user", 500);
        boolean sawFailed = logs.stream().anyMatch(l -> l.getEntityId() == user.getId() && "OTP_FAILED".equals(l.getAction()));
        boolean sawVerified = logs.stream().anyMatch(l -> l.getEntityId() == user.getId() && "OTP_VERIFIED".equals(l.getAction()));
        assertTrue(sawFailed, "Expected an OTP_FAILED entry for the wrong code");
        assertTrue(sawVerified, "Expected an OTP_VERIFIED entry for the correct code");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private User registerAndFetch(String password) throws Exception {
        int userId = TestDatabase.insertUserWithPassword("secuser", "TELLER", password);
        return authService.verifyPassword(userNameFor(userId), password)
                .orElseThrow(() -> new AssertionError("Fixture setup failed: could not log in as the just-created user"));
    }

    /** insertUserWithPassword() generates its own unique username internally, so tests that only
     *  have the ID look it up once via a fresh row read -- cheaper than plumbing the generated
     *  username back out of the insert helper. */
    private String userNameFor(int userId) throws java.sql.SQLException {
        try (var conn = com.branchteller.config.DBConnection.getConnection();
             var ps = conn.prepareStatement("SELECT username FROM users WHERE user_id = ?")) {
            ps.setInt(1, userId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }
}
