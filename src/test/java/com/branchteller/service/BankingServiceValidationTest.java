package com.branchteller.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Negative/input-validation tests for BankingService. deposit/withdraw/transfer all
 * validate their amount argument (and transfer additionally checks fromId != toId)
 * *before* ever opening a database connection, so these run with no database at all --
 * a real regression net for the "reject bad input before touching anything" contract.
 */
class BankingServiceValidationTest {

    private final BankingService bankingService = new BankingService();

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.01", "-100.50", "-1"})
    void deposit_nonPositiveAmount_isRejected(String amount) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> bankingService.deposit(1, new BigDecimal(amount), 1, "test"));
        assertEquals("Deposit amount must be positive", ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.01", "-100.50", "-1"})
    void withdraw_nonPositiveAmount_isRejected(String amount) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> bankingService.withdraw(1, new BigDecimal(amount), 1, "test"));
        assertEquals("Withdrawal amount must be positive", ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.01", "-100.50", "-1"})
    void transfer_nonPositiveAmount_isRejected(String amount) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> bankingService.transfer(1, 2, new BigDecimal(amount), 1, "test"));
        assertEquals("Transfer amount must be positive", ex.getMessage());
    }

    @Test
    void transfer_toSameAccount_isRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> bankingService.transfer(7, 7, new BigDecimal("50.00"), 1, "test"));
        assertEquals("Cannot transfer to the same account", ex.getMessage());
    }

    @Test
    void transfer_toSameAccount_isRejectedEvenWithAPositiveAmount() {
        // Same-account guard must fire regardless of amount -- this documents that the
        // check isn't accidentally gated behind some other validation short-circuiting first.
        assertThrows(IllegalArgumentException.class,
                () -> bankingService.transfer(3, 3, new BigDecimal("1000000.00"), 2, ""));
    }
}
