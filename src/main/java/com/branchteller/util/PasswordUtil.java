package com.branchteller.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * SHA-256 + per-user salt password hashing.
 * Run directly to generate a salt/hash pair for seeding the users table:
 *   java -cp target/classes com.branchteller.util.PasswordUtil myPassword123
 */
public class PasswordUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generateSalt() {
        byte[] saltBytes = new byte[16];
        RANDOM.nextBytes(saltBytes);
        return Base64.getEncoder().encodeToString(saltBytes);
    }

    public static String hash(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Base64.getDecoder().decode(salt));
            byte[] hashed = digest.digest(password.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException | java.io.UnsupportedEncodingException e) {
            throw new RuntimeException("Unable to hash password", e);
        }
    }

    public static boolean verify(String password, String salt, String expectedHash) {
        String actual = hash(password, salt);
        return MessageDigest.isEqual(actual.getBytes(), expectedHash.getBytes());
    }

    /** Minimum bank-grade password policy: 8+ chars, upper, lower, digit, symbol. Returns null if OK, else a reason. */
    public static String checkComplexity(String password) {
        if (password == null || password.length() < 8) return "Password must be at least 8 characters";
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSymbol = password.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
        if (!hasUpper) return "Password must contain an uppercase letter";
        if (!hasLower) return "Password must contain a lowercase letter";
        if (!hasDigit) return "Password must contain a digit";
        if (!hasSymbol) return "Password must contain a symbol";
        return null;
    }

    public static String generateOtp() {
        int code = 100000 + RANDOM.nextInt(900000);
        return String.valueOf(code);
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java com.branchteller.util.PasswordUtil <password>");
            return;
        }
        String salt = generateSalt();
        String hash = hash(args[0], salt);
        System.out.println("Salt: " + salt);
        System.out.println("Hash: " + hash);
    }
}
