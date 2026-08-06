package com.branchteller.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Loan {
    private int id;
    private int customerId;
    private int accountId;
    private String loanType;
    private BigDecimal principal;
    private BigDecimal interestRate;
    private int tenureMonths;
    private String status; // APPLIED, APPROVED, REJECTED, DISBURSED, CLOSED
    private LocalDate appliedDate;
    private Integer approvedBy;
    private LocalDate disbursedDate;

    // populated by joins
    private String customerName;
    private String accountNumber;

    public Loan() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }

    public int getAccountId() { return accountId; }
    public void setAccountId(int accountId) { this.accountId = accountId; }

    public String getLoanType() { return loanType; }
    public void setLoanType(String loanType) { this.loanType = loanType; }

    public BigDecimal getPrincipal() { return principal; }
    public void setPrincipal(BigDecimal principal) { this.principal = principal; }

    public BigDecimal getInterestRate() { return interestRate; }
    public void setInterestRate(BigDecimal interestRate) { this.interestRate = interestRate; }

    public int getTenureMonths() { return tenureMonths; }
    public void setTenureMonths(int tenureMonths) { this.tenureMonths = tenureMonths; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDate getAppliedDate() { return appliedDate; }
    public void setAppliedDate(LocalDate appliedDate) { this.appliedDate = appliedDate; }

    public Integer getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Integer approvedBy) { this.approvedBy = approvedBy; }

    public LocalDate getDisbursedDate() { return disbursedDate; }
    public void setDisbursedDate(LocalDate disbursedDate) { this.disbursedDate = disbursedDate; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
}
