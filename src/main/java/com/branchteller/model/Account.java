package com.branchteller.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Account {
    private int id;
    private String accountNumber;
    private int customerId;
    private int branchId;
    private String accountType; // SAVINGS, CURRENT, FD, RD
    private BigDecimal balance;
    private BigDecimal interestRate;
    private String status; // ACTIVE, DORMANT, CLOSED
    private LocalDate openedDate;

    // populated by joins, not persisted directly on this table
    private String customerName;

    public Account() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }

    public int getBranchId() { return branchId; }
    public void setBranchId(int branchId) { this.branchId = branchId; }

    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public BigDecimal getInterestRate() { return interestRate; }
    public void setInterestRate(BigDecimal interestRate) { this.interestRate = interestRate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDate getOpenedDate() { return openedDate; }
    public void setOpenedDate(LocalDate openedDate) { this.openedDate = openedDate; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
}
