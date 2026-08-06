package com.branchteller.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class InterestAccrual {
    private int id;
    private int accountId;
    private String period; // "YYYY-MM"
    private BigDecimal rateApplied;
    private BigDecimal amount;
    private LocalDate postedDate;

    // populated by joins
    private String accountNumber;

    public InterestAccrual() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getAccountId() { return accountId; }
    public void setAccountId(int accountId) { this.accountId = accountId; }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    public BigDecimal getRateApplied() { return rateApplied; }
    public void setRateApplied(BigDecimal rateApplied) { this.rateApplied = rateApplied; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public LocalDate getPostedDate() { return postedDate; }
    public void setPostedDate(LocalDate postedDate) { this.postedDate = postedDate; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
}
