package com.branchteller.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security-focused unit tests for the SHA-256 + per-user-salt password hashing and the
 * bank-grade complexity policy. All pure functions -- no database needed.
 */
class PasswordUtilTest {

    // ---------- hashing ----------

    @Test
    void hash_isDeterministic_forSameInputPasswordAndSalt() {
        String salt = PasswordUtil.generateSalt();
        assertEquals(PasswordUtil.hash("hunter2", salt), PasswordUtil.hash("hunter2", salt));
    }

    @Test
    void hash_differsAcrossDifferentSalts_forTheSamePassword() {
        String saltA = PasswordUtil.generateSalt();
        String saltB = PasswordUtil.generateSalt();
        assertNotEquals(PasswordUtil.hash("hunter2", saltA), PasswordUtil.hash("hunter2", saltB));
    }

    @Test
    void hash_differsAcrossDifferentPasswords_forTheSameSalt() {
        String salt = PasswordUtil.generateSalt();
        assertNotEquals(PasswordUtil.hash("hunter2", salt), PasswordUtil.hash("hunter3", salt));
    }

    @Test
    void hash_neverEqualsThePlaintextPassword() {
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hash("hunter2", salt);
        assertNotEquals("hunter2", hash);
    }

    @Test
    void hash_producesValidBase64() {
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hash("hunter2", salt);
        assertDoesNotThrow(() -> Base64.getDecoder().decode(hash));
    }

    // ---------- salt ----------

    @Test
    void generateSalt_producesUniqueValuesAcrossManyCalls() {
        Set<String> salts = new HashSet<>();
        for (int i = 0; i < 200; i++) salts.add(PasswordUtil.generateSalt());
        assertEquals(200, salts.size(), "200 generated salts should all be distinct");
    }

    @Test
    void generateSalt_isValidBase64() {
        assertDoesNotThrow(() -> Base64.getDecoder().decode(PasswordUtil.generateSalt()));
    }

    @Test
    void generateSalt_decodesTo16Bytes() {
        byte[] decoded = Base64.getDecoder().decode(PasswordUtil.generateSalt());
        assertEquals(16, decoded.length);
    }

    // ---------- verify ----------

    @Test
    void verify_succeedsForTheCorrectPassword() {
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hash("correctHorse1!", salt);
        assertTrue(PasswordUtil.verify("correctHorse1!", salt, hash));
    }

    @Test
    void verify_failsForAWrongPassword() {
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hash("correctHorse1!", salt);
        assertFalse(PasswordUtil.verify("wrongPassword", salt, hash));
    }

    @Test
    void verify_failsWhenSaltDoesNotMatchTheOneUsedToHash() {
        String salt = PasswordUtil.generateSalt();
        String otherSalt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hash("correctHorse1!", salt);
        assertFalse(PasswordUtil.verify("correctHorse1!", otherSalt, hash));
    }

    @Test
    void verify_isCaseSensitive() {
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hash("CorrectHorse1!", salt);
        assertFalse(PasswordUtil.verify("correcthorse1!", salt, hash));
    }

    // ---------- OTP ----------

    @Test
    void generateOtp_isAlwaysSixDigits() {
        Pattern sixDigits = Pattern.compile("\\d{6}");
        for (int i = 0; i < 100; i++) {
            String otp = PasswordUtil.generateOtp();
            assertTrue(sixDigits.matcher(otp).matches(), "OTP '" + otp + "' should be exactly 6 digits");
        }
    }

    @Test
    void generateOtp_variesAcrossCalls() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 50; i++) codes.add(PasswordUtil.generateOtp());
        assertTrue(codes.size() > 1, "50 OTPs should not all collide on the same value");
    }

    // ---------- complexity policy (data-driven) ----------

    @ParameterizedTest
    @ValueSource(strings = {"short1!", "Ab1!", ""})
    void checkComplexity_tooShort_returnsAReason(String password) {
        assertNotNull(PasswordUtil.checkComplexity(password));
    }

    @Test
    void checkComplexity_nullPassword_returnsAReason() {
        assertNotNull(PasswordUtil.checkComplexity(null));
    }

    @ParameterizedTest
    @CsvSource({
            "alllowercase1!, uppercase",
            "ALLUPPERCASE1!, lowercase",
            "NoDigitsHere!!, digit",
            "NoSymbolsHere1, symbol"
    })
    void checkComplexity_missingACharacterClass_returnsAReasonMentioningIt(String password, String expectedMention) {
        String reason = PasswordUtil.checkComplexity(password);
        assertNotNull(reason);
        assertTrue(reason.toLowerCase().contains(expectedMention),
                "Reason '" + reason + "' should mention '" + expectedMention + "'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Admin123!", "Teller#2024", "S3curePass$", "Br@nchTeller9"})
    void checkComplexity_validPasswords_returnNull(String password) {
        assertNull(PasswordUtil.checkComplexity(password));
    }
}
