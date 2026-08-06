package com.branchteller.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the pure monthly-interest calculation: balance * annualRate% / 12,
 * rounded HALF_UP to 2 decimals. No database involved -- calculateMonthlyInterest is a
 * pure function, package-private on purpose so this test can call it directly.
 */
class InterestServiceTest {

    @Test
    void normalBalanceAndRate_computesSimpleMonthlyInterest() {
        // $10,000 at 6% annual => 10000 * 0.06 / 12 = $50.00
        BigDecimal result = InterestService.calculateMonthlyInterest(
                new BigDecimal("10000.00"), new BigDecimal("6.00"));
        assertEquals(new BigDecimal("50.00"), result);
    }

    @Test
    void zeroBalance_producesZeroInterest() {
        BigDecimal result = InterestService.calculateMonthlyInterest(
                BigDecimal.ZERO, new BigDecimal("3.50"));
        assertEquals(new BigDecimal("0.00"), result);
    }

    @Test
    void zeroRate_producesZeroInterest() {
        BigDecimal result = InterestService.calculateMonthlyInterest(
                new BigDecimal("5000.00"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("0.00"), result);
    }

    @Test
    void result_isRoundedHalfUpToTwoDecimals() {
        // 385.00 * 1.77 / 1200 = 0.567875 -> rounds to 0.57
        BigDecimal result = InterestService.calculateMonthlyInterest(
                new BigDecimal("385.00"), new BigDecimal("1.77"));
        assertEquals(new BigDecimal("0.57"), result);
    }

    @Test
    void halfwayValue_roundsUpNotToEven() {
        // 600.00 * 0.01 / 1200 = 0.005 exactly -- a genuine half-cent boundary.
        // HALF_UP rounds this to 0.01; HALF_EVEN (Java's "banker's rounding") would instead
        // round it down to 0.00 since 0 is even. This pins down which policy is actually wired up.
        BigDecimal result = InterestService.calculateMonthlyInterest(
                new BigDecimal("600.00"), new BigDecimal("0.01"));
        assertEquals(new BigDecimal("0.01"), result);
    }

    @Test
    void largeBalance_doesNotOverflowOrLosePrecision() {
        // A branch-wide sweep can have large balances; confirm no precision loss.
        BigDecimal result = InterestService.calculateMonthlyInterest(
                new BigDecimal("1234567.89"), new BigDecimal("4.25"));
        // 1234567.89 * 4.25 / 1200 = 4372.42794375... -> 4372.43
        assertEquals(new BigDecimal("4372.43"), result);
    }

    // ---------- data-driven boundary sweep across realistic balance/rate combinations ----------

    @ParameterizedTest
    @CsvSource({
            "2000.00,   12.00,   20.00",
            "1500.00,   5.50,    6.88",
            "999.99,    3.33,    2.77",
            "0.01,      100.00,  0.00",
            "50000.00,  0.25,    10.42",
            "333.33,    9.99,    2.77",
            "7.00,      6.00,    0.04",
            "100000.00, 15.00,   1250.00"
    })
    void calculateMonthlyInterest_matchesExpectedValueAcrossABoundarySweep(
            String balance, String rate, String expected) {
        BigDecimal result = InterestService.calculateMonthlyInterest(new BigDecimal(balance), new BigDecimal(rate));
        assertEquals(new BigDecimal(expected), result);
    }
}
