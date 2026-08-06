package com.branchteller.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Card {
    private int id;
    private int accountId;
    private String accountNumber;
    private String cardNumber;
    private String cardType; // DEBIT, CREDIT
    private String cardholderName;
    private LocalDate expiryDate;
    private BigDecimal creditLimit;
    private BigDecimal dailyLimit;
    private String status; // ACTIVE, BLOCKED, EXPIRED, CANCELLED
    private LocalDate issuedDate;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getAccountId() { return accountId; }
    public void setAccountId(int accountId) { this.accountId = accountId; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getCardNumber() { return cardNumber; }
    public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }
    public String getCardType() { return cardType; }
    public void setCardType(String cardType) { this.cardType = cardType; }
    public String getCardholderName() { return cardholderName; }
    public void setCardholderName(String cardholderName) { this.cardholderName = cardholderName; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public BigDecimal getCreditLimit() { return creditLimit; }
    public void setCreditLimit(BigDecimal creditLimit) { this.creditLimit = creditLimit; }
    public BigDecimal getDailyLimit() { return dailyLimit; }
    public void setDailyLimit(BigDecimal dailyLimit) { this.dailyLimit = dailyLimit; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getIssuedDate() { return issuedDate; }
    public void setIssuedDate(LocalDate issuedDate) { this.issuedDate = issuedDate; }
}
